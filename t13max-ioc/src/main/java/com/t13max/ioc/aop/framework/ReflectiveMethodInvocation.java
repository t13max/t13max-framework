package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.ProxyMethodInvocation;
import com.t13max.ioc.aop.intecept.MethodInterceptor;
import com.t13max.ioc.aop.intecept.MethodInvocation;
import com.t13max.ioc.aop.support.AopUtils;
import com.t13max.ioc.core.BridgeMethodResolver;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author t13max
 * @since 17:04 2026/1/16
 */
public class ReflectiveMethodInvocation implements ProxyMethodInvocation, Cloneable {

    protected final Object proxy;
    protected final Object target;

    protected final Method method;

    protected Object[] arguments;
    private final Class<?> targetClass;

    private Map<String, Object> userAttributes;
    protected final List<?> interceptorsAndDynamicMethodMatchers;
    private int currentInterceptorIndex = -1;

    protected ReflectiveMethodInvocation(
            Object proxy, Object target, Method method, Object[] arguments,
            Class<?> targetClass, List<Object> interceptorsAndDynamicMethodMatchers) {

        this.proxy = proxy;
        this.target = target;
        this.targetClass = targetClass;
        this.method = BridgeMethodResolver.findBridgedMethod(method);
        this.arguments = AopProxyUtils.adaptArgumentsIfNecessary(method, arguments);
        this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
    }


    @Override
    public final Object getProxy() {
        return this.proxy;
    }

    @Override
    public final Object getThis() {
        return this.target;
    }

    @Override
    public final AccessibleObject getStaticPart() {
        return this.method;
    }

    @Override
    public final Method getMethod() {
        return this.method;
    }

    @Override
    public final Object[] getArguments() {
        return this.arguments;
    }

    @Override
    public void setArguments(Object... arguments) {
        this.arguments = arguments;
    }

    //从拦截器链中调用拦截器
    //对目标方法的调用是在invokeJoinpoint()中通过AopUtils的invokeJoinpointUsingReflection()完成的
    @Override
    public Object proceed() throws Throwable {
        // We start with an index of -1 and increment early.
        if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
            return invokeJoinpoint();
        }

        Object interceptorOrInterceptionAdvice = this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
        if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher dm) {
            //通过拦截器的方法匹配器进行匹配
            Class<?> targetClass = (this.targetClass != null ? this.targetClass : this.method.getDeclaringClass());
            if (dm.matcher().matches(this.method, targetClass, this.arguments)) {
                //执行当前这个拦截器interceptor的增强方法
                return dm.interceptor().invoke(this);
            } else {
                // 如果不匹配,那么process()方法会被递归调用, 直到所有的拦截器都被运行过为止
                return proceed();
            }
        } else {
            // 如果interceptorOrInterceptionAdvice是一个MethodInterceptor则直接调用其对应的方法
            return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
        }
    }

    protected Object invokeJoinpoint() throws Throwable {
        return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
    }

    @Override
    public MethodInvocation invocableClone() {
        Object[] cloneArguments = this.arguments;
        if (this.arguments.length > 0) {
            // Build an independent copy of the arguments array.
            cloneArguments = this.arguments.clone();
        }
        return invocableClone(cloneArguments);
    }

    @Override
    public MethodInvocation invocableClone(Object... arguments) {
        // Force initialization of the user attributes Map,
        // for having a shared Map reference in the clone.
        if (this.userAttributes == null) {
            this.userAttributes = new HashMap<>();
        }

        // Create the MethodInvocation clone.
        try {
            ReflectiveMethodInvocation clone = (ReflectiveMethodInvocation) clone();
            clone.arguments = arguments;
            return clone;
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(
                    "Should be able to clone object of type [" + getClass() + "]: " + ex);
        }
    }


    @Override
    public void setUserAttribute(String key, Object value) {
        if (value != null) {
            if (this.userAttributes == null) {
                this.userAttributes = new HashMap<>();
            }
            this.userAttributes.put(key, value);
        } else {
            if (this.userAttributes != null) {
                this.userAttributes.remove(key);
            }
        }
    }

    @Override
    public Object getUserAttribute(String key) {
        return (this.userAttributes != null ? this.userAttributes.get(key) : null);
    }

    public Map<String, Object> getUserAttributes() {
        if (this.userAttributes == null) {
            this.userAttributes = new HashMap<>();
        }
        return this.userAttributes;
    }


    @Override
    public String toString() {
        // Don't do toString on target, it may be proxied.
        StringBuilder sb = new StringBuilder("ReflectiveMethodInvocation: ");
        sb.append(this.method).append("; ");
        if (this.target == null) {
            sb.append("target is null");
        } else {
            sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
        }
        return sb.toString();
    }
}
