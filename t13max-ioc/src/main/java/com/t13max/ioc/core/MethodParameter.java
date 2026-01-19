package com.t13max.ioc.core;

import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ObjectUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * @Author: t13max
 * @Since: 22:25 2026/1/16
 */
public class MethodParameter {

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];
    private final Executable executable;

    private final int parameterIndex;

    private volatile Parameter parameter;

    private int nestingLevel;
    Map<Integer, Integer> typeIndexesPerLevel;
    private volatile Class<?> containingClass;

    private volatile Class<?> parameterType;

    private volatile Type genericParameterType;

    private volatile Annotation[] parameterAnnotations;

    private volatile ParameterNameDiscoverer parameterNameDiscoverer;

    volatile String parameterName;

    private volatile MethodParameter nestedMethodParameter;

    public MethodParameter(Method method, int parameterIndex) {
        this(method, parameterIndex, 1);
    }

    public MethodParameter(Method method, int parameterIndex, int nestingLevel) {
        Assert.notNull(method, "Method must not be null");
        this.executable = method;
        this.parameterIndex = validateIndex(method, parameterIndex);
        this.nestingLevel = nestingLevel;
    }

    public MethodParameter(Constructor<?> constructor, int parameterIndex) {
        this(constructor, parameterIndex, 1);
    }

    public MethodParameter(Constructor<?> constructor, int parameterIndex, int nestingLevel) {
        Assert.notNull(constructor, "Constructor must not be null");
        this.executable = constructor;
        this.parameterIndex = validateIndex(constructor, parameterIndex);
        this.nestingLevel = nestingLevel;
    }

    MethodParameter(Executable executable, int parameterIndex, Class<?> containingClass) {
        Assert.notNull(executable, "Executable must not be null");
        this.executable = executable;
        this.parameterIndex = validateIndex(executable, parameterIndex);
        this.nestingLevel = 1;
        this.containingClass = containingClass;
    }

    public MethodParameter(MethodParameter original) {
        Assert.notNull(original, "Original must not be null");
        this.executable = original.executable;
        this.parameterIndex = original.parameterIndex;
        this.parameter = original.parameter;
        this.nestingLevel = original.nestingLevel;
        this.typeIndexesPerLevel = original.typeIndexesPerLevel;
        this.containingClass = original.containingClass;
        this.parameterType = original.parameterType;
        this.genericParameterType = original.genericParameterType;
        this.parameterAnnotations = original.parameterAnnotations;
        this.parameterNameDiscoverer = original.parameterNameDiscoverer;
        this.parameterName = original.parameterName;
    }

    public Method getMethod() {
        return (this.executable instanceof Method method ? method : null);
    }

    public Constructor<?> getConstructor() {
        return (this.executable instanceof Constructor<?> constructor ? constructor : null);
    }

    public Class<?> getDeclaringClass() {
        return this.executable.getDeclaringClass();
    }

    public Member getMember() {
        return this.executable;
    }

    public AnnotatedElement getAnnotatedElement() {
        return this.executable;
    }

    public Executable getExecutable() {
        return this.executable;
    }

    public Parameter getParameter() {
        if (this.parameterIndex < 0) {
            throw new IllegalStateException("Cannot retrieve Parameter descriptor for method return type");
        }
        Parameter parameter = this.parameter;
        if (parameter == null) {
            parameter = getExecutable().getParameters()[this.parameterIndex];
            this.parameter = parameter;
        }
        return parameter;
    }

    public int getParameterIndex() {
        return this.parameterIndex;
    }

    @Deprecated(since = "5.2")
    public void increaseNestingLevel() {
        this.nestingLevel++;
    }

    @Deprecated(since = "5.2")
    public void decreaseNestingLevel() {
        getTypeIndexesPerLevel().remove(this.nestingLevel);
        this.nestingLevel--;
    }

    public int getNestingLevel() {
        return this.nestingLevel;
    }

    public MethodParameter withTypeIndex(int typeIndex) {
        return nested(this.nestingLevel, typeIndex);
    }

    @Deprecated(since = "5.2")
    public void setTypeIndexForCurrentLevel(int typeIndex) {
        getTypeIndexesPerLevel().put(this.nestingLevel, typeIndex);
    }

    public Integer getTypeIndexForCurrentLevel() {
        return getTypeIndexForLevel(this.nestingLevel);
    }

    public Integer getTypeIndexForLevel(int nestingLevel) {
        return getTypeIndexesPerLevel().get(nestingLevel);
    }

    private Map<Integer, Integer> getTypeIndexesPerLevel() {
        if (this.typeIndexesPerLevel == null) {
            this.typeIndexesPerLevel = new HashMap<>(4);
        }
        return this.typeIndexesPerLevel;
    }

    public MethodParameter nested() {
        return nested(null);
    }

    public MethodParameter nested(Integer typeIndex) {
        MethodParameter nestedParam = this.nestedMethodParameter;
        if (nestedParam != null && typeIndex == null) {
            return nestedParam;
        }
        nestedParam = nested(this.nestingLevel + 1, typeIndex);
        if (typeIndex == null) {
            this.nestedMethodParameter = nestedParam;
        }
        return nestedParam;
    }

    private MethodParameter nested(int nestingLevel, Integer typeIndex) {
        MethodParameter copy = clone();
        copy.nestingLevel = nestingLevel;
        if (this.typeIndexesPerLevel != null) {
            copy.typeIndexesPerLevel = new HashMap<>(this.typeIndexesPerLevel);
        }
        if (typeIndex != null) {
            copy.getTypeIndexesPerLevel().put(copy.nestingLevel, typeIndex);
        }
        copy.parameterType = null;
        copy.genericParameterType = null;
        return copy;
    }

    public boolean isOptional() {
        return (getParameterType() == Optional.class || Nullness.forMethodParameter(this) == Nullness.NULLABLE ||
                (KotlinDetector.isKotlinType(getContainingClass()) && KotlinDelegate.isOptional(this)));
    }

    public MethodParameter nestedIfOptional() {
        return (getParameterType() == Optional.class ? nested() : this);
    }

    public MethodParameter withContainingClass(Class<?> containingClass) {
        MethodParameter result = clone();
        result.containingClass = containingClass;
        result.parameterType = null;
        return result;
    }

    @Deprecated(since = "5.2")
    void setContainingClass(Class<?> containingClass) {
        this.containingClass = containingClass;
        this.parameterType = null;
    }

    public Class<?> getContainingClass() {
        Class<?> containingClass = this.containingClass;
        return (containingClass != null ? containingClass : getDeclaringClass());
    }

    @Deprecated(since = "5.2")
    void setParameterType(Class<?> parameterType) {
        this.parameterType = parameterType;
    }

    public Class<?> getParameterType() {
        Class<?> paramType = this.parameterType;
        if (paramType != null) {
            return paramType;
        }
        if (getContainingClass() != getDeclaringClass()) {
            paramType = ResolvableType.forMethodParameter(this, null, 1).resolve();
        }
        if (paramType == null) {
            paramType = computeParameterType();
        }
        this.parameterType = paramType;
        return paramType;
    }

    public Type getGenericParameterType() {
        Type paramType = this.genericParameterType;
        if (paramType == null) {
            if (this.parameterIndex < 0) {
                Method method = getMethod();
                paramType = (method != null ?
                        (KotlinDetector.isKotlinType(getContainingClass()) ?
                                KotlinDelegate.getGenericReturnType(method) : method.getGenericReturnType()) : void.class);
            } else {
                Type[] genericParameterTypes = this.executable.getGenericParameterTypes();
                int index = this.parameterIndex;
                if (this.executable instanceof Constructor &&
                        ClassUtils.isInnerClass(this.executable.getDeclaringClass()) &&
                        genericParameterTypes.length == this.executable.getParameterCount() - 1) {
                    // Bug in javac: type array excludes enclosing instance parameter
                    // for inner classes with at least one generic constructor parameter,
                    // so access it with the actual parameter index lowered by 1
                    index = this.parameterIndex - 1;
                }
                paramType = (index >= 0 && index < genericParameterTypes.length ?
                        genericParameterTypes[index] : computeParameterType());
            }
            this.genericParameterType = paramType;
        }
        return paramType;
    }

    private Class<?> computeParameterType() {
        if (this.parameterIndex < 0) {
            Method method = getMethod();
            if (method == null) {
                return void.class;
            }
            if (KotlinDetector.isKotlinType(getContainingClass())) {
                return KotlinDelegate.getReturnType(method);
            }
            return method.getReturnType();
        }
        return this.executable.getParameterTypes()[this.parameterIndex];
    }

    public Class<?> getNestedParameterType() {
        if (this.nestingLevel > 1) {
            Type type = getGenericParameterType();
            for (int i = 2; i <= this.nestingLevel; i++) {
                if (type instanceof ParameterizedType parameterizedType) {
                    Type[] args = parameterizedType.getActualTypeArguments();
                    Integer index = getTypeIndexForLevel(i);
                    type = args[index != null ? index : args.length - 1];
                }
                // TODO: Object.class if unresolvable
            }
            if (type instanceof Class<?> clazz) {
                return clazz;
            } else if (type instanceof ParameterizedType parameterizedType) {
                Type arg = parameterizedType.getRawType();
                if (arg instanceof Class<?> clazz) {
                    return clazz;
                }
            }
            return Object.class;
        } else {
            return getParameterType();
        }
    }

    public Type getNestedGenericParameterType() {
        if (this.nestingLevel > 1) {
            Type type = getGenericParameterType();
            for (int i = 2; i <= this.nestingLevel; i++) {
                if (type instanceof ParameterizedType parameterizedType) {
                    Type[] args = parameterizedType.getActualTypeArguments();
                    Integer index = getTypeIndexForLevel(i);
                    type = args[index != null ? index : args.length - 1];
                }
            }
            return type;
        } else {
            return getGenericParameterType();
        }
    }

    public Annotation[] getMethodAnnotations() {
        return adaptAnnotationArray(getAnnotatedElement().getAnnotations());
    }

    public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
        A annotation = getAnnotatedElement().getAnnotation(annotationType);
        return (annotation != null ? adaptAnnotation(annotation) : null);
    }

    public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
        return getAnnotatedElement().isAnnotationPresent(annotationType);
    }

    public Annotation[] getParameterAnnotations() {
        Annotation[] paramAnns = this.parameterAnnotations;
        if (paramAnns == null) {
            Annotation[][] annotationArray = this.executable.getParameterAnnotations();
            int index = this.parameterIndex;
            if (this.executable instanceof Constructor &&
                    ClassUtils.isInnerClass(this.executable.getDeclaringClass()) &&
                    annotationArray.length == this.executable.getParameterCount() - 1) {
                // Bug in javac in JDK <9: annotation array excludes enclosing instance parameter
                // for inner classes, so access it with the actual parameter index lowered by 1
                index = this.parameterIndex - 1;
            }
            paramAnns = (index >= 0 && index < annotationArray.length && annotationArray[index].length > 0 ?
                    adaptAnnotationArray(annotationArray[index]) : EMPTY_ANNOTATION_ARRAY);
            this.parameterAnnotations = paramAnns;
        }
        return paramAnns;
    }

    public boolean hasParameterAnnotations() {
        return (getParameterAnnotations().length != 0);
    }

    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getParameterAnnotation(Class<A> annotationType) {
        Annotation[] anns = getParameterAnnotations();
        for (Annotation ann : anns) {
            if (annotationType.isInstance(ann)) {
                return (A) ann;
            }
        }
        return null;
    }

    public <A extends Annotation> boolean hasParameterAnnotation(Class<A> annotationType) {
        return (getParameterAnnotation(annotationType) != null);
    }

    public void initParameterNameDiscovery(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }

    public String getParameterName() {
        if (this.parameterIndex < 0) {
            return null;
        }
        ParameterNameDiscoverer discoverer = this.parameterNameDiscoverer;
        if (discoverer != null) {
            String[] parameterNames = null;
            if (this.executable instanceof Method method) {
                parameterNames = discoverer.getParameterNames(method);
            } else if (this.executable instanceof Constructor<?> constructor) {
                parameterNames = discoverer.getParameterNames(constructor);
            }
            if (parameterNames != null && this.parameterIndex < parameterNames.length) {
                this.parameterName = parameterNames[this.parameterIndex];
            }
            this.parameterNameDiscoverer = null;
        }
        return this.parameterName;
    }


    protected <A extends Annotation> A adaptAnnotation(A annotation) {
        return annotation;
    }

    protected Annotation[] adaptAnnotationArray(Annotation[] annotations) {
        return annotations;
    }


    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof MethodParameter that &&
                getContainingClass() == that.getContainingClass() &&
                ObjectUtils.nullSafeEquals(this.typeIndexesPerLevel, that.typeIndexesPerLevel) &&
                this.nestingLevel == that.nestingLevel &&
                this.parameterIndex == that.parameterIndex &&
                this.executable.equals(that.executable)));
    }

    @Override
    public int hashCode() {
        return (31 * this.executable.hashCode() + this.parameterIndex);
    }

    @Override
    public String toString() {
        Method method = getMethod();
        return (method != null ? "method '" + method.getName() + "'" : "constructor") +
                " parameter " + this.parameterIndex;
    }

    @Override
    public MethodParameter clone() {
        return new MethodParameter(this);
    }


    @Deprecated(since = "5.0")
    public static MethodParameter forMethodOrConstructor(Object methodOrConstructor, int parameterIndex) {
        if (!(methodOrConstructor instanceof Executable executable)) {
            throw new IllegalArgumentException(
                    "Given object [" + methodOrConstructor + "] is neither a Method nor a Constructor");
        }
        return forExecutable(executable, parameterIndex);
    }

    public static MethodParameter forExecutable(Executable executable, int parameterIndex) {
        if (executable instanceof Method method) {
            return new MethodParameter(method, parameterIndex);
        } else if (executable instanceof Constructor<?> constructor) {
            return new MethodParameter(constructor, parameterIndex);
        } else {
            throw new IllegalArgumentException("Not a Method/Constructor: " + executable);
        }
    }

    public static MethodParameter forParameter(Parameter parameter) {
        return forExecutable(parameter.getDeclaringExecutable(), findParameterIndex(parameter));
    }

    protected static int findParameterIndex(Parameter parameter) {
        Executable executable = parameter.getDeclaringExecutable();
        Parameter[] allParams = executable.getParameters();
        // Try first with identity checks for greater performance.
        for (int i = 0; i < allParams.length; i++) {
            if (parameter == allParams[i]) {
                return i;
            }
        }
        // Potentially try again with object equality checks in order to avoid race
        // conditions while invoking java.lang.reflect.Executable.getParameters().
        for (int i = 0; i < allParams.length; i++) {
            if (parameter.equals(allParams[i])) {
                return i;
            }
        }
        throw new IllegalArgumentException("Given parameter [" + parameter +
                "] does not match any parameter in the declaring executable");
    }

    private static int validateIndex(Executable executable, int parameterIndex) {
        int count = executable.getParameterCount();
        Assert.isTrue(parameterIndex >= -1 && parameterIndex < count,
                () -> "Parameter index needs to be between -1 and " + (count - 1));
        return parameterIndex;
    }

    public static MethodParameter forFieldAwareConstructor(Constructor<?> ctor, int parameterIndex, String fieldName) {
        return new FieldAwareConstructorParameter(ctor, parameterIndex, fieldName);
    }


    private static class FieldAwareConstructorParameter extends MethodParameter {

        private volatile Annotation[] combinedAnnotations;

        public FieldAwareConstructorParameter(Constructor<?> constructor, int parameterIndex, String fieldName) {
            super(constructor, parameterIndex);
            this.parameterName = fieldName;
        }

        @Override
        public Annotation[] getParameterAnnotations() {
            String parameterName = this.parameterName;
            Assert.state(parameterName != null, "Parameter name not initialized");

            Annotation[] anns = this.combinedAnnotations;
            if (anns == null) {
                anns = super.getParameterAnnotations();
                try {
                    Field field = getDeclaringClass().getDeclaredField(parameterName);
                    Annotation[] fieldAnns = field.getAnnotations();
                    if (fieldAnns.length > 0) {
                        List<Annotation> merged = new ArrayList<>(anns.length + fieldAnns.length);
                        merged.addAll(Arrays.asList(anns));
                        for (Annotation fieldAnn : fieldAnns) {
                            boolean existingType = false;
                            for (Annotation ann : anns) {
                                if (ann.annotationType() == fieldAnn.annotationType()) {
                                    existingType = true;
                                    break;
                                }
                            }
                            if (!existingType) {
                                merged.add(fieldAnn);
                            }
                        }
                        anns = merged.toArray(EMPTY_ANNOTATION_ARRAY);
                    }
                } catch (NoSuchFieldException | SecurityException ex) {
                    // ignore
                }
                this.combinedAnnotations = anns;
            }
            return anns;
        }
    }


    private static class KotlinDelegate {


        public static boolean isOptional(MethodParameter param) {
            Method method = param.getMethod();
            int index = param.getParameterIndex();
            if (method != null && index == -1) {
                KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
                return (function != null && function.getReturnType().isMarkedNullable());
            }
            KFunction<?> function;
            Predicate<KParameter> predicate;
            if (method != null) {
                if (param.getParameterType().getName().equals("kotlin.coroutines.Continuation")) {
                    return true;
                }
                function = ReflectJvmMapping.getKotlinFunction(method);
                predicate = p -> KParameter.Kind.VALUE.equals(p.getKind());
            } else {
                Constructor<?> ctor = param.getConstructor();
                Assert.state(ctor != null, "Neither method nor constructor found");
                function = ReflectJvmMapping.getKotlinFunction(ctor);
                predicate = p -> (KParameter.Kind.VALUE.equals(p.getKind()) ||
                        KParameter.Kind.INSTANCE.equals(p.getKind()));
            }
            if (function != null) {
                int i = 0;
                for (KParameter kParameter : function.getParameters()) {
                    if (predicate.test(kParameter)) {
                        if (index == i++) {
                            return (kParameter.getType().isMarkedNullable() || kParameter.isOptional());
                        }
                    }
                }
            }
            return false;
        }


        private static Type getGenericReturnType(Method method) {
            try {
                KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
                if (function != null && function.isSuspend()) {
                    return ReflectJvmMapping.getJavaType(function.getReturnType());
                }
            } catch (UnsupportedOperationException ex) {
                // probably a synthetic class - let's use java reflection instead
            }
            return method.getGenericReturnType();
        }


        private static Class<?> getReturnType(Method method) {
            try {
                KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
                if (function != null && function.isSuspend()) {
                    Type paramType = ReflectJvmMapping.getJavaType(function.getReturnType());
                    if (paramType == Unit.class) {
                        paramType = void.class;
                    }
                    return ResolvableType.forType(paramType).resolve(method.getReturnType());
                }
            } catch (UnsupportedOperationException ex) {
                // probably a synthetic class - let's use java reflection instead
            }
            return method.getReturnType();
        }
    }
}
