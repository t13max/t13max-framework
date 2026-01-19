package com.t13max.ioc.aop.intecept;

/**
 * @Author: t13max
 * @Since: 20:36 2026/1/16
 */
public interface MethodInterceptor {

    Object invoke(MethodInvocation invocation) throws Throwable;
}
