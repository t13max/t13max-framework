package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.BeforeAdvice;
import com.t13max.ioc.aop.MethodBeforeAdvice;
import com.t13max.ioc.aop.intecept.MethodInterceptor;
import com.t13max.ioc.aop.intecept.MethodInvocation;
import com.t13max.ioc.utils.Assert;

import java.io.Serializable;

/**
 * @Author: t13max
 * @Since: 22:07 2026/1/16
 */
public class MethodBeforeAdviceInterceptor implements MethodInterceptor, BeforeAdvice, Serializable {

    private final MethodBeforeAdvice advice;

    public MethodBeforeAdviceInterceptor(MethodBeforeAdvice advice) {
        Assert.notNull(advice, "Advice must not be null");
        this.advice = advice;
    }


    @Override
    public Object invoke(MethodInvocation mi) throws Throwable {
        this.advice.before(mi.getMethod(), mi.getArguments(), mi.getThis());
        return mi.proceed();
    }
}
