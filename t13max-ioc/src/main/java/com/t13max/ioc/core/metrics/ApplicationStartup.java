package com.t13max.ioc.core.metrics;

/**
 * @Author: t13max
 * @Since: 20:54 2026/1/15
 */
public interface ApplicationStartup {

    ApplicationStartup DEFAULT = new DefaultApplicationStartup();

    StartupStep start(String name);
}
