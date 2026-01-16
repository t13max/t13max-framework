package com.t13max.ioc.aop;

import com.t13max.ioc.aop.intecept.MethodInvocation;

/**
 * @author t13max
 * @since 17:04 2026/1/16
 */
public interface ProxyMethodInvocation extends MethodInvocation {

    Object getProxy();

    MethodInvocation invocableClone();

    MethodInvocation invocableClone(Object... arguments);

    void setArguments(Object... arguments);

    void setUserAttribute(String key, Object value);

    Object getUserAttribute(String key);
}
