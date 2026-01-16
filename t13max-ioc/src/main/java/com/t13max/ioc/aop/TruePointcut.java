package com.t13max.ioc.aop;

import java.io.Serializable;

/**
 * @Author: t13max
 * @Since: 22:27 2026/1/16
 */
public class TruePointcut  implements Pointcut, Serializable {

    public static final TruePointcut INSTANCE = new TruePointcut();

    private TruePointcut() {

    }

    @Override
    public ClassFilter getClassFilter() {
        return ClassFilter.TRUE;
    }

    @Override
    public MethodMatcher getMethodMatcher() {
        return MethodMatcher.TRUE;
    }

    private Object readResolve() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return "Pointcut.TRUE";
    }
}
