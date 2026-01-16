package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.BeanDefinitionStoreException;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.core.AliasRegistry;

/**
 * @Author: t13max
 * @Since: 20:49 2026/1/16
 */
public interface BeanDefinitionRegistry extends AliasRegistry {

    void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException;

    void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

    BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

    boolean containsBeanDefinition(String beanName);

    String[] getBeanDefinitionNames();

    int getBeanDefinitionCount();

    default boolean isBeanDefinitionOverridable(String beanName) {
        return true;
    }

    boolean isBeanNameInUse(String beanName);
}
