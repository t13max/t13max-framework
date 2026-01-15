package com.t13max.ioc.beans.factory.support;

/**
 * @Author: t13max
 * @Since: 23:17 2026/1/15
 */
public class NullBean {

    NullBean() {
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || other == null);
    }

    @Override
    public int hashCode() {
        return NullBean.class.hashCode();
    }

    @Override
    public String toString() {
        return "null";
    }
}
