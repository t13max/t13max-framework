package com.t13max.ioc.beans;

import com.t13max.ioc.core.MethodParameter;

import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.Field;

/**
 * @Author: t13max
 * @Since: 22:28 2026/1/16
 */
public interface TypeConverter {

    <T>  T convertIfNecessary( Object value,  Class<T> requiredType) throws TypeMismatchException;
    
    <T>  T convertIfNecessary( Object value,  Class<T> requiredType,
                                        MethodParameter methodParam) throws TypeMismatchException;
    
    <T>  T convertIfNecessary( Object value,  Class<T> requiredType,  Field field)
            throws TypeMismatchException;
    
    default <T>  T convertIfNecessary( Object value,  Class<T> requiredType,
                                                TypeDescriptor typeDescriptor) throws TypeMismatchException {

        throw new UnsupportedOperationException("TypeDescriptor resolution not supported");
    }
}
