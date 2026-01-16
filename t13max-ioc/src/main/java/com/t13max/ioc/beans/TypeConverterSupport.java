package com.t13max.ioc.beans;

import com.t13max.ioc.core.MethodParameter;
import com.t13max.ioc.utils.Assert;

import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.Field;

/**
 * @Author: t13max
 * @Since: 22:28 2026/1/16
 */
public class TypeConverterSupport extends PropertyEditorRegistrySupport implements TypeConverter {

     TypeConverterDelegate typeConverterDelegate;


    @Override
    public <T>  T convertIfNecessary( Object value,  Class<T> requiredType) throws TypeMismatchException {
        return convertIfNecessary(null, value, requiredType, TypeDescriptor.valueOf(requiredType));
    }

    @Override
    public <T>  T convertIfNecessary( Object value,  Class<T> requiredType,
                                               MethodParameter methodParam) throws TypeMismatchException {

        return convertIfNecessary((methodParam != null ? methodParam.getParameterName() : null), value, requiredType,
                (methodParam != null ? new TypeDescriptor(methodParam) : TypeDescriptor.valueOf(requiredType)));
    }

    @Override
    public <T>  T convertIfNecessary( Object value,  Class<T> requiredType,  Field field)
            throws TypeMismatchException {

        return convertIfNecessary((field != null ? field.getName() : null), value, requiredType,
                (field != null ? new TypeDescriptor(field) : TypeDescriptor.valueOf(requiredType)));
    }

    @Override
    public <T>  T convertIfNecessary( Object value,  Class<T> requiredType,
                                               TypeDescriptor typeDescriptor) throws TypeMismatchException {

        return convertIfNecessary(null, value, requiredType, typeDescriptor);
    }

    private <T>  T convertIfNecessary( String propertyName,  Object value,
                                                Class<T> requiredType,  TypeDescriptor typeDescriptor) throws TypeMismatchException {

        Assert.state(this.typeConverterDelegate != null, "No TypeConverterDelegate");
        try {
            return this.typeConverterDelegate.convertIfNecessary(
                    propertyName, null, value, requiredType, typeDescriptor);
        }
        catch (ConverterNotFoundException | IllegalStateException ex) {
            throw new ConversionNotSupportedException(value, requiredType, ex);
        }
        catch (ConversionException | IllegalArgumentException ex) {
            throw new TypeMismatchException(value, requiredType, ex);
        }
    }

}
