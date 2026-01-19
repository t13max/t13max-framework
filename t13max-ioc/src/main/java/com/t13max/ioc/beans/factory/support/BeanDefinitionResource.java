package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.core.io.AbstractResource;

import com.t13max.ioc.util.Assert;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: t13max
 * @Since: 0:00 2026/1/17
 */
public class BeanDefinitionResource extends AbstractResource {

    private final BeanDefinition beanDefinition;


    
    public BeanDefinitionResource(BeanDefinition beanDefinition) {
        Assert.notNull(beanDefinition, "BeanDefinition must not be null");
        this.beanDefinition = beanDefinition;
    }

    
    public final BeanDefinition getBeanDefinition() {
        return this.beanDefinition;
    }


    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new FileNotFoundException(
                "Resource cannot be opened because it points to " + getDescription());
    }

    @Override
    public String getDescription() {
        return "BeanDefinition defined in " + this.beanDefinition.getResourceDescription();
    }


    
    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof BeanDefinitionResource that &&
                this.beanDefinition.equals(that.beanDefinition)));
    }

    
    @Override
    public int hashCode() {
        return this.beanDefinition.hashCode();
    }
}
