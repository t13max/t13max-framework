package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 21:33 2026/1/16
 */
public interface BeanFactoryInitializer <F extends ListableBeanFactory> {

    void initialize(F beanFactory);
}
