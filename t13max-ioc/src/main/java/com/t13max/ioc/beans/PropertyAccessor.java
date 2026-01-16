package com.t13max.ioc.beans;

import java.lang.invoke.TypeDescriptor;
import java.util.Map;

/**
 * @Author: t13max
 * @Since: 22:29 2026/1/16
 */
public interface PropertyAccessor {

    String NESTED_PROPERTY_SEPARATOR = ".";
    
    char NESTED_PROPERTY_SEPARATOR_CHAR = '.';
    
    String PROPERTY_KEY_PREFIX = "[";
    
    char PROPERTY_KEY_PREFIX_CHAR = '[';
    
    String PROPERTY_KEY_SUFFIX = "]";
    
    char PROPERTY_KEY_SUFFIX_CHAR = ']';

    
    boolean isReadableProperty(String propertyName);
    
    boolean isWritableProperty(String propertyName);
    
     Class<?> getPropertyType(String propertyName) throws BeansException;
    
    
    TypeDescriptor getPropertyTypeDescriptor(String propertyName) throws BeansException;
    
     Object getPropertyValue(String propertyName) throws BeansException;
    
    void setPropertyValue(String propertyName,  Object value) throws BeansException;
    
    void setPropertyValue(PropertyValue pv) throws BeansException;
    
    void setPropertyValues(Map<?, ?> map) throws BeansException;
    
    void setPropertyValues(PropertyValues pvs) throws BeansException;
    
    void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown)
            throws BeansException;
    
    void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
            throws BeansException;
}
