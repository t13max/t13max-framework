package com.t13max.ioc.beans;

import java.beans.PropertyChangeEvent;

/**
 * @Author: t13max
 * @Since: 22:30 2026/1/16
 */
public abstract class PropertyAccessException extends BeansException {

    private final  PropertyChangeEvent propertyChangeEvent;

    
    public PropertyAccessException(PropertyChangeEvent propertyChangeEvent, String msg,  Throwable cause) {
        super(msg, cause);
        this.propertyChangeEvent = propertyChangeEvent;
    }
    
    public PropertyAccessException(String msg,  Throwable cause) {
        super(msg, cause);
        this.propertyChangeEvent = null;
    }

    
    public  PropertyChangeEvent getPropertyChangeEvent() {
        return this.propertyChangeEvent;
    }
    
    public  String getPropertyName() {
        return (this.propertyChangeEvent != null ? this.propertyChangeEvent.getPropertyName() : null);
    }
    
    public  Object getValue() {
        return (this.propertyChangeEvent != null ? this.propertyChangeEvent.getNewValue() : null);
    }
    
    public abstract String getErrorCode();
}
