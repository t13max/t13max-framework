package com.t13max.ioc.core.convert;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;


import com.t13max.ioc.core.MethodParameter;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.annotation.AnnotatedElementAdapter;
import com.t13max.ioc.core.annotation.AnnotatedElementUtils;
import com.t13max.ioc.lang.Contract;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;

@SuppressWarnings("serial")
public class TypeDescriptor implements Serializable {
    private static final Map<Class<?>, TypeDescriptor> commonTypesCache = new HashMap<>(32);
    private static final Class<?>[] CACHED_COMMON_TYPES = {
            boolean.class, Boolean.class, byte.class, Byte.class, char.class, Character.class,
            double.class, Double.class, float.class, Float.class, int.class, Integer.class,
            long.class, Long.class, short.class, Short.class, String.class, Object.class};

    static {
        for (Class<?> preCachedClass : CACHED_COMMON_TYPES) {
            commonTypesCache.put(preCachedClass, valueOf(preCachedClass));
        }
    }

    private final Class<?> type;
    private final ResolvableType resolvableType;
    private final AnnotatedElementSupplier annotatedElementSupplier;
    private volatile AnnotatedElementAdapter annotatedElement;

    public TypeDescriptor(MethodParameter methodParameter) {
        this.resolvableType = ResolvableType.forMethodParameter(methodParameter);
        this.type = this.resolvableType.resolve(methodParameter.getNestedParameterType());
        this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(methodParameter.getParameterIndex() == -1 ?
                methodParameter.getMethodAnnotations() : methodParameter.getParameterAnnotations());
    }

    public TypeDescriptor(Field field) {
        this.resolvableType = ResolvableType.forField(field);
        this.type = this.resolvableType.resolve(field.getType());
        this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(field.getAnnotations());
    }

    public TypeDescriptor(Property property) {
        Assert.notNull(property, "Property must not be null");
        this.resolvableType = ResolvableType.forMethodParameter(property.getMethodParameter());
        this.type = this.resolvableType.resolve(property.getType());
        this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(property.getAnnotations());
    }

    public TypeDescriptor(ResolvableType resolvableType, Class<?> type, Annotation[] annotations) {
        this.resolvableType = resolvableType;
        this.type = (type != null ? type : resolvableType.toClass());
        this.annotatedElementSupplier = () -> AnnotatedElementAdapter.from(annotations);
    }

    public Class<?> getObjectType() {
        return ClassUtils.resolvePrimitiveIfNecessary(getType());
    }

    public Class<?> getType() {
        return this.type;
    }

    public ResolvableType getResolvableType() {
        return this.resolvableType;
    }

    public Object getSource() {
        return this.resolvableType.getSource();
    }

    public TypeDescriptor nested(int nestingLevel) {
        ResolvableType nested = this.resolvableType;
        for (int i = 0; i < nestingLevel; i++) {
            if (Object.class == nested.getType()) {
                // Could be a collection type but we don't know about its element type,
                // so let's just assume there is an element type of type Object...
            } else {
                nested = nested.getNested(2);
            }
        }
        if (nested == ResolvableType.NONE) {
            return null;
        }
        return getRelatedIfResolvable(nested);
    }

    public TypeDescriptor narrow(Object value) {
        if (value == null) {
            return this;
        }
        ResolvableType narrowed = ResolvableType.forType(value.getClass(), getResolvableType());
        return new TypeDescriptor(narrowed, value.getClass(), getAnnotations());
    }

    public TypeDescriptor upcast(Class<?> superType) {
        if (superType == null) {
            return null;
        }
        Assert.isAssignable(superType, getType());
        return new TypeDescriptor(getResolvableType().as(superType), superType, getAnnotations());
    }

    public String getName() {
        return ClassUtils.getQualifiedName(getType());
    }

    public boolean isPrimitive() {
        return getType().isPrimitive();
    }

    private AnnotatedElementAdapter getAnnotatedElement() {
        AnnotatedElementAdapter annotatedElement = this.annotatedElement;
        if (annotatedElement == null) {
            annotatedElement = this.annotatedElementSupplier.get();
            this.annotatedElement = annotatedElement;
        }
        return annotatedElement;
    }

