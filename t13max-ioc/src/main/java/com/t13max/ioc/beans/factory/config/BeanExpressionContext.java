package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.util.Assert;

/**
 * @author t13max
 * @since 11:08 2026/1/16
 */
public class BeanExpressionContext {

    private final ConfigurableBeanFactory beanFactory;
    private final Scope scope;


    public BeanExpressionContext(ConfigurableBeanFactory beanFactory,  Scope scope) {
        Assert.notNull(beanFactory, "BeanFactory must not be null");
        this.beanFactory = beanFactory;
        this.scope = scope;
    }

    public final ConfigurableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }
    public final Scope getScope() {
        return this.scope;
    }


    public boolean containsObject(String key) {
        return (this.beanFactory.containsBean(key) ||
                (this.scope != null && this.scope.resolveContextualObject(key) != null));
    }
    public Object getObject(String key) {
        if (this.beanFactory.containsBean(key)) {
            return this.beanFactory.getBean(key);
        }
        else if (this.scope != null) {
            return this.scope.resolveContextualObject(key);
        }
        else {
            return null;
        }
    }


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof BeanExpressionContext that &&
                this.beanFactory == that.beanFactory && this.scope == that.scope));
    }

    @Override
    public int hashCode() {
        return this.beanFactory.hashCode();
    }
}
