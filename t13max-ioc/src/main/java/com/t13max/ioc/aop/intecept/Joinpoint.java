package com.t13max.ioc.aop.intecept;

import java.lang.reflect.AccessibleObject;

/**
 * @author t13max
 * @since 17:06 2026/1/16
 */
public interface Joinpoint {

    Object proceed() throws Throwable;    
    Object getThis();
    @Nonnull
    AccessibleObject getStaticPart();
}
