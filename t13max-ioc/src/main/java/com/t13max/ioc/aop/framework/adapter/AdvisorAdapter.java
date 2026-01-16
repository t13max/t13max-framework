package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Advisor;

/**
 * @author t13max
 * @since 17:53 2026/1/16
 */
public interface AdvisorAdapter {
    
    boolean supportsAdvice(Advice advice);

    /**
     * Return an AOP Alliance MethodInterceptor exposing the behavior of
     * the given advice to an interception-based AOP framework.
     * <p>Don't worry about any Pointcut contained in the Advisor;
     * the AOP framework will take care of checking the pointcut.
     * @param advisor the Advisor. The supportsAdvice() method must have
     * returned true on this object
     * @return an AOP Alliance interceptor for this Advisor. There's
     * no need to cache instances for efficiency, as the AOP framework
     * caches advice chains.
     */
    MethodInterceptor getInterceptor(Advisor advisor);
}
