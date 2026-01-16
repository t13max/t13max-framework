package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.config.ConfigurableBeanFactory;
import com.t13max.ioc.utils.ReflectionUtils;
import com.t13max.ioc.utils.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 13:32 2026/1/16
 */
public class SimpleInstantiationStrategy implements InstantiationStrategy {

    private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<>();

    public static Method getCurrentlyInvokedFactoryMethod() {
        return currentlyInvokedFactoryMethod.get();
    }

    public static void setCurrentlyInvokedFactoryMethod(@Nullable Method method) {
        if (method != null) {
            currentlyInvokedFactoryMethod.set(method);
        }
        else {
            currentlyInvokedFactoryMethod.remove();
        }
    }

    //使用初始化策略实例化bean对象
    @Override
    public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
        // 如果配置的bean中没有方法覆盖,则使用Java的反射机制实例化对象,
        if (!bd.hasMethodOverrides()) {
            Constructor<?> constructorToUse;
            synchronized (bd.constructorArgumentLock) {
                // 获取对象的构造方法对bean进行实例化
                constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
                //如果前面没有获取到构造方法, 则通过反射获取
                if (constructorToUse == null) {
                    // 使用JDK的反射机制, 判断要实例化的bean是否是接口
                    Class<?> clazz = bd.getBeanClass();
                    //如果clazz是一个接口, 直接抛出异常
                    if (clazz.isInterface()) {
                        throw new BeanInstantiationException(clazz, "Specified class is an interface");
                    }
                    try {
                        constructorToUse = clazz.getDeclaredConstructor();
                        bd.resolvedConstructorOrFactoryMethod = constructorToUse;
                    }
                    catch (Throwable ex) {
                        throw new BeanInstantiationException(clazz, "No default constructor found", ex);
                    }
                }
            }
            //BeanUtils使用构造方法实例化对象
            return BeanUtils.instantiateClass(constructorToUse);
        }
        else {
            // 否则使用CGLIB
            return instantiateWithMethodInjection(bd, beanName, owner);
        }
    }

    protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
        throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
    }

    @Override
    public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, Constructor<?> ctor, Object... args) {

        if (!bd.hasMethodOverrides()) {
            return BeanUtils.instantiateClass(ctor, args);
        }
        else {
            return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
        }
    }

    protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, @Nullable Constructor<?> ctor, Object... args) {
        throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
    }

    @Override
    public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, @Nullable Object factoryBean, Method factoryMethod, Object... args) {

        try {
            ReflectionUtils.makeAccessible(factoryMethod);

            Method priorInvokedFactoryMethod = getCurrentlyInvokedFactoryMethod();
            try {
                setCurrentlyInvokedFactoryMethod(factoryMethod);
                Object result = factoryMethod.invoke(factoryBean, args);
                if (result == null) {
                    result = new NullBean();
                }
                return result;
            }
            finally {
                setCurrentlyInvokedFactoryMethod(priorInvokedFactoryMethod);
            }
        }
        catch (IllegalArgumentException ex) {
            if (factoryBean != null && !factoryMethod.getDeclaringClass().isAssignableFrom(factoryBean.getClass())) {
                throw new BeanInstantiationException(factoryMethod, "Illegal factory instance for factory method '" + factoryMethod.getName() + "'; " + "instance: " + factoryBean.getClass().getName(), ex);
            }
            throw new BeanInstantiationException(factoryMethod, "Illegal arguments to factory method '" + factoryMethod.getName() + "'; " + "args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
        }
        catch (IllegalAccessException ex) {
            throw new BeanInstantiationException(factoryMethod, "Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
        }
        catch (InvocationTargetException ex) {
            String msg = "Factory method '" + factoryMethod.getName() + "' threw exception with message: " + ex.getTargetException().getMessage();
            if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory cbf &&
                    cbf.isCurrentlyInCreation(bd.getFactoryBeanName())) {
                msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " + "declaring the factory method as static for independence from its containing instance. " + msg;
            }
            throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
        }
    }

}
