package com.t13max.ioc.aop.support;

import com.t13max.ioc.aop.MethodMatcher;

import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 16:20 2026/1/16
 */
public class StaticMethodMatcher implements MethodMatcher {

    @Override
    public final boolean isRuntime() {
        return false;
    }

    @Override
    public final boolean matches(Method method, Class<?> targetClass, Object... args) {
        // should never be invoked because isRuntime() returns false
        throw new UnsupportedOperationException("Illegal MethodMatcher usage");
    }
}
