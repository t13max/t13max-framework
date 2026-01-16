package com.t13max.ioc.aop.intecept;

import java.lang.reflect.AccessibleObject;

/**
 * @author t13max
 * @since 17:06 2026/1/16
 */
public interface Joinpoint {

    Object proceed() throws Throwable;

    /**
     * Return the object that holds the current joinpoint's static part.
     * <p>For instance, the target object for an invocation.
     * @return the object (can be null if the accessible object is static)
     */
    @Nullable
    Object getThis();

    /**
     * Return the static part of this joinpoint.
     * <p>The static part is an accessible object on which a chain of
     * interceptors is installed.
     */
    @Nonnull
    AccessibleObject getStaticPart();
}
