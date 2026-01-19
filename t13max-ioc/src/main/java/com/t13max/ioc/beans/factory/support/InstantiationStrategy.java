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

    Object instantiate(RootBeanDefinition bd,  String beanName, BeanFactory owner)
            throws BeansException;
    Object instantiate(RootBeanDefinition bd,  String beanName, BeanFactory owner,
                       Constructor<?> ctor, Object... args) throws BeansException;
    Object instantiate(RootBeanDefinition bd,  String beanName, BeanFactory owner,
                        Object factoryBean, Method factoryMethod, Object... args)
            throws BeansException;
    default Class<?> getActualBeanClass(RootBeanDefinition bd,  String beanName, BeanFactory owner) {
        return bd.getBeanClass();
    }
}
