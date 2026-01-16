package com.t13max.ioc.beans.factory;

/**
 * @author t13max
 * @since 18:03 2026/1/16
 */
public interface BeanClassLoaderAware extends Aware {

    /**
     * Callback that supplies the bean {@link ClassLoader class loader} to
     * a bean instance.
     * <p>Invoked <i>after</i> the population of normal bean properties but
     * <i>before</i> an initialization callback such as
     * {@link InitializingBean InitializingBean's}
     * {@link InitializingBean#afterPropertiesSet()}
     * method or a custom init-method.
     * @param classLoader the owning class loader
     */
    void setBeanClassLoader(ClassLoader classLoader);
}
