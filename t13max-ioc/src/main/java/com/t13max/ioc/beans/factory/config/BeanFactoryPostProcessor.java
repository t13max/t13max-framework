package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeansException;

/**
 * @Author: t13max
 * @Since: 21:15 2026/1/15
 */
@FunctionalInterface
public interface BeanFactoryPostProcessor {

    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;
}
