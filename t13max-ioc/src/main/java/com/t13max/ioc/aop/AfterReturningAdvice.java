package com.t13max.ioc.aop;

import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 14:36 2026/1/16
 */
public interface AfterReturningAdvice extends AfterAdvice {

    //目标方法执行后, AOP会回调此方法
    void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable;
}
