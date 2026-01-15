package com.t13max.ioc.core;

import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.StringUtils;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * @Author: t13max
 * @Since: 22:49 2026/1/15
 */
public abstract class AttributeAccessorSupport implements AttributeAccessor, Serializable {

    private final Map<String, Object> attributes = new LinkedHashMap<>();    
    @Override
    public void setAttribute(String name, Object value) {
        Assert.notNull(name, "Name must not be null");
        if (value != null) {
            this.attributes.put(name, value);
        }
        else {
            removeAttribute(name);
        }
    }

    @Override
    public Object getAttribute(String name) {
        Assert.notNull(name, "Name must not be null");
        return this.attributes.get(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T computeAttribute(String name, Function<String, T> computeFunction) {
        Assert.notNull(name, "Name must not be null");
        Assert.notNull(computeFunction, "Compute function must not be null");
        Object value = this.attributes.computeIfAbsent(name, computeFunction);
        Assert.state(value != null,
                () -> String.format("Compute function must not return null for attribute named '%s'", name));
        return (T) value;
    }

    @Override
    public Object removeAttribute(String name) {
        Assert.notNull(name, "Name must not be null");
        return this.attributes.remove(name);
    }

    @Override
    public boolean hasAttribute(String name) {
        Assert.notNull(name, "Name must not be null");
        return this.attributes.containsKey(name);
    }

    @Override
    public String[] attributeNames() {
        return StringUtils.toStringArray(this.attributes.keySet());
    }

    protected void copyAttributesFrom(AttributeAccessor source) {
        Assert.notNull(source, "Source must not be null");
        String[] attributeNames = source.attributeNames();
        for (String attributeName : attributeNames) {
            setAttribute(attributeName, source.getAttribute(attributeName));
        }
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof AttributeAccessorSupport that &&
                this.attributes.equals(that.attributes)));
    }

    @Override
    public int hashCode() {
        return this.attributes.hashCode();
    }
}
