package com.t13max.ioc.beans.factory;

/**
 * @Author: t13max
 * @Since: 8:31 2026/1/16
 */
public interface DisposableBean {

    void destroy() throws Exception;
}
