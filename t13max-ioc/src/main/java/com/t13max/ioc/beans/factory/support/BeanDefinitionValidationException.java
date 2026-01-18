package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.FatalBeanException;

/**
 * @Author: t13max
 * @Since: 0:00 2026/1/17
 */
public class BeanDefinitionValidationException extends FatalBeanException {

    public BeanDefinitionValidationException(String msg) {
        super(msg);
    }

    public BeanDefinitionValidationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