    public Annotation[] getAnnotations() {
        return getAnnotatedElement().getAnnotations();
    }

    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        AnnotatedElementAdapter annotatedElement = getAnnotatedElement();
        if (annotatedElement.isEmpty()) {
            // Shortcut: AnnotatedElementUtils would have to expect AnnotatedElement.getAnnotations()
            // to return a copy of the array, whereas we can do it more efficiently here.
            return false;
        }
        return AnnotatedElementUtils.isAnnotated(annotatedElement, annotationType);
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        AnnotatedElementAdapter annotatedElement = getAnnotatedElement();
        if (annotatedElement.isEmpty()) {
            // Shortcut: AnnotatedElementUtils would have to expect AnnotatedElement.getAnnotations()
            // to return a copy of the array, whereas we can do it more efficiently here.
            return null;
        }
        return AnnotatedElementUtils.getMergedAnnotation(annotatedElement, annotationType);
    }

    public boolean isAssignableTo(TypeDescriptor typeDescriptor) {
        boolean typesAssignable = typeDescriptor.getObjectType().isAssignableFrom(getObjectType());
        if (!typesAssignable) {
            return false;
        }
        if (isArray() && typeDescriptor.isArray()) {
            return isNestedAssignable(getElementTypeDescriptor(), typeDescriptor.getElementTypeDescriptor());
        } else if (isCollection() && typeDescriptor.isCollection()) {
            return isNestedAssignable(getElementTypeDescriptor(), typeDescriptor.getElementTypeDescriptor());
        } else if (isMap() && typeDescriptor.isMap()) {
            return isNestedAssignable(getMapKeyTypeDescriptor(), typeDescriptor.getMapKeyTypeDescriptor()) &&
                    isNestedAssignable(getMapValueTypeDescriptor(), typeDescriptor.getMapValueTypeDescriptor());
        } else {
            return true;
        }
    }

    private boolean isNestedAssignable(TypeDescriptor nestedTypeDescriptor,
                                       TypeDescriptor otherNestedTypeDescriptor) {
        return (nestedTypeDescriptor == null || otherNestedTypeDescriptor == null ||
                nestedTypeDescriptor.isAssignableTo(otherNestedTypeDescriptor));
    }

    public boolean isCollection() {
        return Collection.class.isAssignableFrom(getType());
    }

    public boolean isArray() {
        return getType().isArray();
    }

    public TypeDescriptor getElementTypeDescriptor() {
        if (getResolvableType().isArray()) {
            return new TypeDescriptor(getResolvableType().getComponentType(), null, getAnnotations());
        }
        if (Stream.class.isAssignableFrom(getType())) {
            return getRelatedIfResolvable(getResolvableType().as(Stream.class).getGeneric(0));
        }
        return getRelatedIfResolvable(getResolvableType().asCollection().getGeneric(0));
    }

    public TypeDescriptor elementTypeDescriptor(Object element) {
        return narrow(element, getElementTypeDescriptor());
    }

    public boolean isMap() {
        return Map.class.isAssignableFrom(getType());
    }

    public TypeDescriptor getMapKeyTypeDescriptor() {
        Assert.state(isMap(), "Not a [java.util.Map]");
        return getRelatedIfResolvable(getResolvableType().asMap().getGeneric(0));
    }

    public TypeDescriptor getMapKeyTypeDescriptor(Object mapKey) {
        return narrow(mapKey, getMapKeyTypeDescriptor());
    }

    public TypeDescriptor getMapValueTypeDescriptor() {
        Assert.state(isMap(), "Not a [java.util.Map]");
        return getRelatedIfResolvable(getResolvableType().asMap().getGeneric(1));
    }

    public TypeDescriptor getMapValueTypeDescriptor(Object mapValue) {
        return narrow(mapValue, getMapValueTypeDescriptor());
    }

    private TypeDescriptor getRelatedIfResolvable(ResolvableType type) {
        if (type.resolve() == null) {
            return null;
        }
        return new TypeDescriptor(type, null, getAnnotations());
    }

    private TypeDescriptor narrow(Object value, TypeDescriptor typeDescriptor) {
        if (typeDescriptor != null) {
            return typeDescriptor.narrow(value);
        }
        if (value != null) {
            return narrow(value);
        }
        return null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TypeDescriptor otherDesc)) {
            return false;
        }
        if (getType() != otherDesc.getType()) {
            return false;
        }
        if (!annotationsMatch(otherDesc)) {
            return false;
        }
        return Arrays.equals(getResolvableType().getGenerics(), otherDesc.getResolvableType().getGenerics());
    }

    private boolean annotationsMatch(TypeDescriptor otherDesc) {
        Annotation[] anns = getAnnotations();
        Annotation[] otherAnns = otherDesc.getAnnotations();
        if (anns == otherAnns) {
            return true;
        }
        if (anns.length != otherAnns.length) {
            return false;
        }
        if (anns.length > 0) {
            for (int i = 0; i < anns.length; i++) {
                if (!annotationEquals(anns[i], otherAnns[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean annotationEquals(Annotation ann, Annotation otherAnn) {
        // Annotation.equals is reflective and pretty slow, so let's check identity and proxy type first.
        return (ann == otherAnn || (ann.getClass() == otherAnn.getClass() && ann.equals(otherAnn)));
    }

    @Override
    public int hashCode() {
        return getType().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (Annotation ann : getAnnotations()) {
            builder.append('@').append(getName(ann.annotationType())).append(' ');
        }
        builder.append(getResolvableType());
        return builder.toString();
    }

    @Contract("!null -> !null; null -> null")
    public static TypeDescriptor forObject(Object source) {
        return (source != null ? valueOf(source.getClass()) : null);
    }

    public static TypeDescriptor valueOf(Class<?> type) {
        if (type == null) {
            type = Object.class;
        }
        TypeDescriptor desc = commonTypesCache.get(type);
        return (desc != null ? desc : new TypeDescriptor(ResolvableType.forClass(type), null, null));
    }

    public static TypeDescriptor collection(Class<?> collectionType, TypeDescriptor elementTypeDescriptor) {
        Assert.notNull(collectionType, "Collection type must not be null");
        if (!Collection.class.isAssignableFrom(collectionType)) {
            throw new IllegalArgumentException("Collection type must be a [java.util.Collection]");
        }
        ResolvableType element = (elementTypeDescriptor != null ? elementTypeDescriptor.resolvableType : null);
        return new TypeDescriptor(ResolvableType.forClassWithGenerics(collectionType, element), null, null);
    }

    public static TypeDescriptor map(Class<?> mapType, TypeDescriptor keyTypeDescriptor,
                                     TypeDescriptor valueTypeDescriptor) {
        Assert.notNull(mapType, "Map type must not be null");
        if (!Map.class.isAssignableFrom(mapType)) {
            throw new IllegalArgumentException("Map type must be a [java.util.Map]");
        }
        ResolvableType key = (keyTypeDescriptor != null ? keyTypeDescriptor.resolvableType : null);
        ResolvableType value = (valueTypeDescriptor != null ? valueTypeDescriptor.resolvableType : null);
        return new TypeDescriptor(ResolvableType.forClassWithGenerics(mapType, key, value), null, null);
    }

    @Contract("!null -> !null; null -> null")
    public static TypeDescriptor array(TypeDescriptor elementTypeDescriptor) {
        if (elementTypeDescriptor == null) {
            return null;
        }
        return new TypeDescriptor(ResolvableType.forArrayComponent(elementTypeDescriptor.resolvableType),
                null, elementTypeDescriptor.getAnnotations());
    }

    public static TypeDescriptor nested(MethodParameter methodParameter, int nestingLevel) {
        if (methodParameter.getNestingLevel() != 1) {
            throw new IllegalArgumentException("MethodParameter nesting level must be 1: " +
                    "use the nestingLevel parameter to specify the desired nestingLevel for nested type traversal");
        }
        return new TypeDescriptor(methodParameter).nested(nestingLevel);
    }

    public static TypeDescriptor nested(Field field, int nestingLevel) {
        return new TypeDescriptor(field).nested(nestingLevel);
    }

    public static TypeDescriptor nested(Property property, int nestingLevel) {
        return new TypeDescriptor(property).nested(nestingLevel);
    }

    private static String getName(Class<?> clazz) {
        String canonicalName = clazz.getCanonicalName();
        return (canonicalName != null ? canonicalName : clazz.getName());
    }

    private interface AnnotatedElementSupplier extends Supplier<AnnotatedElementAdapter>, Serializable {
    }

}
