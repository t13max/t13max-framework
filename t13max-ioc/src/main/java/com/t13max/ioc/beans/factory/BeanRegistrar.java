package com.t13max.ioc.beans.factory;

import com.t13max.ioc.core.env.Environment;

/**
 * @Author: t13max
 * @Since: 20:56 2026/1/16
 */
@FunctionalInterface
public interface BeanRegistrar {

    void register(BeanRegistry registry, Environment env);
}
