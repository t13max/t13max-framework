package com.t13max.ioc.beans.factory;

/**
 * @author t13max
 * @since 18:03 2026/1/16
 */
public interface BeanClassLoaderAware extends Aware {
    void setBeanClassLoader(ClassLoader classLoader);
}
