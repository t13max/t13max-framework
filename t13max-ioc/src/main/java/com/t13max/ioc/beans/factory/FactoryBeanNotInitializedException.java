package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.FatalBeanException;

/**
 * @Author: t13max
 * @Since: 8:36 2026/1/16
 */
public class FactoryBeanNotInitializedException extends FatalBeanException {

    public FactoryBeanNotInitializedException() {
        super("FactoryBean is not fully initialized yet");
    }

    public FactoryBeanNotInitializedException(String msg) {
        super(msg);
    }
}
