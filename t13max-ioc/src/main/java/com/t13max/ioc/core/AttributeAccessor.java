package com.t13max.ioc.core;

import com.t13max.ioc.util.Assert;

import java.util.function.Function;

/**
 * @Author: t13max
 * @Since: 22:50 2026/1/15
 */
public interface AttributeAccessor {

    void setAttribute(String name, Object value);

    Object getAttribute(String name);

    @SuppressWarnings("unchecked")
    default <T> T computeAttribute(String name, Function<String, T> computeFunction) {
        Assert.notNull(name, "Name must not be null");
        Assert.notNull(computeFunction, "Compute function must not be null");
        Object value = getAttribute(name);
        if (value == null) {
            value = computeFunction.apply(name);
            Assert.state(value != null,
                    () -> String.format("Compute function must not return null for attribute named '%s'", name));
            setAttribute(name, value);
        }
        return (T) value;
    }

    Object removeAttribute(String name);

    boolean hasAttribute(String name);

    String[] attributeNames();
}
