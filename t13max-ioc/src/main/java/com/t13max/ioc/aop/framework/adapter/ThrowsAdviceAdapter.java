package com.t13max.ioc.aop.framework.adapter;


import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.ThrowsAdvice;
import com.t13max.ioc.aop.intecept.MethodInterceptor;

import java.io.Serializable;

@SuppressWarnings("serial")
class ThrowsAdviceAdapter implements AdvisorAdapter, Serializable {

	@Override
	public boolean supportsAdvice(Advice advice) {
		return (advice instanceof ThrowsAdvice);
	}

	@Override
	public MethodInterceptor getInterceptor(Advisor advisor) {
		return new ThrowsAdviceInterceptor(advisor.getAdvice());
	}

}
