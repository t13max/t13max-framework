package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.MethodMatcher;
import com.t13max.ioc.aop.intecept.MethodInterceptor;

/**
 * @Author: t13max
 * @Since: 22:17 2026/1/16
 */
record InterceptorAndDynamicMethodMatcher(MethodInterceptor interceptor, MethodMatcher matcher) {
}
