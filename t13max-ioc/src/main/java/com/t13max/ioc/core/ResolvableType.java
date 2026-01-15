package com.t13max.ioc.core;

import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ObjectUtils;
import com.t13max.ioc.utils.StringUtils;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;

/**
 * @Author: t13max
 * @Since: 21:32 2026/1/15
 */
public class ResolvableType implements Serializable {
    
    public static final ResolvableType NONE = new ResolvableType(EmptyType.INSTANCE, null, null, 0);

    private static final ResolvableType[] EMPTY_TYPES_ARRAY = new ResolvableType[0];

    private static final ConcurrentReferenceHashMap<ResolvableType, ResolvableType> cache =
            new ConcurrentReferenceHashMap<>(256);

    
    private final Type type;
    
    private final ResolvableType componentType;
    
    private final TypeProvider typeProvider;
    
    private final VariableResolver variableResolver;

    private final Integer hash;

    private Class<?> resolved;

    private volatile ResolvableType superType;

    private volatile ResolvableType [] interfaces;

    private volatile ResolvableType [] generics;

    private volatile Boolean unresolvableGenerics;

    
    private ResolvableType(
            Type type, TypeProvider typeProvider, VariableResolver variableResolver) {

        this.type = type;
        this.componentType = null;
        this.typeProvider = typeProvider;
        this.variableResolver = variableResolver;
        this.hash = calculateHashCode();
        this.resolved = null;
    }
    
    private ResolvableType(Type type, TypeProvider typeProvider,
                           VariableResolver variableResolver, Integer hash) {

        this.type = type;
        this.componentType = null;
        this.typeProvider = typeProvider;
        this.variableResolver = variableResolver;
        this.hash = hash;
        this.resolved = resolveClass();
    }
    
    private ResolvableType(Type type, ResolvableType componentType,
                           TypeProvider typeProvider, VariableResolver variableResolver) {

        this.type = type;
        this.componentType = componentType;
        this.typeProvider = typeProvider;
        this.variableResolver = variableResolver;
        this.hash = null;
        this.resolved = resolveClass();
    }
    
    private ResolvableType(Class<?> clazz) {
        this.resolved = (clazz != null ? clazz : Object.class);
        this.type = this.resolved;
        this.componentType = null;
        this.typeProvider = null;
        this.variableResolver = null;
        this.hash = null;
    }

    
    public Type getType() {
        return SerializableTypeWrapper.unwrap(this.type);
    }
    
    public Class<?> getRawClass() {
        if (this.type == this.resolved) {
            return this.resolved;
        }
        Type rawType = this.type;
        if (rawType instanceof ParameterizedType parameterizedType) {
            rawType = parameterizedType.getRawType();
        }
        return (rawType instanceof Class<?> rawClass ? rawClass : null);
    }
    
    public Object getSource() {
        Object source = (this.typeProvider != null ? this.typeProvider.getSource() : null);
        return (source != null ? source : this.type);
    }
    
    public Class<?> toClass() {
        return resolve(Object.class);
    }
    
    public boolean isInstance(Object obj) {
        return (obj != null && isAssignableFrom(obj.getClass()));
    }
    
    public boolean isAssignableFrom(Class<?> other) {
        // As of 6.1: shortcut assignability check for top-level Class references
        return (this.type instanceof Class<?> clazz ? ClassUtils.isAssignable(clazz, other) :
                isAssignableFrom(forClass(other), false, null, false));
    }
    
    public boolean isAssignableFrom(ResolvableType other) {
        return isAssignableFrom(other, false, null, false);
    }
    
    public boolean isAssignableFromResolvedPart(ResolvableType other) {
        return isAssignableFrom(other, false, null, true);
    }

