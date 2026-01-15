package com.t13max.ioc.core.env;

/**
 * @Author: t13max
 * @Since: 21:51 2026/1/15
 */
public interface ConfigurableEnvironment extends Environment{

    void merge(ConfigurableEnvironment parent);
}
