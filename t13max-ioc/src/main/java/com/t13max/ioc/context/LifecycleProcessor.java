package com.t13max.ioc.context;

/**
 * @Author: t13max
 * @Since: 21:36 2026/1/15
 */
public interface LifecycleProcessor extends Lifecycle{

    void onRefresh();

    void onClose();
}
