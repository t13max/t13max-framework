package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.MethodBeforeAdvice;
import com.t13max.ioc.aop.intecept.MethodInterceptor;

import java.io.Serializable;

/**
 * @author t13max
 * @since 17:52 2026/1/16
 */
public class MethodBeforeAdviceAdapter implements AdvisorAdapter, Serializable {

    @Override
    public boolean supportsAdvice(Advice advice) {
        return (advice instanceof MethodBeforeAdvice);
    }

    @Override
    public MethodInterceptor getInterceptor(Advisor advisor) {
        MethodBeforeAdvice advice = (MethodBeforeAdvice) advisor.getAdvice();
        return new MethodBeforeAdviceInterceptor(advice);
    }
}
