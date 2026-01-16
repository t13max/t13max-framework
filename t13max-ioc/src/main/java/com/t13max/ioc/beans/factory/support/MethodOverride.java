package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeanMetadataElement;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ObjectUtils;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @Author: t13max
 * @Since: 21:07 2026/1/16
 */
public abstract class MethodOverride implements BeanMetadataElement {

    private final String methodName;

    private boolean overloaded = true;

    private Object source;    
    protected MethodOverride(String methodName) {
        Assert.notNull(methodName, "Method name must not be null");
        this.methodName = methodName;
    }

    public String getMethodName() {
        return this.methodName;
    }
    protected void setOverloaded(boolean overloaded) {
        this.overloaded = overloaded;
    }
    protected boolean isOverloaded() {
        return this.overloaded;
    }
    public void setSource( Object source) {
        this.source = source;
    }

    @Override
    public  Object getSource() {
        return this.source;
    }
    public abstract boolean matches(Method method);


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof MethodOverride that &&
                this.methodName.equals(that.methodName) &&
                ObjectUtils.nullSafeEquals(this.source, that.source)));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.methodName, this.source);
    }
}
