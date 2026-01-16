package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 13:33 2026/1/16
 */
public interface InstantiationStrategy {

    Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner)
            throws BeansException;

    /**
     * Return an instance of the bean with the given name in this factory,
     * creating it via the given constructor.
     * @param bd the bean definition
     * @param beanName the name of the bean when it is created in this context.
     * The name can be {@code null} if we are autowiring a bean which doesn't
     * belong to the factory.
     * @param owner the owning BeanFactory
     * @param ctor the constructor to use
     * @param args the constructor arguments to apply
     * @return a bean instance for this bean definition
     * @throws BeansException if the instantiation attempt failed
     */
    Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
                       Constructor<?> ctor, Object... args) throws BeansException;

    /**
     * Return an instance of the bean with the given name in this factory,
     * creating it via the given factory method.
     * @param bd the bean definition
     * @param beanName the name of the bean when it is created in this context.
     * The name can be {@code null} if we are autowiring a bean which doesn't
     * belong to the factory.
     * @param owner the owning BeanFactory
     * @param factoryBean the factory bean instance to call the factory method on,
     * or {@code null} in case of a static factory method
     * @param factoryMethod the factory method to use
     * @param args the factory method arguments to apply
     * @return a bean instance for this bean definition
     * @throws BeansException if the instantiation attempt failed
     */
    Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
                       @Nullable Object factoryBean, Method factoryMethod, Object... args)
            throws BeansException;

    /**
     * Determine the actual class for the given bean definition, as instantiated at runtime.
     * @since 6.0
     */
    default Class<?> getActualBeanClass(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
        return bd.getBeanClass();
    }
}
