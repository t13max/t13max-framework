package com.t13max.ioc.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;

/**
 * @Author: t13max
 * @Since: 23:23 2026/1/16
 */
public interface BeanInfoFactory {

    BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException;
}
