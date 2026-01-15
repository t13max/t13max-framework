package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 23:18 2026/1/15
 */
public interface FactoryBean<T> {

    String OBJECT_TYPE_ATTRIBUTE = "factoryBeanObjectType";

    T getObject() throws Exception;

    Class<?> getObjectType();

    default boolean isSingleton() {
        return true;
    }
}
