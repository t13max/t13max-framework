package com.t13max.ioc.aop.framework;

/**
 * @author t13max
 * @since 16:49 2026/1/16
 */
public interface AopProxy {

    Object getProxy();
    Object getProxy( ClassLoader classLoader);
    Class<?> getProxyClass( ClassLoader classLoader);
}
