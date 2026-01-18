package com.t13max.ioc.beans.factory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Objects;


import com.t13max.ioc.core.MethodParameter;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ObjectUtils;

public class InjectionPoint {

    protected MethodParameter methodParameter;

    protected Field field;

    private volatile Annotation[] fieldAnnotations;

    public InjectionPoint(MethodParameter methodParameter) {
        Assert.notNull(methodParameter, "MethodParameter must not be null");
        this.methodParameter = methodParameter;
    }

    public InjectionPoint(Field field) {
        Assert.notNull(field, "Field must not be null");
        this.field = field;
    }

    protected InjectionPoint(InjectionPoint original) {
        this.methodParameter = (original.methodParameter != null ?
                new MethodParameter(original.methodParameter) : null);
        this.field = original.field;
        this.fieldAnnotations = original.fieldAnnotations;
    }

    protected InjectionPoint() {
    }

    public MethodParameter getMethodParameter() {
        return this.methodParameter;
    }

    public Field getField() {
        return this.field;
    }

    protected final MethodParameter obtainMethodParameter() {
        Assert.state(this.methodParameter != null, "MethodParameter is not available");
        return this.methodParameter;
    }

    public Annotation[] getAnnotations() {
        if (this.field != null) {
            Annotation[] fieldAnnotations = this.fieldAnnotations;
            if (fieldAnnotations == null) {
                fieldAnnotations = this.field.getAnnotations();
                this.fieldAnnotations = fieldAnnotations;
            }
            return fieldAnnotations;
        } else {
            return obtainMethodParameter().getParameterAnnotations();
        }
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return (this.field != null ? this.field.getAnnotation(annotationType) :
                obtainMethodParameter().getParameterAnnotation(annotationType));
    }

    public Class<?> getDeclaredType() {
        return (this.field != null ? this.field.getType() : obtainMethodParameter().getParameterType());
    }

    public Member getMember() {
        return (this.field != null ? this.field : obtainMethodParameter().getMember());
    }

    public AnnotatedElement getAnnotatedElement() {
        return (this.field != null ? this.field : obtainMethodParameter().getAnnotatedElement());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        InjectionPoint otherPoint = (InjectionPoint) other;
        return (ObjectUtils.nullSafeEquals(this.field, otherPoint.field) &&
                ObjectUtils.nullSafeEquals(this.methodParameter, otherPoint.methodParameter));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.field, this.methodParameter);
    }

    @Override
    public String toString() {
        return (this.field != null ? "field '" + this.field.getName() + "'" : String.valueOf(this.methodParameter));
    }

}
