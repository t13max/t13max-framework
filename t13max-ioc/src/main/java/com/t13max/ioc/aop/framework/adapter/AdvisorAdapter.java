package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.intecept.MethodInterceptor;

/**
 * @author t13max
 * @since 17:53 2026/1/16
 */
public interface AdvisorAdapter {    boolean supportsAdvice(Advice advice);

    MethodInterceptor getInterceptor(Advisor advisor);
}
