package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.FatalBeanException;

/**
 * @Author: t13max
 * @Since: 20:50 2026/1/16
 */
public class BeanDefinitionStoreException extends FatalBeanException {

    private final  String resourceDescription;

    private final  String beanName;

    public BeanDefinitionStoreException(String msg) {
        super(msg);
        this.resourceDescription = null;
        this.beanName = null;
    }
    public BeanDefinitionStoreException(String msg,  Throwable cause) {
        super(msg, cause);
        this.resourceDescription = null;
        this.beanName = null;
    }
    public BeanDefinitionStoreException( String resourceDescription, String msg) {
        super(msg);
        this.resourceDescription = resourceDescription;
        this.beanName = null;
    }
    public BeanDefinitionStoreException( String resourceDescription, String msg,  Throwable cause) {
        super(msg, cause);
        this.resourceDescription = resourceDescription;
        this.beanName = null;
    }
    public BeanDefinitionStoreException( String resourceDescription, String beanName, String msg) {
        this(resourceDescription, beanName, msg, null);
    }
    public BeanDefinitionStoreException(
             String resourceDescription, String beanName,  String msg,  Throwable cause) {

        super(msg == null ?
                        "Invalid bean definition with name '" + beanName + "' defined in " + resourceDescription :
                        "Invalid bean definition with name '" + beanName + "' defined in " + resourceDescription + ": " + msg,
                cause);
        this.resourceDescription = resourceDescription;
        this.beanName = beanName;
    }

    public  String getResourceDescription() {
        return this.resourceDescription;
    }
    public  String getBeanName() {
        return this.beanName;
    }
}
