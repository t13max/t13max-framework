package com.t13max.ioc.context.annotation;

/**
 * @Author: t13max
 * @Since: 7:59 2026/1/17
 */
public enum ScopedProxyMode {

    DEFAULT,

    /**
     * Do not create a scoped proxy.
     * <p>This proxy-mode is not typically useful when used with a
     * non-singleton scoped instance, which should favor the use of the
     * {@link #INTERFACES} or {@link #TARGET_CLASS} proxy-modes instead if it
     * is to be used as a dependency.
     */
    NO,

    /**
     * Create a JDK dynamic proxy implementing <i>all</i> interfaces exposed by
     * the class of the target object.
     */
    INTERFACES,

    /**
     * Create a class-based proxy (uses CGLIB).
     */
    TARGET_CLASS
}
