package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.BeanDefinitionStoreException;
import com.t13max.ioc.beans.factory.config.BeanDefinition;

/**
 * @Author: t13max
 * @Since: 0:29 2026/1/17
 */
public class BeanDefinitionOverrideException extends BeanDefinitionStoreException {

    private final BeanDefinition beanDefinition;

    private final BeanDefinition existingDefinition;


    
    public BeanDefinitionOverrideException(
            String beanName, BeanDefinition beanDefinition, BeanDefinition existingDefinition) {

        super(beanDefinition.getResourceDescription(), beanName,
                "Cannot register bean definition [" + beanDefinition + "] for bean '" + beanName +
                        "' since there is already [" + existingDefinition + "] bound.");
        this.beanDefinition = beanDefinition;
        this.existingDefinition = existingDefinition;
    }

    
    public BeanDefinitionOverrideException(
            String beanName, BeanDefinition beanDefinition, BeanDefinition existingDefinition, String msg) {

        super(beanDefinition.getResourceDescription(), beanName, msg);
        this.beanDefinition = beanDefinition;
        this.existingDefinition = existingDefinition;
    }


    
    @Override
    public String getResourceDescription() {
        return String.valueOf(super.getResourceDescription());
    }

    
    @Override
    public String getBeanName() {
        return String.valueOf(super.getBeanName());
    }

    
    public BeanDefinition getBeanDefinition() {
        return this.beanDefinition;
    }

    
    public BeanDefinition getExistingDefinition() {
        return this.existingDefinition;
    }

}
