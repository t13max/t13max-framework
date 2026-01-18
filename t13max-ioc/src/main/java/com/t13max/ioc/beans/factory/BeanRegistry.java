package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.support.AbstractBeanDefinition;
import com.t13max.ioc.beans.factory.support.RootBeanDefinition;
import com.t13max.ioc.core.ParameterizedTypeReference;
import com.t13max.ioc.core.ResolvableType;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 20:57 2026/1/16
 */
public interface BeanRegistry {
    void register(BeanRegistrar registrar);
    void registerAlias(String name, String alias);
    <T> String registerBean(Class<T> beanClass);
    <T> String registerBean(Class<T> beanClass, Consumer<Spec<T>> customizer);
    <T> void registerBean(String name, Class<T> beanClass);
    <T> void registerBean(String name, Class<T> beanClass, Consumer<Spec<T>> customizer);

    interface Spec<T> {

        
        Spec<T> backgroundInit();

        
        Spec<T> description(String description);

        
        Spec<T> fallback();

        
        Spec<T> infrastructure();

        
        Spec<T> lazyInit();

        
        Spec<T> notAutowirable();

        
        Spec<T> order(int order);

        
        Spec<T> primary();

        
        Spec<T> prototype();

        
        Spec<T> supplier(Function<SupplierContext, T> supplier);

        
        Spec<T> targetType(ParameterizedTypeReference<? extends T> type);

        
        Spec<T> targetType(ResolvableType type);
    }

    interface SupplierContext {

        
        <T> T bean(Class<T> requiredType) throws BeansException;

        
        <T> T bean(String name, Class<T> requiredType) throws BeansException;

        
        <T> ObjectProvider<T> beanProvider(Class<T> requiredType);

        
        <T> ObjectProvider<T> beanProvider(ResolvableType requiredType);
    }
}
