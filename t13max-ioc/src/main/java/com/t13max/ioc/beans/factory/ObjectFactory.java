package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.BeansException;

/**
 * @Author: t13max
 * @Since: 22:04 2026/1/15
 */
public interface ObjectFactory<T> {

    T getObject() throws BeansException;
}
