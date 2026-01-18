package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.util.Assert;

/**
 * @Author: t13max
 * @Since: 7:53 2026/1/17
 */
public class ScopeMetadata {

    private String scopeName = BeanDefinition.SCOPE_SINGLETON;

    private ScopedProxyMode scopedProxyMode = ScopedProxyMode.NO;

    public void setScopeName(String scopeName) {
        Assert.notNull(scopeName, "'scopeName' must not be null");
        this.scopeName = scopeName;
    }

    public String getScopeName() {
        return this.scopeName;
    }

    public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
        Assert.notNull(scopedProxyMode, "'scopedProxyMode' must not be null");
        this.scopedProxyMode = scopedProxyMode;
    }

    public ScopedProxyMode getScopedProxyMode() {
        return this.scopedProxyMode;
    }
}
