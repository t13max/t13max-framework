package com.t13max.ioc.context;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.Aware;

/**
 * @Author: t13max
 * @Since: 21:39 2026/1/16
 */
public interface ApplicationContextAware extends Aware {

    void setApplicationContext(ApplicationContext applicationContext) throws BeansException;
}
