package com.t13max.ioc.beans;

import java.beans.PropertyDescriptor;

/**
 * @Author: t13max
 * @Since: 22:39 2026/1/16
 */
public interface BeanWrapper extends ConfigurablePropertyAccessor {
    
    void setAutoGrowCollectionLimit(int autoGrowCollectionLimit);
    
    int getAutoGrowCollectionLimit();
    
    Object getWrappedInstance();
    
    Class<?> getWrappedClass();
    
    PropertyDescriptor[] getPropertyDescriptors();
    
    PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException;

}
