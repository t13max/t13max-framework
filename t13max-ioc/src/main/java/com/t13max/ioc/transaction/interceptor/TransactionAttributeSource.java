package com.t13max.ioc.transaction.interceptor;

import java.lang.reflect.Method;

/**
 * @Author: t13max
 * @Since: 20:33 2026/1/16
 */
public interface TransactionAttributeSource {

    default boolean isCandidateClass(Class<?> targetClass) {
        return true;
    }

    default boolean hasTransactionAttribute(Method method, Class<?> targetClass) {
        return (getTransactionAttribute(method, targetClass) != null);
    }

    TransactionAttribute getTransactionAttribute(Method method, Class<?> targetClass);
}
