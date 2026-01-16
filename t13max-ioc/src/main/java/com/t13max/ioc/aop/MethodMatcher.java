package com.t13max.ioc.aop;

import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 16:20 2026/1/16
 */
public interface MethodMatcher {

    boolean matches(Method method, Class<?> targetClass);

    /**
     * Is this {@code MethodMatcher} dynamic, that is, must a final check be made
     * via the {@link #matches(Method, Class, Object[])} method at runtime even
     * if {@link #matches(Method, Class)} returns {@code true}?
     * <p>Can be invoked when an AOP proxy is created, and need not be invoked
     * again before each method invocation.
     * @return whether a runtime match via {@link #matches(Method, Class, Object[])}
     * is required if static matching passed
     */
    boolean isRuntime();

    /**
     * Check whether there is a runtime (dynamic) match for this method, which
     * must have matched statically.
     * <p>This method is invoked only if {@link #matches(Method, Class)} returns
     * {@code true} for the given method and target class, and if
     * {@link #isRuntime()} returns {@code true}.
     * <p>Invoked immediately before potential running of the advice, after any
     * advice earlier in the advice chain has run.
     * @param method the candidate method
     * @param targetClass the target class
     * @param args arguments to the method
     * @return whether there's a runtime match
     * @see #matches(Method, Class)
     */
    boolean matches(Method method, Class<?> targetClass, Object... args);


    /**
     * Canonical instance of a {@code MethodMatcher} that matches all methods.
     */
    MethodMatcher TRUE = TrueMethodMatcher.INSTANCE;
}
