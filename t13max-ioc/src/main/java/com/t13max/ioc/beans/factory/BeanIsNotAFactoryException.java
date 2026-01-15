package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 23:19 2026/1/15
 */
public class BeanIsNotAFactoryException extends BeanNotOfRequiredTypeException {

    public BeanIsNotAFactoryException(String name, Class<?> actualType) {
        super(name, FactoryBean.class, actualType);
    }
}
