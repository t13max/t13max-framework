package com.t13max.ioc.beans;

import com.t13max.ioc.core.Ordered;

import java.beans.*;
import java.util.Collection;

/**
 * @Author: t13max
 * @Since: 23:24 2026/1/16
 */
public class SimpleBeanInfoFactory implements BeanInfoFactory, Ordered {

    @Override
    public BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException {
        Collection<? extends PropertyDescriptor> pds =
                PropertyDescriptorUtils.determineBasicProperties(beanClass);

        return new SimpleBeanInfo() {
            @Override
            public BeanDescriptor getBeanDescriptor() {
                return new BeanDescriptor(beanClass);
            }
            @Override
            public PropertyDescriptor[] getPropertyDescriptors() {
                return pds.toArray(PropertyDescriptorUtils.EMPTY_PROPERTY_DESCRIPTOR_ARRAY);
            }
        };
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
