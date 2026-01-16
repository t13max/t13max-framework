package com.t13max.ioc.core.io;

/**
 * @Author: t13max
 * @Since: 20:48 2026/1/16
 */
@FunctionalInterface
public interface ProtocolResolver {

    Resource resolve(String location, ResourceLoader resourceLoader);
}
