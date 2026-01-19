package com.t13max.ioc.context.annotation;

/**
 * @Author: t13max
 * @Since: 8:00 2026/1/17
 */
public class ConflictingBeanDefinitionException extends IllegalStateException {

    public ConflictingBeanDefinitionException(String message) {
        super(message);
    }
}
