package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.AfterReturningAdvice;
import com.t13max.ioc.aop.intecept.MethodInterceptor;

import java.io.Serializable;

/**
 * @Author: t13max
 * @Since: 22:02 2026/1/16
 */
public class AfterReturningAdviceAdapter implements AdvisorAdapter, Serializable {

    @Override
    public boolean supportsAdvice(Advice advice) {
        return (advice instanceof AfterReturningAdvice);
    }

    @Override
    public MethodInterceptor getInterceptor(Advisor advisor) {
        AfterReturningAdvice advice = (AfterReturningAdvice) advisor.getAdvice();
        return new AfterReturningAdviceInterceptor(advice);
    }
}
