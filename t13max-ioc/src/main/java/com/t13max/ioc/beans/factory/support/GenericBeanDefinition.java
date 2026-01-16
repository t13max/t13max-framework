package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.utils.ObjectUtils;

/**
 * @Author: t13max
 * @Since: 21:03 2026/1/16
 */
public class GenericBeanDefinition extends AbstractBeanDefinition {

    private String parentName;

    public GenericBeanDefinition() {
        super();
    }

    public GenericBeanDefinition(BeanDefinition original) {
        super(original);
    }

    @Override
    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    @Override
    public String getParentName() {
        return this.parentName;
    }


    @Override
    public AbstractBeanDefinition cloneBeanDefinition() {
        return new GenericBeanDefinition(this);
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof GenericBeanDefinition that && ObjectUtils.nullSafeEquals(this.parentName, that.parentName) && super.equals(other)));
    }

    @Override
    public String toString() {
        if (this.parentName != null) {
            return "Generic bean with parent '" + this.parentName + "': " + super.toString();
        }
        return "Generic bean: " + super.toString();
    }

}
