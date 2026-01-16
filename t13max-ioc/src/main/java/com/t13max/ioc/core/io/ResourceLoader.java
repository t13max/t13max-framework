package com.t13max.ioc.core.io;

/**
 * 资源加载器接口
 *
 * @Author: t13max
 * @Since: 21:22 2026/1/15
 */
public interface ResourceLoader {

    Resource getResource(String location);

    ClassLoader getClassLoader();
}
