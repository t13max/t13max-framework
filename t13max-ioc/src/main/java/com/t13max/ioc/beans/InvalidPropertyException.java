package com.t13max.ioc.beans;

public class InvalidPropertyException extends FatalBeanException {

    private final Class<?> beanClass;

    private final String propertyName;

    
    public InvalidPropertyException(Class<?> beanClass, String propertyName, String msg) {
        this(beanClass, propertyName, msg, null);
    }
    
    public InvalidPropertyException(Class<?> beanClass, String propertyName, String msg,  Throwable cause) {
        super("Invalid property '" + propertyName + "' of bean class [" + beanClass.getName() + "]: " + msg, cause);
        this.beanClass = beanClass;
        this.propertyName = propertyName;
    }
    
    public Class<?> getBeanClass() {
        return this.beanClass;
    }
    
    public String getPropertyName() {
        return this.propertyName;
    }
}
