package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.config.BeanPostProcessor;

/**
 * @Author: t13max
 * @Since: 21:40 2026/1/16
 */
public interface MergedBeanDefinitionPostProcessor extends BeanPostProcessor {

    void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName);

    default void resetBeanDefinition(String beanName) {
    }
}
