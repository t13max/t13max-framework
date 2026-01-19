package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 20:34 2026/1/16
 */
public interface InitializingBean {

    void afterPropertiesSet() throws Exception;
}
