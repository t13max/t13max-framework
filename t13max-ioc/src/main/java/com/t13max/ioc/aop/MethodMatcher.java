package com.t13max.ioc.aop;

import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 16:20 2026/1/16
 */
public interface MethodMatcher {

    boolean matches(Method method, Class<?> targetClass);

    boolean isRuntime();

    boolean matches(Method method, Class<?> targetClass, Object... args);

    MethodMatcher TRUE = TrueMethodMatcher.INSTANCE;
}
