package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 23:26 2026/1/15
 */
public interface SmartFactoryBean<T> extends FactoryBean<T> {

    @SuppressWarnings("unchecked")
    default <S> S getObject(Class<S> type) throws Exception {
        Class<?> objectType = getObjectType();
        return (objectType != null && type.isAssignableFrom(objectType) ? (S) getObject() : null);
    }

    default boolean supportsType(Class<?> type) {
        Class<?> objectType = getObjectType();
        return (objectType != null && type.isAssignableFrom(objectType));
    }

    default boolean isPrototype() {
        return false;
    }

    default boolean isEagerInit() {
        return false;
    }

}
