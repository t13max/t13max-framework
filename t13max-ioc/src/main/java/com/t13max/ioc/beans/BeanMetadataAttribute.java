package com.t13max.ioc.beans;

import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ObjectUtils;

/**
 * @Author: t13max
 * @Since: 22:32 2026/1/16
 */
public class BeanMetadataAttribute implements BeanMetadataElement {

    private final String name;

    private final  Object value;

    private  Object source;

    
    public BeanMetadataAttribute(String name,  Object value) {
        Assert.notNull(name, "Name must not be null");
        this.name = name;
        this.value = value;
    }

    
    public String getName() {
        return this.name;
    }
    
    public  Object getValue() {
        return this.value;
    }
    
    public void setSource( Object source) {
        this.source = source;
    }

    @Override
    public  Object getSource() {
        return this.source;
    }


    @Override
    public boolean equals( Object other) {
        return (this == other ||(other instanceof BeanMetadataAttribute that &&
                this.name.equals(that.name) &&
                ObjectUtils.nullSafeEquals(this.value, that.value) &&
                ObjectUtils.nullSafeEquals(this.source, that.source)));
    }

    @Override
    public int hashCode() {
        return ObjectUtils.nullSafeHash(this.name, this.value);
    }

    @Override
    public String toString() {
        return "metadata attribute: name='" + this.name + "'; value=" + this.value;
    }

}
