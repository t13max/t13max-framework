package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeanUtils;
import com.t13max.ioc.beans.factory.config.BeanDefinitionHolder;
import com.t13max.ioc.beans.factory.config.DependencyDescriptor;

/**
 * @Author: t13max
 * @Since: 0:21 2026/1/17
 */
public interface AutowireCandidateResolver {

    default boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
        return bdHolder.getBeanDefinition().isAutowireCandidate();
    }

    default boolean isRequired(DependencyDescriptor descriptor) {
        return descriptor.isRequired();
    }

    default boolean hasQualifier(DependencyDescriptor descriptor) {
        return false;
    }

    default String getSuggestedName(DependencyDescriptor descriptor) {
        return null;
    }

    default Object getSuggestedValue(DependencyDescriptor descriptor) {
        return null;
    }

    default Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
        return null;
    }

    default Class<?> getLazyResolutionProxyClass(DependencyDescriptor descriptor, String beanName) {
        return null;
    }

    default AutowireCandidateResolver cloneIfNecessary() {
        return BeanUtils.instantiateClass(getClass());
    }

}
