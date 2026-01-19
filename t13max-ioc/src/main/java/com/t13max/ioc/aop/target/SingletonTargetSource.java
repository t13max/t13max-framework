package com.t13max.ioc.aop.target;

import com.t13max.ioc.aop.TargetSource;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ObjectUtils;

import java.io.Serializable;

/**
 * @Author: t13max
 * @Since: 22:01 2026/1/16
 */
public class SingletonTargetSource implements TargetSource, Serializable {    
    private static final long serialVersionUID = 9031246629662423738L;
    
    @SuppressWarnings("serial")
    private final Object target;
    
    public SingletonTargetSource(Object target) {
        Assert.notNull(target, "Target object must not be null");
        this.target = target;
    }


    @Override
    public Class<?> getTargetClass() {
        return this.target.getClass();
    }

    @Override
    public Object getTarget() {
        return this.target;
    }

    @Override
    public boolean isStatic() {
        return true;
    }
    
    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof SingletonTargetSource that &&
                this.target.equals(that.target)));
    }    
    @Override
    public int hashCode() {
        return this.target.hashCode();
    }

    @Override
    public String toString() {
        return "SingletonTargetSource for target object [" + ObjectUtils.identityToString(this.target) + "]";
    }
}
