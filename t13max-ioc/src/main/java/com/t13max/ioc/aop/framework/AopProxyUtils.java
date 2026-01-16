package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.SpringProxy;
import com.t13max.ioc.aop.TargetClassAware;
import com.t13max.ioc.aop.TargetSource;
import com.t13max.ioc.aop.support.AopUtils;
import com.t13max.ioc.aop.target.SingletonTargetSource;
import com.t13max.ioc.core.DecoratingProxy;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ObjectUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: t13max
 * @Since: 22:12 2026/1/16
 */
public class AopProxyUtils {

    public static  Object getSingletonTarget(Object candidate) {
        if (candidate instanceof Advised advised) {
            TargetSource targetSource = advised.getTargetSource();
            if (targetSource instanceof SingletonTargetSource singleTargetSource) {
                return singleTargetSource.getTarget();
            }
        }
        return null;
    }

    public static Class<?> ultimateTargetClass(Object candidate) {
        Assert.notNull(candidate, "Candidate object must not be null");
        Object current = candidate;
        Class<?> result = null;
        while (current instanceof TargetClassAware targetClassAware) {
            result = targetClassAware.getTargetClass();
            current = getSingletonTarget(current);
        }
        if (result == null) {
            result = (AopUtils.isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
        }
        return result;
    }

    public static Class<?>[] completeJdkProxyInterfaces(Class<?>... userInterfaces) {
        List<Class<?>> completedInterfaces = new ArrayList<>(userInterfaces.length + 3);
        for (Class<?> ifc : userInterfaces) {
            Assert.notNull(ifc, "'userInterfaces' must not contain null values");
            Assert.isTrue(ifc.isInterface() && !ifc.isSealed(),
                    () -> ifc.getName() + " must be a non-sealed interface");
            completedInterfaces.add(ifc);
        }
        completedInterfaces.add(SpringProxy.class);
        completedInterfaces.add(Advised.class);
        completedInterfaces.add(DecoratingProxy.class);
        return completedInterfaces.toArray(Class<?>[]::new);
    }

    public static Class<?>[] completeProxiedInterfaces(AdvisedSupport advised) {
        return completeProxiedInterfaces(advised, false);
    }

    static Class<?>[] completeProxiedInterfaces(AdvisedSupport advised, boolean decoratingProxy) {
        Class<?>[] specifiedInterfaces = advised.getProxiedInterfaces();
        if (specifiedInterfaces.length == 0) {
            // No user-specified interfaces: check whether target class is an interface.
            Class<?> targetClass = advised.getTargetClass();
            if (targetClass != null) {
                if (targetClass.isInterface()) {
                    advised.setInterfaces(targetClass);
                }
                else if (Proxy.isProxyClass(targetClass) || ClassUtils.isLambdaClass(targetClass)) {
                    advised.setInterfaces(targetClass.getInterfaces());
                }
                specifiedInterfaces = advised.getProxiedInterfaces();
            }
        }
        List<Class<?>> proxiedInterfaces = new ArrayList<>(specifiedInterfaces.length + 3);
        for (Class<?> ifc : specifiedInterfaces) {
            // Only non-sealed interfaces are actually eligible for JDK proxying (on JDK 17)
            if (!ifc.isSealed()) {
                proxiedInterfaces.add(ifc);
            }
        }
        if (!advised.isInterfaceProxied(SpringProxy.class)) {
            proxiedInterfaces.add(SpringProxy.class);
        }
        if (!advised.isOpaque() && !advised.isInterfaceProxied(Advised.class)) {
            proxiedInterfaces.add(Advised.class);
        }
        if (decoratingProxy && !advised.isInterfaceProxied(DecoratingProxy.class)) {
            proxiedInterfaces.add(DecoratingProxy.class);
        }
        return ClassUtils.toClassArray(proxiedInterfaces);
    }    
    public static Class<?>[] proxiedUserInterfaces(Object proxy) {
        Class<?>[] proxyInterfaces = proxy.getClass().getInterfaces();
        int nonUserIfcCount = 0;
        if (proxy instanceof SpringProxy) {
            nonUserIfcCount++;
        }
        if (proxy instanceof Advised) {
            nonUserIfcCount++;
        }
        if (proxy instanceof DecoratingProxy) {
            nonUserIfcCount++;
        }
        Class<?>[] userInterfaces = Arrays.copyOf(proxyInterfaces, proxyInterfaces.length - nonUserIfcCount);
        Assert.notEmpty(userInterfaces, "JDK proxy must implement one or more interfaces");
        return userInterfaces;
    }    
    public static boolean equalsInProxy(AdvisedSupport a, AdvisedSupport b) {
        return (a == b ||
                (equalsProxiedInterfaces(a, b) && equalsAdvisors(a, b) && a.getTargetSource().equals(b.getTargetSource())));
    }    
    public static boolean equalsProxiedInterfaces(AdvisedSupport a, AdvisedSupport b) {
        return Arrays.equals(a.getProxiedInterfaces(), b.getProxiedInterfaces());
    }    
    public static boolean equalsAdvisors(AdvisedSupport a, AdvisedSupport b) {
        return a.getAdvisorCount() == b.getAdvisorCount() && Arrays.equals(a.getAdvisors(), b.getAdvisors());
    }
    
    static  Object[] adaptArgumentsIfNecessary(Method method,  Object[] arguments) {
        if (ObjectUtils.isEmpty(arguments)) {
            return new Object[0];
        }
        if (method.isVarArgs() && (method.getParameterCount() == arguments.length)) {
            Class<?>[] paramTypes = method.getParameterTypes();
            int varargIndex = paramTypes.length - 1;
            Class<?> varargType = paramTypes[varargIndex];
            if (varargType.isArray()) {
                Object varargArray = arguments[varargIndex];
                if (varargArray instanceof Object[] && !varargType.isInstance(varargArray)) {
                    Object[] newArguments = new Object[arguments.length];
                    System.arraycopy(arguments, 0, newArguments, 0, varargIndex);
                    Class<?> targetElementType = varargType.componentType();
                    int varargLength = Array.getLength(varargArray);
                    Object newVarargArray = Array.newInstance(targetElementType, varargLength);
                    System.arraycopy(varargArray, 0, newVarargArray, 0, varargLength);
                    newArguments[varargIndex] = newVarargArray;
                    return newArguments;
                }
            }
        }
        return arguments;
    }

}
