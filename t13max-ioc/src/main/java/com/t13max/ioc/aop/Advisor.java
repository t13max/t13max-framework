package com.t13max.ioc.aop;

/**
 * @author t13max
 * @since 14:49 2026/1/16
 */
public interface Advisor {

    Advice EMPTY_ADVICE = new Advice() {};

    Advice getAdvice();
    default boolean isPerInstance() {
        return true;
    }
}
