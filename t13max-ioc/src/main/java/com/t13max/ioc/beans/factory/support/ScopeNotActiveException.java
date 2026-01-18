package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.BeanCreationException;

/**
 * @Author: t13max
 * @Since: 0:23 2026/1/17
 */
public class ScopeNotActiveException  extends BeanCreationException {

    public ScopeNotActiveException(String beanName, String scopeName, IllegalStateException cause) {
        super(beanName, "Scope '" + scopeName + "' is not active for the current thread; consider defining a scoped proxy for this bean if you intend to refer to it from a singleton", cause);
    }
}
