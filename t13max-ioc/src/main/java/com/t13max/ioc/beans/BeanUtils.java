package com.t13max.ioc.beans;

import com.t13max.ioc.core.*;
import com.t13max.ioc.utils.*;

import java.beans.ConstructorProperties;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.lang.reflect.*;
import java.util.*;

/**
 * @Author: t13max
 * @Since: 21:08 2026/1/16
 */
public class BeanUtils {

    private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private static final Set<Class<?>> unknownEditorTypes = Collections.newSetFromMap(new ConcurrentReferenceHashMap<>(64));

    private static final Map<Class<?>, Object> DEFAULT_TYPE_VALUES = Map.of(
            boolean.class, false,
            byte.class, (byte) 0,
            short.class, (short) 0,
            int.class, 0,
            long.class, 0L,
            float.class, 0F,
            double.class, 0D,
            char.class, '\0');

    @Deprecated(since = "5.0")
    public static <T> T instantiate(Class<T> clazz) throws BeanInstantiationException {
        Assert.notNull(clazz, "Class must not be null");
        if (clazz.isInterface()) {
            throw new BeanInstantiationException(clazz, "Specified class is an interface");
        }
        try {
            return clazz.newInstance();
        } catch (InstantiationException ex) {
            throw new BeanInstantiationException(clazz, "Is it an abstract class?", ex);
        } catch (IllegalAccessException ex) {
            throw new BeanInstantiationException(clazz, "Is the constructor accessible?", ex);
        }
    }

    public static <T> T instantiateClass(Class<T> clazz) throws BeanInstantiationException {
        Assert.notNull(clazz, "Class must not be null");
        if (clazz.isInterface()) {
            throw new BeanInstantiationException(clazz, "Specified class is an interface");
        }
        Constructor<T> ctor;
        try {
            ctor = clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException ex) {
            ctor = findPrimaryConstructor(clazz);
            if (ctor == null) {
                throw new BeanInstantiationException(clazz, "No default constructor found", ex);
            }
        } catch (LinkageError err) {
            throw new BeanInstantiationException(clazz, "Unresolvable class definition", err);
        }
        return instantiateClass(ctor);
    }

    @SuppressWarnings("unchecked")
    public static <T> T instantiateClass(Class<?> clazz, Class<T> assignableTo) throws BeanInstantiationException {
        Assert.isAssignable(assignableTo, clazz);
        return (T) instantiateClass(clazz);
    }

