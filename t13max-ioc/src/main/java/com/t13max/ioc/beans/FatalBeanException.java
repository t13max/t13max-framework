package com.t13max.ioc.beans;

/**
 * @Author: t13max
 * @Since: 23:26 2026/1/15
 */
public class FatalBeanException extends BeansException {

    public FatalBeanException(String msg) {
        super(msg);
    }

    public FatalBeanException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
