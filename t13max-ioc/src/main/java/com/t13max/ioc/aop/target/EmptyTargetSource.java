package com.t13max.ioc.aop.target;

import com.t13max.ioc.aop.TargetSource;
import com.t13max.ioc.util.ObjectUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * @Author: t13max
 * @Since: 22:07 2026/1/16
 */
public final class EmptyTargetSource implements TargetSource, Serializable {

    private static final long serialVersionUID = 3680494563553489691L;

    //---------------------------------------------------------------------
    // Static factory methods
    //---------------------------------------------------------------------

    public static final EmptyTargetSource INSTANCE = new EmptyTargetSource(null, true);

    public static EmptyTargetSource forClass( Class<?> targetClass) {
        return forClass(targetClass, true);
    }

    public static EmptyTargetSource forClass( Class<?> targetClass, boolean isStatic) {
        return (targetClass == null && isStatic ? INSTANCE : new EmptyTargetSource(targetClass, isStatic));
    }

    //---------------------------------------------------------------------
    // Instance implementation
    //---------------------------------------------------------------------

    private final  Class<?> targetClass;

    private final boolean isStatic;

    private EmptyTargetSource( Class<?> targetClass, boolean isStatic) {
        this.targetClass = targetClass;
        this.isStatic = isStatic;
    }

    @Override
    public  Class<?> getTargetClass() {
        return this.targetClass;
    }

    @Override
    public boolean isStatic() {
        return this.isStatic;
    }

    @Override
    public  Object getTarget() {
        return null;
    }

    private Object readResolve() {
        return (this.targetClass == null && this.isStatic ? INSTANCE : this);
    }

    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof EmptyTargetSource that &&
                ObjectUtils.nullSafeEquals(this.targetClass, that.targetClass) &&
                this.isStatic == that.isStatic));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), this.targetClass);
    }

    @Override
    public String toString() {
        return "EmptyTargetSource: " +
                (this.targetClass != null ? "target class [" + this.targetClass.getName() + "]" : "no target class") +
                ", " + (this.isStatic ? "static" : "dynamic");
    }
}
