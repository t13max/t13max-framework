package com.t13max.ioc.context;

/**
 * @Author: t13max
 * @Since: 21:05 2026/1/15
 */
public interface Lifecycle {

    void start();

    void stop();

    boolean isRunning();
}
