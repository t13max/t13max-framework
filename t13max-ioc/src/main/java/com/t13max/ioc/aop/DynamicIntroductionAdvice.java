package com.t13max.ioc.aop;

/**
 * @Author: t13max
 * @Since: 22:10 2026/1/16
 */
public interface DynamicIntroductionAdvice extends Advice {

    boolean implementsInterface(Class<?> intf);
}
