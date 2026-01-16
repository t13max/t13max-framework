package com.t13max.ioc.aop;

import com.t13max.ioc.core.NestedRuntimeException;

/**
 * @Author: t13max
 * @Since: 22:20 2026/1/16
 */
public class AopInvocationException extends NestedRuntimeException {

    public AopInvocationException(String msg) {
        super(msg);
    }

    public AopInvocationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
