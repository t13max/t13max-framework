package com.t13max.ioc.beans.factory.config;

import java.util.function.Consumer;

/**
 * @Author: t13max
 * @Since: 22:13 2026/1/15
 */
public interface SingletonBeanRegistry {

    void registerSingleton(String beanName, Object singletonObject);

    void addSingletonCallback(String beanName, Consumer<Object> singletonConsumer);

    Object getSingleton(String beanName);

    boolean containsSingleton(String beanName);

    String[] getSingletonNames();

    int getSingletonCount();

}
