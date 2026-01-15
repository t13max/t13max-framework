package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.ListableBeanFactory;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;

import java.util.Iterator;

/**
 * @Author: t13max
 * @Since: 23:35 2026/1/14
 */
public interface ConfigurableListableBeanFactory extends ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {

    void ignoreDependencyType(Class<?> type);

    void ignoreDependencyInterface(Class<?> ifc);

    void registerResolvableDependency(Class<?> dependencyType, Object autowiredValue);

    boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor) throws NoSuchBeanDefinitionException;

    BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

    Iterator<String> getBeanNamesIterator();

    void clearMetadataCache();

    void freezeConfiguration();

    boolean isConfigurationFrozen();

    void preInstantiateSingletons() throws BeansException;
}
