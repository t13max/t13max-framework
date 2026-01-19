package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeanMetadataElement;
import com.t13max.ioc.beans.factory.BeanFactoryUtils;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ObjectUtils;
import com.t13max.ioc.util.StringUtils;

/**
 * @Author: t13max
 * @Since: 22:51 2026/1/15
 */
public class BeanDefinitionHolder implements BeanMetadataElement {

    private final BeanDefinition beanDefinition;

    private final String beanName;

    private final String [] aliases;

    public BeanDefinitionHolder(BeanDefinition beanDefinition, String beanName) {
        this(beanDefinition, beanName, null);
    }

    public BeanDefinitionHolder(BeanDefinition beanDefinition, String beanName, String [] aliases) {
        Assert.notNull(beanDefinition, "BeanDefinition must not be null");
        Assert.notNull(beanName, "Bean name must not be null");
        this.beanDefinition = beanDefinition;
        this.beanName = beanName;
        this.aliases = aliases;
    }

    public BeanDefinitionHolder(BeanDefinitionHolder beanDefinitionHolder) {
        Assert.notNull(beanDefinitionHolder, "BeanDefinitionHolder must not be null");
        this.beanDefinition = beanDefinitionHolder.getBeanDefinition();
        this.beanName = beanDefinitionHolder.getBeanName();
        this.aliases = beanDefinitionHolder.getAliases();
    }

    public BeanDefinition getBeanDefinition() {
        return this.beanDefinition;
    }

    public String getBeanName() {
        return this.beanName;
    }

    public String [] getAliases() {
        return this.aliases;
    }

    @Override
    public Object getSource() {
        return this.beanDefinition.getSource();
    }

    public boolean matchesName(String candidateName) {
        return (candidateName != null && (candidateName.equals(this.beanName) ||
                candidateName.equals(BeanFactoryUtils.transformedBeanName(this.beanName)) ||
                ObjectUtils.containsElement(this.aliases, candidateName)));
    }

    public String getShortDescription() {
        if (this.aliases == null) {
            return "Bean definition with name '" + this.beanName + "'";
        }
        return "Bean definition with name '" + this.beanName + "' and aliases [" + StringUtils.arrayToCommaDelimitedString(this.aliases) + ']';
    }

    public String getLongDescription() {
        return getShortDescription() + ": " + this.beanDefinition;
    }

    @Override
    public String toString() {
        return getLongDescription();
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof BeanDefinitionHolder that &&
                this.beanDefinition.equals(that.beanDefinition) &&
                this.beanName.equals(that.beanName) &&
                ObjectUtils.nullSafeEquals(this.aliases, that.aliases)));
    }

    @Override
    public int hashCode() {
        return ObjectUtils.nullSafeHash(this.beanDefinition, this.beanName, this.aliases);
    }
}
