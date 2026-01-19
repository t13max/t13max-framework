package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.AfterAdvice;
import com.t13max.ioc.aop.AfterReturningAdvice;
import com.t13max.ioc.aop.intecept.MethodInterceptor;
import com.t13max.ioc.aop.intecept.MethodInvocation;
import com.t13max.ioc.util.Assert;

import java.io.Serializable;

/**
 * @Author: t13max
 * @Since: 22:02 2026/1/16
 */
public class AfterReturningAdviceInterceptor implements MethodInterceptor, AfterAdvice, Serializable {

    private final AfterReturningAdvice advice;

    public AfterReturningAdviceInterceptor(AfterReturningAdvice advice) {
        Assert.notNull(advice, "Advice must not be null");
        this.advice = advice;
    }

    @Override
    public Object invoke(MethodInvocation mi) throws Throwable {
        Object retVal = mi.proceed();
        this.advice.afterReturning(retVal, mi.getMethod(), mi.getArguments(), mi.getThis());
        return retVal;
    }

}
