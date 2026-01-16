package com.t13max.ioc.aop.framework;

import com.t13max.ioc.utils.ClassUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;

/**
 * @author t13max
 * @since 16:45 2026/1/16
 */
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {
    public static final DefaultAopProxyFactory INSTANCE = new DefaultAopProxyFactory();

    private static final long serialVersionUID = 7930414337282325166L;


    @Override
    public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
        // 然后根据Class是否为接口采取不同的生成代理对象的策略
        if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
            Class<?> targetClass = config.getTargetClass();
            if (targetClass == null) {
                throw new AopConfigException("TargetSource cannot determine target class: Either an interface or a target is required for proxy creation.");
            }
            if (targetClass.isInterface() || Proxy.isProxyClass(targetClass) || ClassUtils.isLambdaClass(targetClass)) {
                //是接口则使用JDK动态代理
                return new JdkDynamicAopProxy(config);
            }
            return new ObjenesisCglibAopProxy(config);
        }
        else {
            return new JdkDynamicAopProxy(config);
        }
    }
    private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
        Class<?>[] ifcs = config.getProxiedInterfaces();
        return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
    }
}
