package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.core.ResolvableType;

/**
 * @Author: t13max
 * @Since: 22:05 2026/1/15
 */
public class NoSuchBeanDefinitionException extends BeansException {

    private final String beanName;

    private final ResolvableType resolvableType;

    public NoSuchBeanDefinitionException(String name) {
        super("No bean named '" + name + "' available");
        this.beanName = name;
        this.resolvableType = null;
    }

    public NoSuchBeanDefinitionException(String name, String message) {
        super("No bean named '" + name + "' available: " + message);
        this.beanName = name;
        this.resolvableType = null;
    }

    public NoSuchBeanDefinitionException(Class<?> type) {
        this(ResolvableType.forClass(type));
    }

    public NoSuchBeanDefinitionException(Class<?> type, String message) {
        this(ResolvableType.forClass(type), message);
    }

    public NoSuchBeanDefinitionException(ResolvableType type) {
        super("No qualifying bean of type '" + type + "' available");
        this.beanName = null;
        this.resolvableType = type;
    }

    public NoSuchBeanDefinitionException(ResolvableType type, String message) {
        super("No qualifying bean of type '" + type + "' available: " + message);
        this.beanName = null;
        this.resolvableType = type;
    }

    public String getBeanName() {
        return this.beanName;
    }

    public Class<?> getBeanType() {
        return (this.resolvableType != null ? this.resolvableType.resolve() : null);
    }

    public ResolvableType getResolvableType() {
        return this.resolvableType;
    }

    public int getNumberOfBeansFound() {
        return 0;
    }
}
