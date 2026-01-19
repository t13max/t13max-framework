package com.t13max.ioc.aop.framework;

import com.t13max.ioc.core.NestedRuntimeException;

/**
 * @Author: t13max
 * @Since: 22:00 2026/1/16
 */
public class AopConfigException extends NestedRuntimeException {
	public AopConfigException(String msg) {
        super(msg);
    }
	public AopConfigException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
