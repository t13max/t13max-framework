package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 9:04 2026/1/16
 */
public class BeanIsAbstractException extends BeanCreationException {

    public BeanIsAbstractException(String beanName) {
        super(beanName, "Bean definition is abstract");
    }
}
