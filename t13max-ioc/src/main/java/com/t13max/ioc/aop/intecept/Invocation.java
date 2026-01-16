package com.t13max.ioc.aop.intecept;

/**
 * @author t13max
 * @since 17:06 2026/1/16
 */
public interface Invocation extends Joinpoint{

    Object[] getArguments();
}
