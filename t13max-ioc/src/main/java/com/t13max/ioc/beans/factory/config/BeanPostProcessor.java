package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeansException;

/**
 * @Author: t13max
 * @Since: 8:44 2026/1/16
 */
public interface BeanPostProcessor {

    default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}
