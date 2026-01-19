package com.t13max.ioc.aop;

/**
 * @Author: t13max
 * @Since: 22:09 2026/1/16
 */
@FunctionalInterface
public interface ClassFilter {

    boolean matches(Class<?> clazz);

    ClassFilter TRUE = TrueClassFilter.INSTANCE;
}