    public static <T> T instantiateClass(Constructor<T> ctor, Object... args) throws BeanInstantiationException {
        Assert.notNull(ctor, "Constructor must not be null");
        try {
            ReflectionUtils.makeAccessible(ctor);
            /*if (KotlinDetector.isKotlinType(ctor.getDeclaringClass())) {
                return KotlinDelegate.instantiateClass(ctor, args);
            } else {

            }*/
            int parameterCount = ctor.getParameterCount();
            Assert.isTrue(args.length <= parameterCount, "Can't specify more arguments than constructor parameters");
            if (parameterCount == 0) {
                return ctor.newInstance();
            }
            Class<?>[] parameterTypes = ctor.getParameterTypes();
            Object[] argsWithDefaultValues = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    Class<?> parameterType = parameterTypes[i];
                    argsWithDefaultValues[i] = (parameterType.isPrimitive() ? DEFAULT_TYPE_VALUES.get(parameterType) : null);
                } else {
                    argsWithDefaultValues[i] = args[i];
                }
            }
            return ctor.newInstance(argsWithDefaultValues);
        } catch (InstantiationException ex) {
            throw new BeanInstantiationException(ctor, "Is it an abstract class?", ex);
        } catch (IllegalAccessException ex) {
            throw new BeanInstantiationException(ctor, "Is the constructor accessible?", ex);
        } catch (IllegalArgumentException ex) {
            throw new BeanInstantiationException(ctor, "Illegal arguments for constructor", ex);
        } catch (InvocationTargetException ex) {
            throw new BeanInstantiationException(ctor, "Constructor threw exception", ex.getTargetException());
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> getResolvableConstructor(Class<T> clazz) {
        Constructor<T> ctor = findPrimaryConstructor(clazz);
        if (ctor != null) {
            return ctor;
        }

        Constructor<?>[] ctors = clazz.getConstructors();
        if (ctors.length == 1) {
            // A single public constructor
            return (Constructor<T>) ctors[0];
        } else if (ctors.length == 0) {
            // No public constructors -> check non-public
            ctors = clazz.getDeclaredConstructors();
            if (ctors.length == 1) {
                // A single non-public constructor, for example, from a non-public record type
                return (Constructor<T>) ctors[0];
            }
        }

        // Several constructors -> let's try to take the default constructor
        try {
            return clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException ex) {
            // Giving up...
        }

        // No unique constructor at all
        throw new IllegalStateException("No primary or single unique constructor found for " + clazz);
    }

    public static <T> Constructor<T> findPrimaryConstructor(Class<T> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        /*if (KotlinDetector.isKotlinType(clazz)) {
            return KotlinDelegate.findPrimaryConstructor(clazz);
        }*/
        if (clazz.isRecord()) {
            try {
                // Use the canonical constructor which is always present
                RecordComponent[] components = clazz.getRecordComponents();
                Class<?>[] paramTypes = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    paramTypes[i] = components[i].getType();
                }
                return clazz.getDeclaredConstructor(paramTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public static Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException ex) {
            return findDeclaredMethod(clazz, methodName, paramTypes);
        }
    }

    public static Method findDeclaredMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getDeclaredMethod(methodName, paramTypes);
        } catch (NoSuchMethodException ex) {
            if (clazz.getSuperclass() != null) {
                return findDeclaredMethod(clazz.getSuperclass(), methodName, paramTypes);
            }
            return null;
        }
    }

    public static Method findMethodWithMinimalParameters(Class<?> clazz, String methodName)
            throws IllegalArgumentException {

        Method targetMethod = findMethodWithMinimalParameters(clazz.getMethods(), methodName);
        if (targetMethod == null) {
            targetMethod = findDeclaredMethodWithMinimalParameters(clazz, methodName);
        }
        return targetMethod;
    }

    public static Method findDeclaredMethodWithMinimalParameters(Class<?> clazz, String methodName)
            throws IllegalArgumentException {

        Method targetMethod = findMethodWithMinimalParameters(clazz.getDeclaredMethods(), methodName);
        if (targetMethod == null && clazz.getSuperclass() != null) {
            targetMethod = findDeclaredMethodWithMinimalParameters(clazz.getSuperclass(), methodName);
        }
        return targetMethod;
    }

    public static Method findMethodWithMinimalParameters(Method[] methods, String methodName)
            throws IllegalArgumentException {

        Method targetMethod = null;
        int numMethodsFoundWithCurrentMinimumArgs = 0;
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                int numParams = method.getParameterCount();
                if (targetMethod == null || numParams < targetMethod.getParameterCount()) {
                    targetMethod = method;
                    numMethodsFoundWithCurrentMinimumArgs = 1;
                } else if (!method.isBridge() && targetMethod.getParameterCount() == numParams) {
                    if (targetMethod.isBridge()) {
                        // Prefer regular method over bridge...
                        targetMethod = method;
                    } else {
                        // Additional candidate with same length
                        numMethodsFoundWithCurrentMinimumArgs++;
                    }
                }
            }
        }
        if (numMethodsFoundWithCurrentMinimumArgs > 1) {
            throw new IllegalArgumentException("Cannot resolve method '" + methodName +
                    "' to a unique method. Attempted to resolve to overloaded method with " +
                    "the least number of parameters but there were " +
                    numMethodsFoundWithCurrentMinimumArgs + " candidates.");
        }
        return targetMethod;
    }

    public static Method resolveSignature(String signature, Class<?> clazz) {
        Assert.hasText(signature, "'signature' must not be empty");
        Assert.notNull(clazz, "Class must not be null");
        int startParen = signature.indexOf('(');
        int endParen = signature.indexOf(')');
        if (startParen > -1 && endParen == -1) {
            throw new IllegalArgumentException("Invalid method signature '" + signature +
                    "': expected closing ')' for args list");
        } else if (startParen == -1 && endParen > -1) {
            throw new IllegalArgumentException("Invalid method signature '" + signature +
                    "': expected opening '(' for args list");
        } else if (startParen == -1) {
            return findMethodWithMinimalParameters(clazz, signature);
        } else {
            String methodName = signature.substring(0, startParen);
            String[] parameterTypeNames =
                    StringUtils.commaDelimitedListToStringArray(signature.substring(startParen + 1, endParen));
            Class<?>[] parameterTypes = new Class<?>[parameterTypeNames.length];
            for (int i = 0; i < parameterTypeNames.length; i++) {
                String parameterTypeName = parameterTypeNames[i].trim();
                try {
                    parameterTypes[i] = ClassUtils.forName(parameterTypeName, clazz.getClassLoader());
                } catch (Throwable ex) {
                    throw new IllegalArgumentException("Invalid method signature: unable to resolve type [" +
                            parameterTypeName + "] for argument " + i + ". Root cause: " + ex);
                }
            }
            return findMethod(clazz, methodName, parameterTypes);
        }
    }

    public static PropertyDescriptor[] getPropertyDescriptors(Class<?> clazz) throws BeansException {
        return CachedIntrospectionResults.forClass(clazz).getPropertyDescriptors();
    }

    public static PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) throws BeansException {
        return CachedIntrospectionResults.forClass(clazz).getPropertyDescriptor(propertyName);
    }

    public static PropertyDescriptor findPropertyForMethod(Method method) throws BeansException {
        return findPropertyForMethod(method, method.getDeclaringClass());
    }

    public static PropertyDescriptor findPropertyForMethod(Method method, Class<?> clazz) throws BeansException {
        Assert.notNull(method, "Method must not be null");
        PropertyDescriptor[] pds = getPropertyDescriptors(clazz);
        for (PropertyDescriptor pd : pds) {
            if (method.equals(pd.getReadMethod()) || method.equals(pd.getWriteMethod())) {
                return pd;
            }
        }
        return null;
    }

    public static PropertyEditor findEditorByConvention(Class<?> targetType) {
        if (targetType == null || targetType.isArray() || unknownEditorTypes.contains(targetType)) {
            return null;
        }

        ClassLoader cl = targetType.getClassLoader();
        if (cl == null) {
            try {
                cl = ClassLoader.getSystemClassLoader();
                if (cl == null) {
                    return null;
                }
            } catch (Throwable ex) {
                // for example, AccessControlException on Google App Engine
                return null;
            }
        }

        String targetTypeName = targetType.getName();
        String editorName = targetTypeName + "Editor";
        try {
            Class<?> editorClass = cl.loadClass(editorName);
            if (editorClass != null) {
                if (!PropertyEditor.class.isAssignableFrom(editorClass)) {
                    unknownEditorTypes.add(targetType);
                    return null;
                }
                return (PropertyEditor) instantiateClass(editorClass);
            }
            // Misbehaving ClassLoader returned null instead of ClassNotFoundException
            // - fall back to unknown editor type registration below
        } catch (ClassNotFoundException ex) {
            // Ignore - fall back to unknown editor type registration below
        }
        unknownEditorTypes.add(targetType);
        return null;
    }

    public static Class<?> findPropertyType(String propertyName, Class<?>... beanClasses) {
        if (beanClasses != null) {
            for (Class<?> beanClass : beanClasses) {
                PropertyDescriptor pd = getPropertyDescriptor(beanClass, propertyName);
                if (pd != null) {
                    return pd.getPropertyType();
                }
            }
        }
        return Object.class;
    }

    public static boolean hasUniqueWriteMethod(PropertyDescriptor pd) {
        if (pd instanceof GenericTypeAwarePropertyDescriptor gpd) {
            return gpd.hasUniqueWriteMethod();
        } else {
            return (pd.getWriteMethod() != null);
        }
    }

    public static MethodParameter getWriteMethodParameter(PropertyDescriptor pd) {
        if (pd instanceof GenericTypeAwarePropertyDescriptor gpd) {
            return new MethodParameter(gpd.getWriteMethodParameter());
        } else {
            Method writeMethod = pd.getWriteMethod();
            Assert.state(writeMethod != null, "No write method available");
            return new MethodParameter(writeMethod, 0);
        }
    }

    /*@SuppressWarnings("NullAway") // Dataflow analysis limitation
    public static String[] getParameterNames(Constructor<?> ctor) {
        ConstructorProperties cp = ctor.getAnnotation(ConstructorProperties.class);
        String[] paramNames = (cp != null ? cp.value() : parameterNameDiscoverer.getParameterNames(ctor));
        Assert.state(paramNames != null, () -> "Cannot resolve parameter names for constructor " + ctor);
        int parameterCount = (KotlinDetector.isKotlinReflectPresent() && KotlinDelegate.hasDefaultConstructorMarker(ctor) ?
                ctor.getParameterCount() - 1 : ctor.getParameterCount());
        Assert.state(paramNames.length == parameterCount,
                () -> "Invalid number of parameter names: " + paramNames.length + " for constructor " + ctor);
        return paramNames;
    }*/

    public static boolean isSimpleProperty(Class<?> type) {
        Assert.notNull(type, "'type' must not be null");
        return isSimpleValueType(type) || (type.isArray() && isSimpleValueType(type.componentType()));
    }

    public static boolean isSimpleValueType(Class<?> type) {
        return ClassUtils.isSimpleValueType(type);
    }

    public static void copyProperties(Object source, Object target) throws BeansException {
        copyProperties(source, target, null, (String[]) null);
    }

    public static void copyProperties(Object source, Object target, Class<?> editable) throws BeansException {
        copyProperties(source, target, editable, (String[]) null);
    }

    public static void copyProperties(Object source, Object target, String... ignoreProperties) throws BeansException {
        copyProperties(source, target, null, ignoreProperties);
    }

    private static void copyProperties(Object source, Object target, Class<?> editable,
                                       String... ignoreProperties) throws BeansException {

        Assert.notNull(source, "Source must not be null");
        Assert.notNull(target, "Target must not be null");

        Class<?> actualEditable = target.getClass();
        if (editable != null) {
            if (!editable.isInstance(target)) {
                throw new IllegalArgumentException("Target class [" + target.getClass().getName() +
                        "] not assignable to editable class [" + editable.getName() + "]");
            }
            actualEditable = editable;
        }
        PropertyDescriptor[] targetPds = getPropertyDescriptors(actualEditable);
        Set<String> ignoredProps = (ignoreProperties != null ? new HashSet<>(Arrays.asList(ignoreProperties)) : null);
        CachedIntrospectionResults sourceResults = (actualEditable != source.getClass() ?
                CachedIntrospectionResults.forClass(source.getClass()) : null);

        for (PropertyDescriptor targetPd : targetPds) {
            Method writeMethod = targetPd.getWriteMethod();
            if (writeMethod != null && (ignoredProps == null || !ignoredProps.contains(targetPd.getName()))) {
                PropertyDescriptor sourcePd = (sourceResults != null ?
                        sourceResults.getPropertyDescriptor(targetPd.getName()) : targetPd);
                if (sourcePd != null) {
                    Method readMethod = sourcePd.getReadMethod();
                    if (readMethod != null) {
                        if (isAssignable(writeMethod, readMethod, sourcePd, targetPd)) {
                            try {
                                ReflectionUtils.makeAccessible(readMethod);
                                Object value = readMethod.invoke(source);
                                ReflectionUtils.makeAccessible(writeMethod);
                                writeMethod.invoke(target, value);
                            } catch (Throwable ex) {
                                throw new FatalBeanException(
                                        "Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isAssignable(Method writeMethod, Method readMethod, PropertyDescriptor sourcePd, PropertyDescriptor targetPd) {

        Type paramType = writeMethod.getGenericParameterTypes()[0];
        if (paramType instanceof Class<?> clazz) {
            return ClassUtils.isAssignable(clazz, readMethod.getReturnType());
        } else if (paramType.equals(readMethod.getGenericReturnType())) {
            return true;
        } else {
            ResolvableType sourceType = ((GenericTypeAwarePropertyDescriptor) sourcePd).getReadMethodType();
            ResolvableType targetType = ((GenericTypeAwarePropertyDescriptor) targetPd).getWriteMethodType();
            // Ignore generic types in assignable check if either ResolvableType has unresolvable generics.
            return (sourceType.hasUnresolvableGenerics() || targetType.hasUnresolvableGenerics() ?
                    ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], readMethod.getReturnType()) :
                    targetType.isAssignableFrom(sourceType));
        }
    }

    /*private static class KotlinDelegate {

        
        @SuppressWarnings("unchecked")
        public static <T>  Constructor<T> findPrimaryConstructor(Class<T> clazz) {
            try {
                KClass<T> kClass = JvmClassMappingKt.getKotlinClass(clazz);
                KFunction<T> primaryCtor = KClasses.getPrimaryConstructor(kClass);
                if (primaryCtor == null) {
                    return null;
                }
                if (KotlinDetector.isInlineClass(clazz)) {
                    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                    Assert.state(constructors.length == 1,
                            "Kotlin value classes annotated with @JvmInline are expected to have a single JVM constructor");
                    return (Constructor<T>) constructors[0];
                }
                Constructor<T> constructor = ReflectJvmMapping.getJavaConstructor(primaryCtor);
                if (constructor == null) {
                    throw new IllegalStateException(
                            "Failed to find Java constructor for Kotlin primary constructor: " + clazz.getName());
                }
                return constructor;
            }
            catch (UnsupportedOperationException ex) {
                return null;
            }
        }

        
        public static <T> T instantiateClass(Constructor<T> ctor,  Object... args)
                throws IllegalAccessException, InvocationTargetException, InstantiationException {

            KFunction<T> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(ctor);
            if (kotlinConstructor == null) {
                return ctor.newInstance(args);
            }

            if ((!Modifier.isPublic(ctor.getModifiers()) || !Modifier.isPublic(ctor.getDeclaringClass().getModifiers()))) {
                KCallablesJvm.setAccessible(kotlinConstructor, true);
            }

            List<KParameter> parameters = kotlinConstructor.getParameters();

            Assert.isTrue(args.length <= parameters.size(),
                    "Number of provided arguments must be less than or equal to the number of constructor parameters");
            if (parameters.isEmpty()) {
                return kotlinConstructor.call();
            }
            Map<KParameter, Object> argParameters = CollectionUtils.newHashMap(parameters.size());
            for (int i = 0 ; i < args.length ; i++) {
                if (!(parameters.get(i).isOptional() && args[i] == null)) {
                    argParameters.put(parameters.get(i), args[i]);
                }
            }
            return kotlinConstructor.callBy(argParameters);
        }

        public static boolean hasDefaultConstructorMarker(Constructor<?> ctor) {
            int parameterCount = ctor.getParameterCount();
            return parameterCount > 0 && ctor.getParameters()[parameterCount -1].getType() == DefaultConstructorMarker.class;
        }
    }*/

}