    private boolean isAssignableFrom(ResolvableType other, boolean strict,
                                     Map<Type, Type> matchedBefore, boolean upUntilUnresolvable) {

        Assert.notNull(other, "ResolvableType must not be null");

        // If we cannot resolve types, we are not assignable
        if (this == NONE || other == NONE) {
            return false;
        }

        if (matchedBefore != null) {
            if (matchedBefore.get(this.type) == other.type) {
                return true;
            }
        } else {
            // As of 6.1: shortcut assignability check for top-level Class references
            if (this.type instanceof Class<?> clazz && other.type instanceof Class<?> otherClazz) {
                return (strict ? clazz.isAssignableFrom(otherClazz) : ClassUtils.isAssignable(clazz, otherClazz));
            }
        }

        if (upUntilUnresolvable && (other.isUnresolvableTypeVariable() || other.isWildcardWithoutBounds())) {
            return true;
        }

        // Deal with array by delegating to the component type
        if (isArray()) {
            return (other.isArray() && getComponentType().isAssignableFrom(
                    other.getComponentType(), true, matchedBefore, upUntilUnresolvable));
        }

        // Deal with wildcard bounds
        WildcardBounds ourBounds = WildcardBounds.get(this);
        WildcardBounds otherBounds = WildcardBounds.get(other);

        // In the form X is assignable to <? extends Number>
        if (otherBounds != null) {
            if (ourBounds != null) {
                return (ourBounds.isSameKind(otherBounds) &&
                        ourBounds.isAssignableFrom(otherBounds.getBounds(), matchedBefore));
            } else if (upUntilUnresolvable) {
                return otherBounds.isAssignableFrom(this, matchedBefore);
            } else if (!strict) {
                return (matchedBefore != null ? otherBounds.equalsType(this, matchedBefore) :
                        otherBounds.isAssignableTo(this, matchedBefore));
            } else {
                return false;
            }
        }

        // In the form <? extends Number> is assignable to X...
        if (ourBounds != null) {
            return ourBounds.isAssignableFrom(other, matchedBefore);
        }

        // Main assignability check about to follow
        boolean exactMatch = (strict && matchedBefore != null);
        boolean checkGenerics = true;
        Class<?> ourResolved = null;
        if (this.type instanceof TypeVariable<?> variable) {
            // Try default variable resolution
            if (this.variableResolver != null) {
                ResolvableType resolved = this.variableResolver.resolveVariable(variable);
                if (resolved != null) {
                    ourResolved = resolved.resolve();
                }
            }
            if (ourResolved == null) {
                // Try variable resolution against target type
                if (other.variableResolver != null) {
                    ResolvableType resolved = other.variableResolver.resolveVariable(variable);
                    if (resolved != null) {
                        ourResolved = resolved.resolve();
                        checkGenerics = false;
                    }
                }
            }
            if (ourResolved == null) {
                // Unresolved type variable, potentially nested -> never insist on exact match
                exactMatch = false;
            }
        }
        if (ourResolved == null) {
            ourResolved = toClass();
        }
        Class<?> otherResolved = other.toClass();

        // We need an exact type match for generics
        // List<CharSequence> is not assignable from List<String>
        if (exactMatch ? !ourResolved.equals(otherResolved) :
                (strict ? !ourResolved.isAssignableFrom(otherResolved) :
                        !ClassUtils.isAssignable(ourResolved, otherResolved))) {
            return false;
        }

        if (checkGenerics) {
            // Recursively check each generic
            ResolvableType[] ourGenerics = getGenerics();
            ResolvableType[] otherGenerics = other.as(ourResolved).getGenerics();
            if (ourGenerics.length != otherGenerics.length) {
                return false;
            }
            if (ourGenerics.length > 0) {
                if (matchedBefore == null) {
                    matchedBefore = new IdentityHashMap<>(1);
                }
                matchedBefore.put(this.type, other.type);
                for (int i = 0; i < ourGenerics.length; i++) {
                    ResolvableType otherGeneric = otherGenerics[i];
                    if (!ourGenerics[i].isAssignableFrom(otherGeneric,
                            !otherGeneric.isUnresolvableTypeVariable(), matchedBefore, upUntilUnresolvable)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
    
    public boolean isArray() {
        if (this == NONE) {
            return false;
        }
        return ((this.type instanceof Class<?> clazz && clazz.isArray()) ||
                this.type instanceof GenericArrayType || resolveType().isArray());
    }
    
    public ResolvableType getComponentType() {
        if (this == NONE) {
            return NONE;
        }
        if (this.componentType != null) {
            return this.componentType;
        }
        if (this.type instanceof Class<?> clazz) {
            Class<?> componentType = clazz.componentType();
            return forType(componentType, this.variableResolver);
        }
        if (this.type instanceof GenericArrayType genericArrayType) {
            return forType(genericArrayType.getGenericComponentType(), this.variableResolver);
        }
        return resolveType().getComponentType();
    }
    
    public ResolvableType asCollection() {
        return as(Collection.class);
    }
    
    public ResolvableType asMap() {
        return as(Map.class);
    }
    
    public ResolvableType as(Class<?> type) {
        if (this == NONE) {
            return NONE;
        }
        Class<?> resolved = resolve();
        if (resolved == null || resolved == type) {
            return this;
        }
        for (ResolvableType interfaceType : getInterfaces()) {
            ResolvableType interfaceAsType = interfaceType.as(type);
            if (interfaceAsType != NONE) {
                return interfaceAsType;
            }
        }
        return getSuperType().as(type);
    }
    
    public ResolvableType getSuperType() {
        Class<?> resolved = resolve();
        if (resolved == null) {
            return NONE;
        }
        try {
            Type superclass = resolved.getGenericSuperclass();
            if (superclass == null) {
                return NONE;
            }
            ResolvableType superType = this.superType;
            if (superType == null) {
                superType = forType(superclass, this);
                this.superType = superType;
            }
            return superType;
        } catch (TypeNotPresentException ex) {
            // Ignore non-present types in generic signature
            return NONE;
        }
    }
    
    public ResolvableType[] getInterfaces() {
        Class<?> resolved = resolve();
        if (resolved == null) {
            return EMPTY_TYPES_ARRAY;
        }
        ResolvableType[] interfaces = this.interfaces;
        if (interfaces == null) {
            Type[] genericIfcs = resolved.getGenericInterfaces();
            if (genericIfcs.length > 0) {
                interfaces = new ResolvableType[genericIfcs.length];
                for (int i = 0; i < genericIfcs.length; i++) {
                    interfaces[i] = forType(genericIfcs[i], this);
                }
            } else {
                interfaces = EMPTY_TYPES_ARRAY;
            }
            this.interfaces = interfaces;
        }
        return interfaces;
    }
    
    public boolean hasGenerics() {
        return (getGenerics().length > 0);
    }
    
    public boolean hasResolvableGenerics() {
        if (this == NONE) {
            return false;
        }
        ResolvableType[] generics = getGenerics();
        for (ResolvableType generic : generics) {
            if (!generic.isUnresolvableTypeVariable() && !generic.isWildcardWithoutBounds()) {
                return true;
            }
        }
        return false;
    }
    
    public boolean hasUnresolvableGenerics() {
        if (this == NONE) {
            return false;
        }
        return hasUnresolvableGenerics(null);
    }

    private boolean hasUnresolvableGenerics(Set<Type> alreadySeen) {
        Boolean unresolvableGenerics = this.unresolvableGenerics;
        if (unresolvableGenerics == null) {
            unresolvableGenerics = determineUnresolvableGenerics(alreadySeen);
            this.unresolvableGenerics = unresolvableGenerics;
        }
        return unresolvableGenerics;
    }

    private boolean determineUnresolvableGenerics(Set<Type> alreadySeen) {
        if (alreadySeen != null && alreadySeen.contains(this.type)) {
            // Self-referencing generic -> not unresolvable
            return false;
        }

        ResolvableType[] generics = getGenerics();
        for (ResolvableType generic : generics) {
            if (generic.isUnresolvableTypeVariable() || generic.isWildcardWithoutBounds() ||
                    generic.hasUnresolvableGenerics(currentTypeSeen(alreadySeen))) {
                return true;
            }
        }
        Class<?> resolved = resolve();
        if (resolved != null) {
            try {
                for (Type genericInterface : resolved.getGenericInterfaces()) {
                    if (genericInterface instanceof Class<?> clazz) {
                        if (clazz.getTypeParameters().length > 0) {
                            return true;
                        }
                    }
                }
            } catch (TypeNotPresentException ex) {
                // Ignore non-present types in generic signature
            }
            Class<?> superclass = resolved.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return getSuperType().hasUnresolvableGenerics(currentTypeSeen(alreadySeen));
            }
        }
        return false;
    }

    private Set<Type> currentTypeSeen(Set<Type> alreadySeen) {
        if (alreadySeen == null) {
            alreadySeen = new HashSet<>(4);
        }
        alreadySeen.add(this.type);
        return alreadySeen;
    }
    
    private boolean isUnresolvableTypeVariable() {
        if (this.type instanceof TypeVariable<?> variable) {
            if (this.variableResolver == null) {
                return true;
            }
            ResolvableType resolved = this.variableResolver.resolveVariable(variable);
            if (resolved == null || resolved.isUnresolvableTypeVariable() || resolved.isWildcardWithoutBounds()) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isWildcardWithoutBounds() {
        if (this.type instanceof WildcardType wildcardType) {
            if (wildcardType.getLowerBounds().length == 0) {
                Type[] upperBounds = wildcardType.getUpperBounds();
                if (upperBounds.length == 0 || (upperBounds.length == 1 && Object.class == upperBounds[0])) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public ResolvableType getNested(int nestingLevel) {
        return getNested(nestingLevel, null);
    }
    
    public ResolvableType getNested(int nestingLevel, Map<Integer, Integer> typeIndexesPerLevel) {
        ResolvableType result = this;
        for (int i = 2; i <= nestingLevel; i++) {
            if (result.isArray()) {
                result = result.getComponentType();
            } else {
                // Handle derived types
                while (result != ResolvableType.NONE && !result.hasGenerics()) {
                    result = result.getSuperType();
                }
                Integer index = (typeIndexesPerLevel != null ? typeIndexesPerLevel.get(i) : null);
                index = (index == null ? result.getGenerics().length - 1 : index);
                result = result.getGeneric(index);
            }
        }
        return result;
    }
    
    public ResolvableType getGeneric(int ... indexes) {
        ResolvableType[] generics = getGenerics();
        if (indexes == null || indexes.length == 0) {
            return (generics.length == 0 ? NONE : generics[0]);
        }
        ResolvableType generic = this;
        for (int index : indexes) {
            generics = generic.getGenerics();
            if (index < 0 || index >= generics.length) {
                return NONE;
            }
            generic = generics[index];
        }
        return generic;
    }
    
    public ResolvableType[] getGenerics() {
        if (this == NONE) {
            return EMPTY_TYPES_ARRAY;
        }
        ResolvableType[] generics = this.generics;
        if (generics == null) {
            if (this.type instanceof Class<?> clazz) {
                Type[] typeParams = clazz.getTypeParameters();
                if (typeParams.length > 0) {
                    generics = new ResolvableType[typeParams.length];
                    for (int i = 0; i < generics.length; i++) {
                        generics[i] = ResolvableType.forType(typeParams[i], this);
                    }
                } else {
                    generics = EMPTY_TYPES_ARRAY;
                }
            } else if (this.type instanceof ParameterizedType parameterizedType) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0) {
                    generics = new ResolvableType[actualTypeArguments.length];
                    for (int i = 0; i < actualTypeArguments.length; i++) {
                        generics[i] = forType(actualTypeArguments[i], this.variableResolver);
                    }
                } else {
                    generics = EMPTY_TYPES_ARRAY;
                }
            } else {
                generics = resolveType().getGenerics();
            }
            this.generics = generics;
        }
        return generics;
    }
    
    public Class<?>[] resolveGenerics() {
        ResolvableType[] generics = getGenerics();
        Class<?>[] resolvedGenerics = new Class<?>[generics.length];
        for (int i = 0; i < generics.length; i++) {
            resolvedGenerics[i] = generics[i].resolve();
        }
        return resolvedGenerics;
    }
    
    public Class<?>[] resolveGenerics(Class<?> fallback) {
        ResolvableType[] generics = getGenerics();
        Class<?>[] resolvedGenerics = new Class<?>[generics.length];
        for (int i = 0; i < generics.length; i++) {
            resolvedGenerics[i] = generics[i].resolve(fallback);
        }
        return resolvedGenerics;
    }
    
    public Class<?> resolveGeneric(int... indexes) {
        return getGeneric(indexes).resolve();
    }
    
    public Class<?> resolve() {
        return this.resolved;
    }
    
    public Class<?> resolve(Class<?> fallback) {
        return (this.resolved != null ? this.resolved : fallback);
    }

    private Class<?> resolveClass() {
        if (this.type == EmptyType.INSTANCE) {
            return null;
        }
        if (this.type instanceof Class<?> clazz) {
            return clazz;
        }
        if (this.type instanceof GenericArrayType) {
            Class<?> resolvedComponent = getComponentType().resolve();
            return (resolvedComponent != null ? Array.newInstance(resolvedComponent, 0).getClass() : null);
        }
        return resolveType().resolve();
    }
    
    ResolvableType resolveType() {
        if (this.type instanceof ParameterizedType parameterizedType) {
            return forType(parameterizedType.getRawType(), this.variableResolver);
        }
        if (this.type instanceof WildcardType wildcardType) {
            Type resolved = resolveBounds(wildcardType.getUpperBounds());
            if (resolved == null) {
                resolved = resolveBounds(wildcardType.getLowerBounds());
            }
            return forType(resolved, this.variableResolver);
        }
        if (this.type instanceof TypeVariable<?> variable) {
            // Try default variable resolution
            if (this.variableResolver != null) {
                ResolvableType resolved = this.variableResolver.resolveVariable(variable);
                if (resolved != null) {
                    return resolved;
                }
            }
            // Fallback to bounds
            return forType(resolveBounds(variable.getBounds()), this.variableResolver);
        }
        return NONE;
    }

    private ResolvableType resolveVariable(TypeVariable<?> variable) {
        if (this.type instanceof TypeVariable) {
            return resolveType().resolveVariable(variable);
        }
        if (this.type instanceof ParameterizedType parameterizedType) {
            Class<?> resolved = resolve();
            if (resolved == null) {
                return null;
            }
            TypeVariable<?>[] variables = resolved.getTypeParameters();
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            for (int i = 0; i < variables.length; i++) {
                if (ObjectUtils.nullSafeEquals(variables[i].getName(), variable.getName())) {
                    return forType(typeArguments[i], this.variableResolver);
                }
            }
            Type ownerType = parameterizedType.getOwnerType();
            if (ownerType != null) {
                return forType(ownerType, this.variableResolver).resolveVariable(variable);
            }
        }
        if (this.type instanceof WildcardType) {
            ResolvableType resolved = resolveType().resolveVariable(variable);
            if (resolved != null) {
                return resolved;
            }
        }
        if (this.variableResolver != null) {
            return this.variableResolver.resolveVariable(variable);
        }
        return null;
    }

    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || other.getClass() != getClass()) {
            return false;
        }
        ResolvableType otherType = (ResolvableType) other;

        if (!equalsType(otherType)) {
            return false;
        }
        if (this.typeProvider != otherType.typeProvider &&
                (this.typeProvider == null || otherType.typeProvider == null ||
                        !ObjectUtils.nullSafeEquals(this.typeProvider.getType(), otherType.typeProvider.getType()))) {
            return false;
        }
        if (this.variableResolver != otherType.variableResolver &&
                (this.variableResolver == null || otherType.variableResolver == null ||
                        !ObjectUtils.nullSafeEquals(this.variableResolver.getSource(), otherType.variableResolver.getSource()))) {
            return false;
        }
        return true;
    }
    
    public boolean equalsType(ResolvableType otherType) {
        return (ObjectUtils.nullSafeEquals(this.type, otherType.type) &&
                ObjectUtils.nullSafeEquals(this.componentType, otherType.componentType));
    }

    @Override
    public int hashCode() {
        return (this.hash != null ? this.hash : calculateHashCode());
    }

    private int calculateHashCode() {
        int hashCode = ObjectUtils.nullSafeHashCode(this.type);
        if (this.componentType != null) {
            hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.componentType);
        }
        if (this.typeProvider != null) {
            hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.typeProvider.getType());
        }
        if (this.variableResolver != null) {
            hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.variableResolver.getSource());
        }
        return hashCode;
    }
    
    
    VariableResolver asVariableResolver() {
        if (this == NONE) {
            return null;
        }
        return new DefaultVariableResolver(this);
    }
    
    private Object readResolve() {
        return (this.type == EmptyType.INSTANCE ? NONE : this);
    }
    
    @Override
    public String toString() {
        if (isArray()) {
            return getComponentType() + "[]";
        }
        if (this.resolved == null) {
            return "?";
        }
        if (this.type instanceof TypeVariable<?> variable) {
            if (this.variableResolver == null || this.variableResolver.resolveVariable(variable) == null) {
                // Don't bother with variable boundaries for toString()...
                // Can cause infinite recursions in case of self-references
                return "?";
            }
        }
        if (hasGenerics()) {
            return this.resolved.getName() + '<' + StringUtils.arrayToDelimitedString(getGenerics(), ", ") + '>';
        }
        return this.resolved.getName();
    }


    // Factory methods
    
    public static ResolvableType forClass(Class<?> clazz) {
        return new ResolvableType(clazz);
    }
    
    public static ResolvableType forRawClass(Class<?> clazz) {
        return new ResolvableType(clazz) {
            @Override
            public ResolvableType[] getGenerics() {
                return EMPTY_TYPES_ARRAY;
            }

            @Override
            public boolean isAssignableFrom(Class<?> other) {
                return (clazz == null || ClassUtils.isAssignable(clazz, other));
            }

            @Override
            public boolean isAssignableFrom(ResolvableType other) {
                Class<?> otherClass = other.resolve();
                return (otherClass != null && (clazz == null || ClassUtils.isAssignable(clazz, otherClass)));
            }
        };
    }
    
    public static ResolvableType forClass(Class<?> baseType, Class<?> implementationClass) {
        Assert.notNull(baseType, "Base type must not be null");
        ResolvableType asType = forType(implementationClass).as(baseType);
        return (asType == NONE ? forType(baseType) : asType);
    }
    
    public static ResolvableType forClassWithGenerics(Class<?> clazz, Class<?>... generics) {
        Assert.notNull(clazz, "Class must not be null");
        Assert.notNull(generics, "Generics array must not be null");
        ResolvableType[] resolvableGenerics = new ResolvableType[generics.length];
        for (int i = 0; i < generics.length; i++) {
            resolvableGenerics[i] = forClass(generics[i]);
        }
        return forClassWithGenerics(clazz, resolvableGenerics);
    }
    
    public static ResolvableType forClassWithGenerics(Class<?> clazz, ResolvableType ... generics) {
        Assert.notNull(clazz, "Class must not be null");
        TypeVariable<?>[] variables = clazz.getTypeParameters();
        if (generics != null) {
            Assert.isTrue(variables.length == generics.length,
                    () -> "Mismatched number of generics specified for " + clazz.toGenericString());
        }
        Type[] arguments = new Type[variables.length];
        for (int i = 0; i < variables.length; i++) {
            ResolvableType generic = (generics != null ? generics[i] : null);
            Type argument = (generic != null ? generic.getType() : null);
            arguments[i] = (argument != null && !(argument instanceof TypeVariable) ? argument : variables[i]);
        }
        return forType(new SyntheticParameterizedType(clazz, arguments),
                (generics != null ? new TypeVariablesVariableResolver(variables, generics) : null));
    }
    
    public static ResolvableType forInstance(Object instance) {
        if (instance instanceof ResolvableTypeProvider resolvableTypeProvider) {
            ResolvableType type = resolvableTypeProvider.getResolvableType();
            if (type != null) {
                return type;
            }
        }
        return (instance != null ? forClass(instance.getClass()) : NONE);
    }
    
    public static ResolvableType forField(Field field) {
        Assert.notNull(field, "Field must not be null");
        return forType(null, new FieldTypeProvider(field), null);
    }
    
    public static ResolvableType forField(Field field, Class<?> implementationClass) {
        Assert.notNull(field, "Field must not be null");
        ResolvableType owner = forType(implementationClass).as(field.getDeclaringClass());
        return forType(null, new FieldTypeProvider(field), owner.asVariableResolver());
    }
    
    public static ResolvableType forField(Field field, ResolvableType implementationType) {
        Assert.notNull(field, "Field must not be null");
        ResolvableType owner = (implementationType != null ? implementationType : NONE);
        owner = owner.as(field.getDeclaringClass());
        return forType(null, new FieldTypeProvider(field), owner.asVariableResolver());
    }
    
    public static ResolvableType forField(Field field, int nestingLevel) {
        Assert.notNull(field, "Field must not be null");
        return forType(null, new FieldTypeProvider(field), null).getNested(nestingLevel);
    }
    
    public static ResolvableType forField(Field field, int nestingLevel, Class<?> implementationClass) {
        Assert.notNull(field, "Field must not be null");
        ResolvableType owner = forType(implementationClass).as(field.getDeclaringClass());
        return forType(null, new FieldTypeProvider(field), owner.asVariableResolver()).getNested(nestingLevel);
    }
    
    public static ResolvableType forConstructorParameter(Constructor<?> constructor, int parameterIndex) {
        Assert.notNull(constructor, "Constructor must not be null");
        return forMethodParameter(new MethodParameter(constructor, parameterIndex));
    }
    
    public static ResolvableType forConstructorParameter(Constructor<?> constructor, int parameterIndex,
                                                         Class<?> implementationClass) {

        Assert.notNull(constructor, "Constructor must not be null");
        MethodParameter methodParameter = new MethodParameter(constructor, parameterIndex, implementationClass);
        return forMethodParameter(methodParameter);
    }
    
    public static ResolvableType forMethodReturnType(Method method) {
        Assert.notNull(method, "Method must not be null");
        return forMethodParameter(new MethodParameter(method, -1));
    }
    
    public static ResolvableType forMethodReturnType(Method method, Class<?> implementationClass) {
        Assert.notNull(method, "Method must not be null");
        MethodParameter methodParameter = new MethodParameter(method, -1, implementationClass);
        return forMethodParameter(methodParameter);
    }
    
    public static ResolvableType forMethodParameter(Method method, int parameterIndex) {
        Assert.notNull(method, "Method must not be null");
        return forMethodParameter(new MethodParameter(method, parameterIndex));
    }
    
    public static ResolvableType forMethodParameter(Method method, int parameterIndex, Class<?> implementationClass) {
        Assert.notNull(method, "Method must not be null");
        MethodParameter methodParameter = new MethodParameter(method, parameterIndex, implementationClass);
        return forMethodParameter(methodParameter);
    }
    
    public static ResolvableType forMethodParameter(MethodParameter methodParameter) {
        return forMethodParameter(methodParameter, (Type) null);
    }
    
    public static ResolvableType forMethodParameter(MethodParameter methodParameter,
                                                    ResolvableType implementationType) {

        Assert.notNull(methodParameter, "MethodParameter must not be null");
        implementationType = (implementationType != null ? implementationType :
                forType(methodParameter.getContainingClass()));
        ResolvableType owner = implementationType.as(methodParameter.getDeclaringClass());
        return forType(null, new MethodParameterTypeProvider(methodParameter), owner.asVariableResolver()).
                getNested(methodParameter.getNestingLevel(), methodParameter.typeIndexesPerLevel);
    }
    
    public static ResolvableType forMethodParameter(MethodParameter methodParameter, Type targetType) {
        Assert.notNull(methodParameter, "MethodParameter must not be null");
        return forMethodParameter(methodParameter, targetType, methodParameter.getNestingLevel());
    }
    
    static ResolvableType forMethodParameter(
            MethodParameter methodParameter, Type targetType, int nestingLevel) {

        ResolvableType owner = forType(methodParameter.getContainingClass()).as(methodParameter.getDeclaringClass());
        return forType(targetType, new MethodParameterTypeProvider(methodParameter), owner.asVariableResolver()).
                getNested(nestingLevel, methodParameter.typeIndexesPerLevel);
    }
    
    public static ResolvableType forArrayComponent(ResolvableType componentType) {
        Assert.notNull(componentType, "Component type must not be null");
        Class<?> arrayType = componentType.toClass().arrayType();
        return new ResolvableType(arrayType, componentType, null, null);
    }
    
    static ResolvableType forVariableBounds(TypeVariable<?> typeVariable) {
        return forType(resolveBounds(typeVariable.getBounds()));
    }

    private static Type resolveBounds(Type[] bounds) {
        if (bounds.length == 0 || bounds[0] == Object.class) {
            return null;
        }
        return bounds[0];
    }
    
    public static ResolvableType forType(Type type) {
        return forType(type, null, null);
    }
    
    public static ResolvableType forType(Type type, ResolvableType owner) {
        VariableResolver variableResolver = null;
        if (owner != null) {
            variableResolver = owner.asVariableResolver();
        }
        return forType(type, variableResolver);
    }
    
    public static ResolvableType forType(ParameterizedTypeReference<?> typeReference) {
        return forType(typeReference.getType(), null, null);
    }
    
    static ResolvableType forType(Type type, VariableResolver variableResolver) {
        return forType(type, null, variableResolver);
    }
    
    static ResolvableType forType(
            Type type, TypeProvider typeProvider, VariableResolver variableResolver) {

        if (type == null && typeProvider != null) {
            type = SerializableTypeWrapper.forTypeProvider(typeProvider);
        }
        if (type == null) {
            return NONE;
        }

        // For simple Class references, build the wrapper right away -
        // no expensive resolution necessary, so not worth caching...
        if (type instanceof Class) {
            return new ResolvableType(type, null, typeProvider, variableResolver);
        }

        // Purge empty entries on access since we don't have a clean-up thread or the like.
        cache.purgeUnreferencedEntries();

        // Check the cache - we may have a ResolvableType which has been resolved before...
        ResolvableType resultType = new ResolvableType(type, typeProvider, variableResolver);
        ResolvableType cachedType = cache.get(resultType);
        if (cachedType == null) {
            cachedType = new ResolvableType(type, typeProvider, variableResolver, resultType.hash);
            cache.put(cachedType, cachedType);
        }
        resultType.resolved = cachedType.resolved;
        return resultType;
    }
    
    public static void clearCache() {
        cache.clear();
        SerializableTypeWrapper.cache.clear();
    }

    
    interface VariableResolver extends Serializable {

        
        Object getSource();

        
        
        ResolvableType resolveVariable(TypeVariable<?> variable);
    }


    @SuppressWarnings("serial")
    private static class DefaultVariableResolver implements VariableResolver {

        private final ResolvableType source;

        DefaultVariableResolver(ResolvableType resolvableType) {
            this.source = resolvableType;
        }

        @Override
        public ResolvableType resolveVariable(TypeVariable<?> variable) {
            return this.source.resolveVariable(variable);
        }

        @Override
        public Object getSource() {
            return this.source;
        }
    }


    @SuppressWarnings("serial")
    private static class TypeVariablesVariableResolver implements VariableResolver {

        private final TypeVariable<?>[] variables;

        private final ResolvableType[] generics;

        public TypeVariablesVariableResolver(TypeVariable<?>[] variables, ResolvableType[] generics) {
            this.variables = variables;
            this.generics = generics;
        }

        @Override
        public ResolvableType resolveVariable(TypeVariable<?> variable) {
            TypeVariable<?> variableToCompare = SerializableTypeWrapper.unwrap(variable);
            for (int i = 0; i < this.variables.length; i++) {
                TypeVariable<?> resolvedVariable = SerializableTypeWrapper.unwrap(this.variables[i]);
                if (ObjectUtils.nullSafeEquals(resolvedVariable, variableToCompare)) {
                    return this.generics[i];
                }
            }
            return null;
        }

        @Override
        public Object getSource() {
            return this.generics;
        }
    }


    private static final class SyntheticParameterizedType implements ParameterizedType, Serializable {

        private final Type rawType;

        private final Type[] typeArguments;

        public SyntheticParameterizedType(Type rawType, Type[] typeArguments) {
            this.rawType = rawType;
            this.typeArguments = typeArguments;
        }

        @Override
        public String getTypeName() {
            String typeName = this.rawType.getTypeName();
            if (this.typeArguments.length > 0) {
                StringJoiner stringJoiner = new StringJoiner(", ", "<", ">");
                for (Type argument : this.typeArguments) {
                    stringJoiner.add(argument.getTypeName());
                }
                return typeName + stringJoiner;
            }
            return typeName;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public Type getRawType() {
            return this.rawType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return this.typeArguments;
        }

        @Override
        public boolean equals(Object other) {
            return (this == other || (other instanceof ParameterizedType that &&
                    that.getOwnerType() == null && this.rawType.equals(that.getRawType()) &&
                    Arrays.equals(this.typeArguments, that.getActualTypeArguments())));
        }

        @Override
        public int hashCode() {
            return (this.rawType.hashCode() * 31 + Arrays.hashCode(this.typeArguments));
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    
    private static class WildcardBounds {

        private final Kind kind;

        private final ResolvableType[] bounds;

        
        public WildcardBounds(Kind kind, ResolvableType[] bounds) {
            this.kind = kind;
            this.bounds = bounds;
        }

        
        public boolean isSameKind(WildcardBounds bounds) {
            return this.kind == bounds.kind;
        }

        
        public boolean isAssignableFrom(ResolvableType[] types, Map<Type, Type> matchedBefore) {
            for (ResolvableType bound : this.bounds) {
                boolean matched = false;
                for (ResolvableType type : types) {
                    if (this.kind == Kind.UPPER ? bound.isAssignableFrom(type, false, matchedBefore, false) :
                            type.isAssignableFrom(bound, false, matchedBefore, false)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            return true;
        }

        
        public boolean isAssignableFrom(ResolvableType type, Map<Type, Type> matchedBefore) {
            for (ResolvableType bound : this.bounds) {
                if (this.kind == Kind.UPPER ? !bound.isAssignableFrom(type, false, matchedBefore, false) :
                        !type.isAssignableFrom(bound, false, matchedBefore, false)) {
                    return false;
                }
            }
            return true;
        }

        
        public boolean isAssignableTo(ResolvableType type, Map<Type, Type> matchedBefore) {
            if (this.kind == Kind.UPPER) {
                for (ResolvableType bound : this.bounds) {
                    if (type.isAssignableFrom(bound, false, matchedBefore, false)) {
                        return true;
                    }
                }
                return false;
            } else {
                return (type.resolve() == Object.class);
            }
        }

        
        public boolean equalsType(ResolvableType type, Map<Type, Type> matchedBefore) {
            for (ResolvableType bound : this.bounds) {
                if (this.kind == Kind.UPPER && bound.hasUnresolvableGenerics() ?
                        !type.isAssignableFrom(bound, true, matchedBefore, false) :
                        !type.equalsType(bound)) {
                    return false;
                }
            }
            return true;
        }

        
        public ResolvableType[] getBounds() {
            return this.bounds;
        }

        
        public static WildcardBounds get(ResolvableType type) {
            ResolvableType candidate = type;
            while (!(candidate.getType() instanceof WildcardType || candidate.isUnresolvableTypeVariable())) {
                if (candidate == NONE) {
                    return null;
                }
                candidate = candidate.resolveType();
            }
            Kind boundsType;
            Type[] bounds;
            if (candidate.getType() instanceof WildcardType wildcardType) {
                boundsType = (wildcardType.getLowerBounds().length > 0 ? Kind.LOWER : Kind.UPPER);
                bounds = (boundsType == Kind.UPPER ? wildcardType.getUpperBounds() : wildcardType.getLowerBounds());
            } else {
                boundsType = Kind.UPPER;
                bounds = ((TypeVariable<?>) candidate.getType()).getBounds();
            }
            ResolvableType[] resolvableBounds = new ResolvableType[bounds.length];
            for (int i = 0; i < bounds.length; i++) {
                resolvableBounds[i] = ResolvableType.forType(bounds[i], type.variableResolver);
            }
            return new WildcardBounds(boundsType, resolvableBounds);
        }

        
        enum Kind {UPPER, LOWER}
    }

    
    @SuppressWarnings("serial")
    static class EmptyType implements Type, Serializable {

        static final Type INSTANCE = new EmptyType();

        Object readResolve() {
            return INSTANCE;
        }
    }

}
