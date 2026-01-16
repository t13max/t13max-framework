package com.t13max.ioc.aop.framework;

/**
 * @author t13max
 * @since 16:49 2026/1/16
 */
public interface AopProxy {

    Object getProxy();

    /**
     * Create a new proxy object.
     * <p>Uses the given class loader (if necessary for proxy creation).
     * {@code null} will simply be passed down and thus lead to the low-level
     * proxy facility's default, which is usually different from the default chosen
     * by the AopProxy implementation's {@link #getProxy()} method.
     * @param classLoader the class loader to create the proxy with
     * (or {@code null} for the low-level proxy facility's default)
     * @return the new proxy object (never {@code null})
     */
    Object getProxy(@Nullable ClassLoader classLoader);

    /**
     * Determine the proxy class.
     * @param classLoader the class loader to create the proxy class with
     * (or {@code null} for the low-level proxy facility's default)
     * @return the proxy class
     * @since 6.0
     */
    Class<?> getProxyClass(@Nullable ClassLoader classLoader);
}
