package com.t13max.ioc.aop.framework;

/**
 * @author t13max
 * @since 16:45 2026/1/16
 */
public interface AopProxyFactory {

    AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException;
}
