package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeansException;

/**
 * @Author: t13max
 * @Since: 21:40 2026/1/16
 */
public interface DestructionAwareBeanPostProcessor extends BeanPostProcessor {

    void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException;

    default boolean requiresDestruction(Object bean) {
        return true;
    }
}
