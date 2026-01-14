package com.t13max.ioc.beans;

import com.t13max.ioc.core.NestedRuntimeException;

/**
 * @Author: t13max
 * @Since: 23:21 2026/1/14
 */
public class BeansException extends NestedRuntimeException {

    public BeansException(String message) {
        super(message);
    }

    public BeansException(String message, Throwable cause) {
        super(message, cause);
    }
}
