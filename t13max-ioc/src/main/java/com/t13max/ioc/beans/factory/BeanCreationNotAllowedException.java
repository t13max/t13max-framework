package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 8:32 2026/1/16
 */
public class BeanCreationNotAllowedException extends BeanCreationException {

    public BeanCreationNotAllowedException(String beanName, String msg) {
        super(beanName, msg);
    }
}
