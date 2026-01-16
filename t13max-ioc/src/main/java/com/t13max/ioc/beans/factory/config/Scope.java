package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.factory.ObjectFactory;

/**
 * @Author: t13max
 * @Since: 21:20 2026/1/16
 */
public interface Scope {

    Object get(String name, ObjectFactory<?> objectFactory);

    Object remove(String name);

    void registerDestructionCallback(String name, Runnable callback);

    Object resolveContextualObject(String key);

    String getConversationId();
}
