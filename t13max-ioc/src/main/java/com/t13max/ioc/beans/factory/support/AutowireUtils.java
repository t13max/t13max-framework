package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeanMetadataElement;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;
import com.t13max.ioc.beans.factory.ObjectFactory;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.ConfigurableBeanFactory;
import com.t13max.ioc.beans.factory.config.TypedStringValue;
import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * @Author: t13max
 * @Since: 0:23 2026/1/17
 */
public class AutowireUtils {

    public static final Comparator<Executable> EXECUTABLE_COMPARATOR = (e1, e2) -> {
        int result = Boolean.compare(Modifier.isPublic(e2.getModifiers()), Modifier.isPublic(e1.getModifiers()));
        return (result != 0 ? result : Integer.compare(e2.getParameterCount(), e1.getParameterCount()));
    };


    
    public static void sortConstructors(Constructor<?>[] constructors) {
        Arrays.sort(constructors, EXECUTABLE_COMPARATOR);
    }

    
    public static void sortFactoryMethods(Method[] factoryMethods) {
        Arrays.sort(factoryMethods, EXECUTABLE_COMPARATOR);
    }

    
    public static boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
        Method wm = pd.getWriteMethod();
        if (wm == null) {
            return false;
        }
        if (!wm.getDeclaringClass().getName().contains("$$")) {
            // Not a CGLIB method so it's OK.
            return false;
        }
        // It was declared by CGLIB, but we might still want to autowire it
        // if it was actually declared by the superclass.
        Class<?> superclass = wm.getDeclaringClass().getSuperclass();
        return !ClassUtils.hasMethod(superclass, wm);
    }

    
    public static boolean isSetterDefinedInInterface(PropertyDescriptor pd, Set<Class<?>> interfaces) {
        Method setter = pd.getWriteMethod();
        if (setter != null) {
            Class<?> targetClass = setter.getDeclaringClass();
            for (Class<?> ifc : interfaces) {
                if (ifc.isAssignableFrom(targetClass) && ClassUtils.hasMethod(ifc, setter)) {
                    return true;
                }
            }
        }
        return false;
    }

    
    public static Object resolveAutowiringValue(Object autowiringValue, Class<?> requiredType) {
        if (autowiringValue instanceof ObjectFactory<?> factory && !requiredType.isInstance(autowiringValue)) {
            if (autowiringValue instanceof Serializable && requiredType.isInterface()) {
                autowiringValue = Proxy.newProxyInstance(requiredType.getClassLoader(),
                        new Class<?>[] {requiredType}, new ObjectFactoryDelegatingInvocationHandler(factory));
            }
            else {
                return factory.getObject();
            }
        }
        return autowiringValue;
    }

    
    public static Class<?> resolveReturnTypeForFactoryMethod(
            Method method,  Object[] args,  ClassLoader classLoader) {

        Assert.notNull(method, "Method must not be null");
        Assert.notNull(args, "Argument array must not be null");

        TypeVariable<Method>[] declaredTypeVariables = method.getTypeParameters();
        Type genericReturnType = method.getGenericReturnType();
        Type[] methodParameterTypes = method.getGenericParameterTypes();
        Assert.isTrue(args.length == methodParameterTypes.length, "Argument array does not match parameter count");

        // Ensure that the type variable (for example, T) is declared directly on the method
        // itself (for example, via <T>), not on the enclosing class or interface.
        boolean locallyDeclaredTypeVariableMatchesReturnType = false;
        for (TypeVariable<Method> currentTypeVariable : declaredTypeVariables) {
            if (currentTypeVariable.equals(genericReturnType)) {
                locallyDeclaredTypeVariableMatchesReturnType = true;
                break;
            }
        }

        if (locallyDeclaredTypeVariableMatchesReturnType) {
            for (int i = 0; i < methodParameterTypes.length; i++) {
                Type methodParameterType = methodParameterTypes[i];
                Object arg = args[i];
                if (methodParameterType.equals(genericReturnType)) {
                    if (arg instanceof TypedStringValue typedValue) {
                        if (typedValue.hasTargetType()) {
                            return typedValue.getTargetType();
                        }
                        try {
                            Class<?> resolvedType = typedValue.resolveTargetType(classLoader);
                            if (resolvedType != null) {
                                return resolvedType;
                            }
                        }
                        catch (ClassNotFoundException ex) {
                            throw new IllegalStateException("Failed to resolve value type [" +
                                    typedValue.getTargetTypeName() + "] for factory method argument", ex);
                        }
                    }
                    else if (arg != null && !(arg instanceof BeanMetadataElement)) {
                        // Only consider argument type if it is a simple value...
                        return arg.getClass();
                    }
                    return method.getReturnType();
                }
                else if (methodParameterType instanceof ParameterizedType parameterizedType) {
                    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                    for (Type typeArg : actualTypeArguments) {
                        if (typeArg.equals(genericReturnType)) {
                            if (arg instanceof Class<?> clazz) {
                                return clazz;
                            }
                            else {
                                String className = null;
                                if (arg instanceof String name) {
                                    className = name;
                                }
                                else if (arg instanceof TypedStringValue typedValue) {
                                    String targetTypeName = typedValue.getTargetTypeName();
                                    if (targetTypeName == null || Class.class.getName().equals(targetTypeName)) {
                                        className = typedValue.getValue();
                                    }
                                }
                                if (className != null) {
                                    try {
                                        return ClassUtils.forName(className, classLoader);
                                    }
                                    catch (ClassNotFoundException ex) {
                                        throw new IllegalStateException("Could not resolve class name [" + arg +
                                                "] for factory method argument", ex);
                                    }
                                }
                                // Consider adding logic to determine the class of the typeArg, if possible.
                                // For now, just fall back...
                                return method.getReturnType();
                            }
                        }
                    }
                }
            }
        }

        // Fall back...
        return method.getReturnType();
    }

    public static boolean isAutowireCandidate(ConfigurableBeanFactory beanFactory, String beanName) {
        try {
            return beanFactory.getMergedBeanDefinition(beanName).isAutowireCandidate();
        }
        catch (NoSuchBeanDefinitionException ex) {
            // A manually registered singleton instance not backed by a BeanDefinition.
            return true;
        }
    }

    
    public static boolean isDefaultCandidate(ConfigurableBeanFactory beanFactory, String beanName) {
        try {
            BeanDefinition mbd = beanFactory.getMergedBeanDefinition(beanName);
            return (!(mbd instanceof AbstractBeanDefinition abd) || abd.isDefaultCandidate());
        }
        catch (NoSuchBeanDefinitionException ex) {
            // A manually registered singleton instance not backed by a BeanDefinition.
            return true;
        }
    }


    
    @SuppressWarnings("serial")
    private static class ObjectFactoryDelegatingInvocationHandler implements InvocationHandler, Serializable {

        private final ObjectFactory<?> objectFactory;

        ObjectFactoryDelegatingInvocationHandler(ObjectFactory<?> objectFactory) {
            this.objectFactory = objectFactory;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "equals" -> (proxy == args[0]); // Only consider equal when proxies are identical.
                case "hashCode" -> System.identityHashCode(proxy); // Use hashCode of proxy.
                case "toString" -> this.objectFactory.toString();
                default -> {
                    try {
                        yield method.invoke(this.objectFactory.getObject(), args);
                    }
                    catch (InvocationTargetException ex) {
                        throw ex.getTargetException();
                    }
                }
            };
        }
    }
}
