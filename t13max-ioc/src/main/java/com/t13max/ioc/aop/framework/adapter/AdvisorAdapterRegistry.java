package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.intecept.MethodInterceptor;

/**
 * @author t13max
 * @since 17:48 2026/1/16
 */
public interface AdvisorAdapterRegistry {

    Advisor wrap(Object advice) throws UnknownAdviceTypeException;

    MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException;

    void registerAdvisorAdapter(AdvisorAdapter adapter);
}
