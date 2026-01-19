package com.t13max.ioc.aop;

import java.io.Serializable;

/**
 * @Author: t13max
 * @Since: 22:10 2026/1/16
 */
@SuppressWarnings("serial")
public class TrueClassFilter implements ClassFilter, Serializable {

    public static final TrueClassFilter INSTANCE = new TrueClassFilter();

    private TrueClassFilter() {
    }

    @Override
    public boolean matches(Class<?> clazz) {
        return true;
    }

    private Object readResolve() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return "ClassFilter.TRUE";
    }
}
