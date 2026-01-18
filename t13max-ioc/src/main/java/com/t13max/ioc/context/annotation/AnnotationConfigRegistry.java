package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.BeanRegistrar;

/**
 * @Author: t13max
 * @Since: 7:50 2026/1/17
 */
public interface AnnotationConfigRegistry {

    void register(BeanRegistrar... registrars);

    void register(Class<?>... componentClasses);

    void scan(String... basePackages);
}
