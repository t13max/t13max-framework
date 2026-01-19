package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.beans.factory.support.BeanDefinitionRegistry;
import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;

/**
 * @Author: t13max
 * @Since: 8:03 2026/1/17
 */
public interface ConditionContext {

    BeanDefinitionRegistry getRegistry();

    ConfigurableListableBeanFactory getBeanFactory();

    /**
     * Return the {@link Environment} for which the current application is running.
     */
    Environment getEnvironment();

    /**
     * Return the {@link ResourceLoader} currently being used.
     */
    ResourceLoader getResourceLoader();

    ClassLoader getClassLoader();
}
