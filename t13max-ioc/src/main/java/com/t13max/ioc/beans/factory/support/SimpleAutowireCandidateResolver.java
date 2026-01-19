package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanFactoryUtils;
import com.t13max.ioc.beans.factory.ListableBeanFactory;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author: t13max
 * @Since: 0:22 2026/1/17
 */
public class SimpleAutowireCandidateResolver implements AutowireCandidateResolver {

    public static final SimpleAutowireCandidateResolver INSTANCE = new SimpleAutowireCandidateResolver();

    @Override
    public AutowireCandidateResolver cloneIfNecessary() {
        return this;
    }

    public static <T> Map<String, T> resolveAutowireCandidates(ConfigurableListableBeanFactory lbf, Class<T> type) {
        return resolveAutowireCandidates(lbf, type, true, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> resolveAutowireCandidates(ConfigurableListableBeanFactory lbf, Class<T> type,
                                                               boolean includeNonSingletons, boolean allowEagerInit) {

        Map<String, T> candidates = new LinkedHashMap<>();
        for (String beanName : BeanFactoryUtils.beanNamesForTypeIncludingAncestors(lbf, type,
                includeNonSingletons, allowEagerInit)) {
            if (AutowireUtils.isAutowireCandidate(lbf, beanName)) {
                Object beanInstance = lbf.getBean(beanName);
                if (!(beanInstance instanceof NullBean)) {
                    candidates.put(beanName, (T) beanInstance);
                }
            }
        }
        return candidates;
    }
}
