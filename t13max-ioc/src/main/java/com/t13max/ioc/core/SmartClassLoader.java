package com.t13max.ioc.core;

import java.security.ProtectionDomain;

/**
 * @Author: t13max
 * @Since: 21:45 2026/1/16
 */
public interface SmartClassLoader {

    default boolean isClassReloadable(Class<?> clazz) {
        return false;
    }

    default ClassLoader getOriginalClassLoader() {
        return (ClassLoader) this;
    }

    default Class<?> publicDefineClass(String name, byte[] b, ProtectionDomain protectionDomain) {
        throw new UnsupportedOperationException();
    }
}
