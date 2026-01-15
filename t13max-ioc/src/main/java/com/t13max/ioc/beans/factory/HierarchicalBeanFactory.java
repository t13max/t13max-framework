package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 22:11 2026/1/15
 */
public interface HierarchicalBeanFactory extends BeanFactory {

    BeanFactory getParentBeanFactory();

    boolean containsLocalBean(String name);
}
