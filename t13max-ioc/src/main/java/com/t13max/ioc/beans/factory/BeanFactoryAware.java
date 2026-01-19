package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.BeansException;

/**
 * @Author: t13max
 * @Since: 21:30 2026/1/16
 */
public interface BeanFactoryAware extends Aware {

    void setBeanFactory(BeanFactory beanFactory) throws BeansException;
}
