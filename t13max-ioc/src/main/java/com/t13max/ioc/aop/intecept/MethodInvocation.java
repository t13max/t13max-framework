package com.t13max.ioc.aop.intecept;

import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 17:05 2026/1/16
 */
public interface MethodInvocation extends Invocation{

    Method getMethod();
}
