package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.TargetSource;
import com.t13max.ioc.aop.intecept.Interceptor;
import com.t13max.ioc.util.ClassUtils;

/**
 * @Author: t13max
 * @Since: 21:56 2026/1/16
 */
public class ProxyFactory extends ProxyCreatorSupport {    
    public ProxyFactory() {
    }    
    public ProxyFactory(Object target) {
        setTarget(target);
        setInterfaces(ClassUtils.getAllInterfaces(target));
    }    
    public ProxyFactory(Class<?>... proxyInterfaces) {
        setInterfaces(proxyInterfaces);
    }    
    public ProxyFactory(Class<?> proxyInterface, Interceptor interceptor) {
        addInterface(proxyInterface);
        addAdvice(interceptor);
    }    
    public ProxyFactory(Class<?> proxyInterface, TargetSource targetSource) {
        addInterface(proxyInterface);
        setTargetSource(targetSource);
    }
    
    public Object getProxy() {
        return createAopProxy().getProxy();
    }    
    public Object getProxy(ClassLoader classLoader) {
        return createAopProxy().getProxy(classLoader);
    }    
    public Class<?> getProxyClass(ClassLoader classLoader) {
        return createAopProxy().getProxyClass(classLoader);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T getProxy(Class<T> proxyInterface, Interceptor interceptor) {
        return (T) new ProxyFactory(proxyInterface, interceptor).getProxy();
    }    
    @SuppressWarnings("unchecked")
    public static <T> T getProxy(Class<T> proxyInterface, TargetSource targetSource) {
        return (T) new ProxyFactory(proxyInterface, targetSource).getProxy();
    }    
    public static Object getProxy(TargetSource targetSource) {
        if (targetSource.getTargetClass() == null) {
            throw new IllegalArgumentException("Cannot create class proxy for TargetSource with null target class");
        }
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTargetSource(targetSource);
        proxyFactory.setProxyTargetClass(true);
        return proxyFactory.getProxy();
    }
}
