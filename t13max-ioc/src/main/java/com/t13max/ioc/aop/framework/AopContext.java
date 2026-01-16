package com.t13max.ioc.aop.framework;

/**
 * @Author: t13max
 * @Since: 22:18 2026/1/16
 */
public class AopContext {
    
    private static final ThreadLocal<Object> currentProxy = new NamedThreadLocal<>("Current AOP proxy");


    private AopContext() {
    }
    
    public static Object currentProxy() throws IllegalStateException {
        Object proxy = currentProxy.get();
        if (proxy == null) {
            throw new IllegalStateException(
                    "Cannot find current proxy: Set 'exposeProxy' property on Advised to 'true' to make it available, and " +
                            "ensure that AopContext.currentProxy() is invoked in the same thread as the AOP invocation context.");
        }
        return proxy;
    }    
    static Object setCurrentProxy(Object proxy) {
        Object old = currentProxy.get();
        if (proxy != null) {
            currentProxy.set(proxy);
        } else {
            currentProxy.remove();
        }
        return old;
    }

}
