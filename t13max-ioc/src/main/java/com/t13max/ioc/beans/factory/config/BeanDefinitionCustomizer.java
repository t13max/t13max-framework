package com.t13max.ioc.beans.factory.config;

/**
 * @Author: t13max
 * @Since: 20:53 2026/1/16
 */
@FunctionalInterface
public interface BeanDefinitionCustomizer {

    void customize(BeanDefinition bd);
}
