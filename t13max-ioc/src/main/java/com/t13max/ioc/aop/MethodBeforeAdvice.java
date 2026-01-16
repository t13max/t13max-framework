package com.t13max.ioc.aop;

import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 14:31 2026/1/16
 */
public interface MethodBeforeAdvice extends BeforeAdvice {

    //目标方法开始执行前, AOP会回调此方法
    void before(Method method, Object[] args, Object target) throws Throwable;
}
