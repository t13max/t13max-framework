package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.config.BeanDefinition;

/**
 * @Author: t13max
 * @Since: 7:53 2026/1/17
 */
@FunctionalInterface
public interface ScopeMetadataResolver {

    ScopeMetadata resolveScopeMetadata(BeanDefinition definition);
}
