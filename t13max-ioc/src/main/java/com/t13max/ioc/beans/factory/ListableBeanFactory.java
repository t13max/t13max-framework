package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.core.ResolvableType;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

/**
 * @Author: t13max
 * @Since: 22:15 2026/1/14
 */
public interface ListableBeanFactory extends BeanFactory {

    boolean containsBeanDefinition(String beanName);

    int getBeanDefinitionCount();

    String[] getBeanDefinitionNames();

    <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit);

    <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit);

    String[] getBeanNamesForType(ResolvableType type);

    String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit);

    String[] getBeanNamesForType(Class<?> type);

    String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit);

    <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException;

    <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
            throws BeansException;

    String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType);

    Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException;

    <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
            throws NoSuchBeanDefinitionException;

    <A extends Annotation> A findAnnotationOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException;

    <A extends Annotation> Set<A> findAllAnnotationsOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException;
}
