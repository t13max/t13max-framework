package com.t13max.ioc.aop;

/**
 * @author t13max
 * @since 16:25 2026/1/16
 */
public interface PointcutAdvisor extends Advisor {

    Pointcut getPointcut();
}
