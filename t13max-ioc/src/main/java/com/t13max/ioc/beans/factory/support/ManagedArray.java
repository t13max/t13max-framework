package com.t13max.ioc.beans.factory.support;


import com.t13max.ioc.util.Assert;

/**
 * @Author: t13max
 * @Since: 0:01 2026/1/17
 */
public class ManagedArray extends ManagedList<Object> {

    volatile  Class<?> resolvedElementType;

    public ManagedArray(String elementTypeName, int size) {
        super(size);
        Assert.notNull(elementTypeName, "elementTypeName must not be null");
        setElementTypeName(elementTypeName);
    }
}
