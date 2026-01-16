package com.t13max.ioc.beans;

import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ObjectUtils;

import java.io.Serializable;

/**
 * @Author: t13max
 * @Since: 20:55 2026/1/16
 */
public class PropertyValue extends BeanMetadataAttributeAccessor implements Serializable {

    private final String name;

    private final  Object value;

    private boolean optional = false;

    private boolean converted = false;

    private  Object convertedValue;
    volatile  Boolean conversionNecessary;
    transient volatile  Object resolvedTokens;

    public PropertyValue(String name,  Object value) {
        Assert.notNull(name, "Name must not be null");
        this.name = name;
        this.value = value;
    }
    public PropertyValue(PropertyValue original) {
        Assert.notNull(original, "Original must not be null");
        this.name = original.getName();
        this.value = original.getValue();
        this.optional = original.isOptional();
        this.converted = original.converted;
        this.convertedValue = original.convertedValue;
        this.conversionNecessary = original.conversionNecessary;
        this.resolvedTokens = original.resolvedTokens;
        setSource(original.getSource());
        copyAttributesFrom(original);
    }
    public PropertyValue(PropertyValue original,  Object newValue) {
        Assert.notNull(original, "Original must not be null");
        this.name = original.getName();
        this.value = newValue;
        this.optional = original.isOptional();
        this.conversionNecessary = original.conversionNecessary;
        this.resolvedTokens = original.resolvedTokens;
        setSource(original);
        copyAttributesFrom(original);
    }

    public String getName() {
        return this.name;
    }
    public  Object getValue() {
        return this.value;
    }
    public PropertyValue getOriginalPropertyValue() {
        PropertyValue original = this;
        Object source = getSource();
        while (source instanceof PropertyValue pv && source != original) {
            original = pv;
            source = original.getSource();
        }
        return original;
    }
    public void setOptional(boolean optional) {
        this.optional = optional;
    }
    public boolean isOptional() {
        return this.optional;
    }
    public synchronized boolean isConverted() {
        return this.converted;
    }
    public synchronized void setConvertedValue( Object value) {
        this.converted = true;
        this.convertedValue = value;
    }
    public synchronized  Object getConvertedValue() {
        return this.convertedValue;
    }


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof PropertyValue that &&
                this.name.equals(that.name) &&
                ObjectUtils.nullSafeEquals(this.value, that.value) &&
                ObjectUtils.nullSafeEquals(getSource(), that.getSource())));
    }

    @Override
    public int hashCode() {
        return ObjectUtils.nullSafeHash(this.name, this.value);
    }

    @Override
    public String toString() {
        return "bean property '" + this.name + "'";
    }
}
