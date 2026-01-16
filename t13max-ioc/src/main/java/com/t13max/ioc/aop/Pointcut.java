package com.t13max.ioc.aop;

/**
 * @author t13max
 * @since 14:47 2026/1/16
 */
public interface Pointcut {

    ClassFilter getClassFilter();

    MethodMatcher getMethodMatcher();

    Pointcut TRUE = TruePointcut.INSTANCE;
}
