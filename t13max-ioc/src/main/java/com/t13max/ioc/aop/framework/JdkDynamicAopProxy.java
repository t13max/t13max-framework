package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.AopInvocationException;
import com.t13max.ioc.aop.RawTargetAccess;
import com.t13max.ioc.aop.TargetSource;
import com.t13max.ioc.aop.intecept.MethodInvocation;
import com.t13max.ioc.aop.support.AopUtils;
import com.t13max.ioc.core.DecoratingProxy;
import com.t13max.ioc.core.KotlinDetector;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * @author t13max
 * @since 16:48 2026/1/16
 */
public class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {
    private static final long serialVersionUID = 5531744639992436476L;


    private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";

    private static final boolean coroutinesReactorPresent = ClassUtils.isPresent("kotlinx.coroutines.reactor.MonoKt", JdkDynamicAopProxy.class.getClassLoader());
    private static final Logger logger = LogManager.getLogger(JdkDynamicAopProxy.class);
    private final AdvisedSupport advised;
    private transient ProxiedInterfacesCache cache;

    public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
        Assert.notNull(config, "AdvisedSupport must not be null");
        this.advised = config;

        // Initialize ProxiedInterfacesCache if not cached already
        ProxiedInterfacesCache cache;
        if (config.proxyMetadataCache instanceof ProxiedInterfacesCache proxiedInterfacesCache) {
            cache = proxiedInterfacesCache;
        }
        else {
            cache = new ProxiedInterfacesCache(config);
            config.proxyMetadataCache = cache;
        }
        this.cache = cache;
    }


    @Override
    public Object getProxy() {
        return getProxy(ClassUtils.getDefaultClassLoader());
    }

    @Override
    public Object getProxy( ClassLoader classLoader) {
        if (logger.isTraceEnabled()) {
            logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
        }
        return Proxy.newProxyInstance(determineClassLoader(classLoader), this.cache.proxiedInterfaces, this);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Class<?> getProxyClass( ClassLoader classLoader) {
        return Proxy.getProxyClass(determineClassLoader(classLoader), this.cache.proxiedInterfaces);
    }
    private ClassLoader determineClassLoader( ClassLoader classLoader) {
        if (classLoader == null) {
            // JDK bootstrap loader -> use spring-aop ClassLoader instead.
            return getClass().getClassLoader();
        }
        if (classLoader.getParent() == null) {
            // Potentially the JDK platform loader on JDK 9+
            ClassLoader aopClassLoader = getClass().getClassLoader();
            ClassLoader aopParent = aopClassLoader.getParent();
            while (aopParent != null) {
                if (classLoader == aopParent) {
                    // Suggested ClassLoader is ancestor of spring-aop ClassLoader
                    // -> use spring-aop ClassLoader itself instead.
                    return aopClassLoader;
                }
                aopParent = aopParent.getParent();
            }
        }
        // Regular case: use suggested ClassLoader as-is.
        return classLoader;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        Object oldProxy = null;
        boolean setProxyContext = false;

        //通过 targetSource 可以获取被代理对象
        TargetSource targetSource = this.advised.targetSource;
        Object target = null;

        try {
            //Obejct的equals
            if (!this.cache.equalsDefined && AopUtils.isEqualsMethod(method)) {
                // 如果目标对象没有重写Object类的equals(Object other)
                return equals(args[0]);
            }
            //Object的hashCode
            else if (!this.cache.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
                // 如果目标对象没有重写Object类的hashCode()
                return hashCode();
            }
            else if (method.getDeclaringClass() == DecoratingProxy.class) {
                // There is only getDecoratedClass() declared -> dispatch to proxy config.
                return AopProxyUtils.ultimateTargetClass(this.advised);
            }
            else if (!this.advised.opaque && method.getDeclaringClass().isInterface() &&
                    method.getDeclaringClass().isAssignableFrom(Advised.class)) {
                // 使用代理配置对ProxyConfig进行服务调用
                return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
            }

            Object retVal;

            if (this.advised.exposeProxy) {
                // 如果有必要,可以援引
                oldProxy = AopContext.setCurrentProxy(proxy);
                setProxyContext = true;
            }

            // 获取目标对象, 为目标方法的调用做准备
            target = targetSource.getTarget();
            Class<?> targetClass = (target != null ? target.getClass() : null);

            // 获取定义好的拦截器链, 即Advisor列表
            List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

            // 如果没有配置拦截器, 就直接通过反射调用目标对象target的method对象,并获取返回值
            if (chain.isEmpty()) {
                Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
                retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
            }
            else {
                // 如果有拦截器链, 则需要先调用拦截器链中的拦截器,再调用目标的对应方法, 这里通过构造ReflectiveMethodInvocation来实现
                MethodInvocation invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
                retVal = invocation.proceed();
            }

            // 获取method返回值的类型
            Class<?> returnType = method.getReturnType();
            if (retVal != null && retVal == target &&
                    returnType != Object.class && returnType.isInstance(proxy) &&
                    !RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
                // 特殊提醒: 它返回“this”,方法的返回类型与类型兼容
                // 注意: 如果target在另一个返回的对象中设置了对自身的引用，Spring 将无法处理
                retVal = proxy;
            }
            else if (retVal == null && returnType != void.class && returnType.isPrimitive()) {
                throw new AopInvocationException("Null return value from advice does not match primitive return type for: " + method);
            }
            /*if (coroutinesReactorPresent && KotlinDetector.isSuspendingFunction(method)) {
                return COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName()) ?
                        CoroutinesUtils.asFlow(retVal) : CoroutinesUtils.awaitSingleOrNull(retVal, args[args.length - 1]);
            }*/
            return retVal;
        }
        finally {
            if (target != null && !targetSource.isStatic()) {
                // 必须来自TargetSource
                targetSource.releaseTarget(target);
            }
            if (setProxyContext) {
                // 存储旧的proxy
                AopContext.setCurrentProxy(oldProxy);
            }
        }
    }

    @Override
    public boolean equals( Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }

        JdkDynamicAopProxy otherProxy;
        if (other instanceof JdkDynamicAopProxy jdkDynamicAopProxy) {
            otherProxy = jdkDynamicAopProxy;
        }
        else if (Proxy.isProxyClass(other.getClass())) {
            InvocationHandler ih = Proxy.getInvocationHandler(other);
            if (!(ih instanceof JdkDynamicAopProxy jdkDynamicAopProxy)) {
                return false;
            }
            otherProxy = jdkDynamicAopProxy;
        }
        else {
            // Not a valid comparison...
            return false;
        }

        // If we get here, otherProxy is the other AopProxy.
        return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
    }
    @Override
    public int hashCode() {
        return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
    }


    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Rely on default serialization; just initialize state after deserialization.
        ois.defaultReadObject();

        // Initialize transient fields.
        this.cache = new ProxiedInterfacesCache(this.advised);
    }

    private static final class ProxiedInterfacesCache {

        final Class<?>[] proxiedInterfaces;

        final boolean equalsDefined;

        final boolean hashCodeDefined;

        ProxiedInterfacesCache(AdvisedSupport config) {
            this.proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(config, true);

            // Find any {@link #equals} or {@link #hashCode} method that may be defined
            // on the supplied set of interfaces.
            boolean equalsDefined = false;
            boolean hashCodeDefined = false;
            for (Class<?> proxiedInterface : this.proxiedInterfaces) {
                Method[] methods = proxiedInterface.getDeclaredMethods();
                for (Method method : methods) {
                    if (AopUtils.isEqualsMethod(method)) {
                        equalsDefined = true;
                        if (hashCodeDefined) {
                            break;
                        }
                    }
                    if (AopUtils.isHashCodeMethod(method)) {
                        hashCodeDefined = true;
                        if (equalsDefined) {
                            break;
                        }
                    }
                }
                if (equalsDefined && hashCodeDefined) {
                    break;
                }
            }
            this.equalsDefined = equalsDefined;
            this.hashCodeDefined = hashCodeDefined;
        }
    }

}
