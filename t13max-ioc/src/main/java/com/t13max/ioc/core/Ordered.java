package com.t13max.ioc.core;

/**
 * @Author: t13max
 * @Since: 22:05 2026/1/16
 */
public interface Ordered {

    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    int getOrder();
}
