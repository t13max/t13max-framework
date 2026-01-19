package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.TargetSource;
import com.t13max.ioc.aop.intecept.MethodInterceptor;
import com.t13max.ioc.aop.intecept.MethodInvocation;
import com.t13max.ioc.aop.support.AopUtils;
import com.t13max.ioc.core.SmartClassLoader;
import com.t13max.ioc.util.*;
import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.proxy.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.security.auth.callback.Callback;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;

/**
 * @author t13max
 * @since 16:51 2026/1/16
 */
public class CglibAopProxy implements AopProxy, Serializable {

    // Constants for CGLIB callback array indices
    private static final int AOP_PROXY = 0;
    private static final int INVOKE_TARGET = 1;
    private static final int NO_OVERRIDE = 2;
    private static final int DISPATCH_TARGET = 3;
    private static final int DISPATCH_ADVISED = 4;
    private static final int INVOKE_EQUALS = 5;
    private static final int INVOKE_HASHCODE = 6;


    private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";

    private static final boolean coroutinesReactorPresent = ClassUtils.isPresent("kotlinx.coroutines.reactor.MonoKt", CglibAopProxy.class.getClassLoader());
    protected static final Logger logger = LogManager.getLogger(CglibAopProxy.class);
    private static final Map<Class<?>, Boolean> validatedClasses = new WeakHashMap<>();

    protected final AdvisedSupport advised;
    protected Object[] constructorArgs;
    protected Class<?>[] constructorArgTypes;
    private final transient AdvisedDispatcher advisedDispatcher;

    private transient Map<Method, Integer> fixedInterceptorMap = Collections.emptyMap();

    private transient int fixedInterceptorOffset;

    public CglibAopProxy(AdvisedSupport config) throws AopConfigException {
        Assert.notNull(config, "AdvisedSupport must not be null");
        this.advised = config;
        this.advisedDispatcher = new AdvisedDispatcher(this.advised);
    }
    public void setConstructorArguments( Object[] constructorArgs,  Class<?>[] constructorArgTypes) {
        if (constructorArgs == null || constructorArgTypes == null) {
            throw new IllegalArgumentException("Both 'constructorArgs' and 'constructorArgTypes' need to be specified");
        }
        if (constructorArgs.length != constructorArgTypes.length) {
            throw new IllegalArgumentException("Number of 'constructorArgs' (" + constructorArgs.length + ") must match number of 'constructorArgTypes' (" + constructorArgTypes.length + ")");
        }
        this.constructorArgs = constructorArgs;
        this.constructorArgTypes = constructorArgTypes;
    }


    @Override
    public Object getProxy() {
        return buildProxy(null, false);
    }

    @Override
    public Object getProxy( ClassLoader classLoader) {
        return buildProxy(classLoader, false);
    }

    @Override
    public Class<?> getProxyClass( ClassLoader classLoader) {
        return (Class<?>) buildProxy(classLoader, true);
    }

    private Object buildProxy( ClassLoader classLoader, boolean classOnly) {
        if (logger.isTraceEnabled()) {
            logger.trace("Creating CGLIB proxy: {}", this.advised.getTargetSource());
        }

        try {
            Class<?> rootClass = this.advised.getTargetClass();
            Assert.state(rootClass != null, "Target class must be available for creating a CGLIB proxy");

            Class<?> proxySuperClass = rootClass;
            if (rootClass.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
                proxySuperClass = rootClass.getSuperclass();
                Class<?>[] additionalInterfaces = rootClass.getInterfaces();
                for (Class<?> additionalInterface : additionalInterfaces) {
                    this.advised.addInterface(additionalInterface);
                }
            }

            // Validate the class, writing log messages as necessary.
            validateClassIfNecessary(proxySuperClass, classLoader);

            // 创建并配置Enhancer对象
            Enhancer enhancer = createEnhancer();
            if (classLoader != null) {
                enhancer.setClassLoader(classLoader);
                if (classLoader instanceof SmartClassLoader smartClassLoader && smartClassLoader.isClassReloadable(proxySuperClass)) {
                    enhancer.setUseCache(false);
                }
            }
            enhancer.setSuperclass(proxySuperClass);
            enhancer.setInterfaces(AopProxyUtils.completeProxiedInterfaces(this.advised));
            enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
            enhancer.setAttemptLoad(enhancer.getUseCache() && AotDetector.useGeneratedArtifacts());
            enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(classLoader));

            Callback[] callbacks = getCallbacks(rootClass);
            Class<?>[] types = new Class<?>[callbacks.length];
            for (int x = 0; x < types.length; x++) {
                types[x] = callbacks[x].getClass();
            }
            // fixedInterceptorMap only populated at this point, after getCallbacks call above
            ProxyCallbackFilter filter = new ProxyCallbackFilter(this.advised.getConfigurationOnlyCopy(), this.fixedInterceptorMap, this.fixedInterceptorOffset);
            enhancer.setCallbackFilter(filter);
            enhancer.setCallbackTypes(types);

            // Generate the proxy class and create a proxy instance.
            // ProxyCallbackFilter has method introspection capability with Advisor access.
            try {
                return (classOnly ? createProxyClass(enhancer) : createProxyClassAndInstance(enhancer, callbacks));
            }
            finally {
                // Reduce ProxyCallbackFilter to key-only state for its class cache role
                // in the CGLIB$CALLBACK_FILTER field, not leaking any Advisor state...
                filter.advised.reduceToAdvisorKey();
            }
        }
        catch (CodeGenerationException | IllegalArgumentException ex) {
            throw new AopConfigException("Could not generate CGLIB subclass of " + this.advised.getTargetClass() + ": Common causes of this problem include using a final class or a non-visible class", ex);
        }
        catch (Throwable ex) {
            // TargetSource.getTarget() failed
            throw new AopConfigException("Unexpected AOP exception", ex);
        }
    }

    protected Class<?> createProxyClass(Enhancer enhancer) {
        enhancer.setInterceptDuringConstruction(false);
        return enhancer.createClass();
    }

    protected Object createProxyClassAndInstance(Enhancer enhancer, Callback[] callbacks) {
        enhancer.setInterceptDuringConstruction(false);
        enhancer.setCallbacks(callbacks);
        return (this.constructorArgs != null && this.constructorArgTypes != null ?
                enhancer.create(this.constructorArgTypes, this.constructorArgs) :
                enhancer.create());
    }
    protected Enhancer createEnhancer() {
        return new Enhancer();
    }
    private void validateClassIfNecessary(Class<?> proxySuperClass,  ClassLoader proxyClassLoader) {
        if (!this.advised.isOptimize() && logger.isInfoEnabled()) {
            synchronized (validatedClasses) {
                validatedClasses.computeIfAbsent(proxySuperClass, clazz -> {
                    doValidateClass(clazz, proxyClassLoader, ClassUtils.getAllInterfacesForClassAsSet(clazz));
                    return Boolean.TRUE;
                });
            }
        }
    }
    private void doValidateClass(Class<?> proxySuperClass,  ClassLoader proxyClassLoader, Set<Class<?>> ifcs) {
        if (proxySuperClass != Object.class) {
            Method[] methods = proxySuperClass.getDeclaredMethods();
            for (Method method : methods) {
                int mod = method.getModifiers();
                if (!Modifier.isStatic(mod) && !Modifier.isPrivate(mod)) {
                    if (Modifier.isFinal(mod)) {
                        if (logger.isWarnEnabled() && implementsInterface(method, ifcs)) {
                            logger.warn("Unable to proxy interface-implementing method [" + method + "] because " +
                                    "it is marked as final, consider using interface-based JDK proxies instead.");
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug("Final method [" + method + "] cannot get proxied via CGLIB: " +
                                    "Calls to this method will NOT be routed to the target instance and " +
                                    "might lead to NPEs against uninitialized fields in the proxy instance.");
                        }
                    }
                    else if (logger.isDebugEnabled() && !Modifier.isPublic(mod) && !Modifier.isProtected(mod) &&
                            proxyClassLoader != null && proxySuperClass.getClassLoader() != proxyClassLoader) {
                        logger.debug("Method [" + method + "] is package-visible across different ClassLoaders " +
                                "and cannot get proxied via CGLIB: Declare this method as public or protected " +
                                "if you need to support invocations through the proxy.");
                    }
                }
            }
            doValidateClass(proxySuperClass.getSuperclass(), proxyClassLoader, ifcs);
        }
    }

    private Callback[] getCallbacks(Class<?> rootClass) throws Exception {
        // Parameters used for optimization choices...
        boolean isStatic = this.advised.getTargetSource().isStatic();
        boolean isFrozen = this.advised.isFrozen();
        boolean exposeProxy = this.advised.isExposeProxy();

        // Choose an "aop" interceptor (used for AOP calls).
        Callback aopInterceptor = new DynamicAdvisedInterceptor(this.advised);

        // Choose a "straight to target" interceptor. (used for calls that are
        // unadvised but can return this). May be required to expose the proxy.
        Callback targetInterceptor;
        if (exposeProxy) {
            targetInterceptor = (isStatic ?
                    new StaticUnadvisedExposedInterceptor(this.advised.getTargetSource().getTarget()) :
                    new DynamicUnadvisedExposedInterceptor(this.advised.getTargetSource()));
        }
        else {
            targetInterceptor = (isStatic ?
                    new StaticUnadvisedInterceptor(this.advised.getTargetSource().getTarget()) :
                    new DynamicUnadvisedInterceptor(this.advised.getTargetSource()));
        }

        // Choose a "direct to target" dispatcher (used for
        // unadvised calls to static targets that cannot return this).
        Callback targetDispatcher = (isStatic ?
                new StaticDispatcher(this.advised.getTargetSource().getTarget()) : new SerializableNoOp());

        Callback[] mainCallbacks = new Callback[] {
                aopInterceptor,  // for normal advice
                targetInterceptor,  // invoke target without considering advice, if optimized
                new SerializableNoOp(),  // no override for methods mapped to this
                targetDispatcher, this.advisedDispatcher,
                new EqualsInterceptor(this.advised),
                new HashCodeInterceptor(this.advised)
        };

        // If the target is a static one and the advice chain is frozen,
        // then we can make some optimizations by sending the AOP calls
        // direct to the target using the fixed chain for that method.
        if (isStatic && isFrozen) {
            Method[] methods = rootClass.getMethods();
            int methodsCount = methods.length;
            List<Callback> fixedCallbacks = new ArrayList<>(methodsCount);
            this.fixedInterceptorMap = CollectionUtils.newHashMap(methodsCount);

            int advicedMethodCount = methodsCount;
            for (int x = 0; x < methodsCount; x++) {
                Method method = methods[x];
                //do not create advices for non-overridden methods of java.lang.Object
                if (method.getDeclaringClass() == Object.class) {
                    advicedMethodCount--;
                    continue;
                }
                List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, rootClass);
                fixedCallbacks.add(new FixedChainStaticTargetInterceptor(
                        chain, this.advised.getTargetSource().getTarget(), this.advised.getTargetClass()));
                this.fixedInterceptorMap.put(method, x - (methodsCount - advicedMethodCount) );
            }

            // Now copy both the callbacks from mainCallbacks
            // and fixedCallbacks into the callbacks array.
            Callback[] callbacks = new Callback[mainCallbacks.length + advicedMethodCount];
            System.arraycopy(mainCallbacks, 0, callbacks, 0, mainCallbacks.length);
            System.arraycopy(fixedCallbacks.toArray(Callback[]::new), 0, callbacks,
                    mainCallbacks.length, advicedMethodCount);
            this.fixedInterceptorOffset = mainCallbacks.length;
            return callbacks;
        }
        return mainCallbacks;
    }


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof CglibAopProxy that &&
                AopProxyUtils.equalsInProxy(this.advised, that.advised)));
    }

    @Override
    public int hashCode() {
        return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
    }

    private static boolean implementsInterface(Method method, Set<Class<?>> ifcs) {
        for (Class<?> ifc : ifcs) {
            if (ClassUtils.hasMethod(ifc, method)) {
                return true;
            }
        }
        return false;
    }    
    private static Object processReturnType(
            Object proxy,  Object target, Method method, Object[] arguments,  Object returnValue) {

        // Massage return value if necessary
        if (returnValue != null && returnValue == target &&
                !RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
            // Special case: it returned "this". Note that we can't help
            // if the target sets a reference to itself in another returned object.
            returnValue = proxy;
        }
        Class<?> returnType = method.getReturnType();
        if (returnValue == null && returnType != void.class && returnType.isPrimitive()) {
            throw new AopInvocationException(
                    "Null return value from advice does not match primitive return type for: " + method);
        }
        if (coroutinesReactorPresent && KotlinDetector.isSuspendingFunction(method)) {
            return COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName()) ?
                    CoroutinesUtils.asFlow(returnValue) :
                    CoroutinesUtils.awaitSingleOrNull(returnValue, arguments[arguments.length - 1]);
        }
        return returnValue;
    }

    public static class SerializableNoOp implements NoOp, Serializable {
    }

    private static class StaticUnadvisedInterceptor implements MethodInterceptor, Serializable {

        
        private final Object target;

        public StaticUnadvisedInterceptor( Object target) {
            this.target = target;
        }

        @Override
        
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Object retVal = AopUtils.invokeJoinpointUsingReflection(this.target, method, args);
            return processReturnType(proxy, this.target, method, args, retVal);
        }
    }

    private static class StaticUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {

        
        private final Object target;

        public StaticUnadvisedExposedInterceptor( Object target) {
            this.target = target;
        }

        @Override
        
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Object oldProxy = null;
            try {
                oldProxy = AopContext.setCurrentProxy(proxy);
                Object retVal = AopUtils.invokeJoinpointUsingReflection(this.target, method, args);
                return processReturnType(proxy, this.target, method, args, retVal);
            }
            finally {
                AopContext.setCurrentProxy(oldProxy);
            }
        }
    }

    private static class DynamicUnadvisedInterceptor implements MethodInterceptor, Serializable {

        private final TargetSource targetSource;

        public DynamicUnadvisedInterceptor(TargetSource targetSource) {
            this.targetSource = targetSource;
        }

        @Override
        
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Object target = this.targetSource.getTarget();
            try {
                Object retVal = AopUtils.invokeJoinpointUsingReflection(target, method, args);
                return processReturnType(proxy, target, method, args, retVal);
            }
            finally {
                if (target != null) {
                    this.targetSource.releaseTarget(target);
                }
            }
        }
    }

    private static class DynamicUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {

        private final TargetSource targetSource;

        public DynamicUnadvisedExposedInterceptor(TargetSource targetSource) {
            this.targetSource = targetSource;
        }

        @Override
        
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Object oldProxy = null;
            Object target = this.targetSource.getTarget();
            try {
                oldProxy = AopContext.setCurrentProxy(proxy);
                Object retVal = AopUtils.invokeJoinpointUsingReflection(target, method, args);
                return processReturnType(proxy, target, method, args, retVal);
            }
            finally {
                AopContext.setCurrentProxy(oldProxy);
                if (target != null) {
                    this.targetSource.releaseTarget(target);
                }
            }
        }
    }

    private static class StaticDispatcher implements Dispatcher, Serializable {

        
        private final Object target;

        public StaticDispatcher( Object target) {
            this.target = target;
        }

        @Override
        
        public Object loadObject() {
            return this.target;
        }
    }

    private static class AdvisedDispatcher implements Dispatcher, Serializable {

        private final AdvisedSupport advised;

        public AdvisedDispatcher(AdvisedSupport advised) {
            this.advised = advised;
        }

        @Override
        public Object loadObject() {
            return this.advised;
        }
    }

    private static class EqualsInterceptor implements MethodInterceptor, Serializable {

        private final AdvisedSupport advised;

        public EqualsInterceptor(AdvisedSupport advised) {
            this.advised = advised;
        }

        @Override
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
            Object other = args[0];
            if (proxy == other) {
                return true;
            }
            if (other instanceof Factory factory) {
                Callback callback = factory.getCallback(INVOKE_EQUALS);
                return (callback instanceof EqualsInterceptor that &&
                        AopProxyUtils.equalsInProxy(this.advised, that.advised));
            }
            return false;
        }
    }

    private static class HashCodeInterceptor implements MethodInterceptor, Serializable {

        private final AdvisedSupport advised;

        public HashCodeInterceptor(AdvisedSupport advised) {
            this.advised = advised;
        }

        @Override
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
            return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
        }
    }

    private static class FixedChainStaticTargetInterceptor implements MethodInterceptor, Serializable {

        private final List<Object> adviceChain;

        
        private final Object target;

        
        private final Class<?> targetClass;

        public FixedChainStaticTargetInterceptor(
                List<Object> adviceChain,  Object target,  Class<?> targetClass) {

            this.adviceChain = adviceChain;
            this.target = target;
            this.targetClass = targetClass;
        }

        @Override
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            MethodInvocation invocation = new CglibMethodInvocation(proxy, this.target, method, args, this.targetClass, this.adviceChain, methodProxy);
            // If we get here, we need to create a MethodInvocation.
            Object retVal = invocation.proceed();
            retVal = processReturnType(proxy, this.target, method, args, retVal);
            return retVal;
        }
    }

    private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {

        private final AdvisedSupport advised;

        public DynamicAdvisedInterceptor(AdvisedSupport advised) {
            this.advised = advised;
        }

        @Override
        
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Object oldProxy = null;
            boolean setProxyContext = false;
            Object target = null;
            TargetSource targetSource = this.advised.getTargetSource();
            try {
                if (this.advised.exposeProxy) {
                    // Make invocation available if necessary.
                    oldProxy = AopContext.setCurrentProxy(proxy);
                    setProxyContext = true;
                }
                // Get as late as possible to minimize the time we "own" the target, in case it comes from a pool...
                target = targetSource.getTarget();
                Class<?> targetClass = (target != null ? target.getClass() : null);
                List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
                Object retVal;
                // Check whether we only have one InvokerInterceptor: that is,
                // no real advice, but just reflective invocation of the target.
                if (chain.isEmpty()) {
                    // We can skip creating a MethodInvocation: just invoke the target directly.
                    // Note that the final invoker must be an InvokerInterceptor, so we know
                    // it does nothing but a reflective operation on the target, and no hot
                    // swapping or fancy proxying.
                    Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
                    retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
                }
                else {
                    // We need to create a method invocation...
                    retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
                }
                return processReturnType(proxy, target, method, args, retVal);
            }
            finally {
                if (target != null && !targetSource.isStatic()) {
                    targetSource.releaseTarget(target);
                }
                if (setProxyContext) {
                    // Restore old proxy.
                    AopContext.setCurrentProxy(oldProxy);
                }
            }
        }

        @Override
        public boolean equals( Object other) {
            return (this == other ||
                    (other instanceof DynamicAdvisedInterceptor dynamicAdvisedInterceptor &&
                            this.advised.equals(dynamicAdvisedInterceptor.advised)));
        }

        
        @Override
        public int hashCode() {
            return this.advised.hashCode();
        }
    }

    private static class CglibMethodInvocation extends ReflectiveMethodInvocation {

        public CglibMethodInvocation(Object proxy,  Object target, Method method, Object[] arguments,  Class<?> targetClass, List<Object> interceptorsAndDynamicMethodMatchers, MethodProxy methodProxy) {
            super(proxy, target, method, arguments, targetClass, interceptorsAndDynamicMethodMatchers);
        }

        @Override
        
        public Object proceed() throws Throwable {
            try {
                return super.proceed();
            }
            catch (RuntimeException ex) {
                throw ex;
            }
            catch (Exception ex) {
                if (ReflectionUtils.declaresException(getMethod(), ex.getClass()) ||
                        KotlinDetector.isKotlinType(getMethod().getDeclaringClass())) {
                    // Propagate original exception if declared on the target method
                    // (with callers expecting it). Always propagate it for Kotlin code
                    // since checked exceptions do not have to be explicitly declared there.
                    throw ex;
                }
                else {
                    // Checked exception thrown in the interceptor but not declared on the
                    // target method signature -> apply an UndeclaredThrowableException,
                    // aligned with standard JDK dynamic proxy behavior.
                    throw new UndeclaredThrowableException(ex);
                }
            }
        }
    }

    private static class ProxyCallbackFilter implements CallbackFilter {

        final AdvisedSupport advised;

        private final Map<Method, Integer> fixedInterceptorMap;

        private final int fixedInterceptorOffset;

        public ProxyCallbackFilter(
                AdvisedSupport advised, Map<Method, Integer> fixedInterceptorMap, int fixedInterceptorOffset) {

            this.advised = advised;
            this.fixedInterceptorMap = fixedInterceptorMap;
            this.fixedInterceptorOffset = fixedInterceptorOffset;
        }

        
        @Override
        public int accept(Method method) {
            if (AopUtils.isFinalizeMethod(method)) {
                logger.trace("Found finalize() method - using NO_OVERRIDE");
                return NO_OVERRIDE;
            }
            if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() &&
                    method.getDeclaringClass().isAssignableFrom(Advised.class)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Method is declared on Advised interface: " + method);
                }
                return DISPATCH_ADVISED;
            }
            // We must always proxy equals, to direct calls to this.
            if (AopUtils.isEqualsMethod(method)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Found 'equals' method: " + method);
                }
                return INVOKE_EQUALS;
            }
            // We must always calculate hashCode based on the proxy.
            if (AopUtils.isHashCodeMethod(method)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Found 'hashCode' method: " + method);
                }
                return INVOKE_HASHCODE;
            }
            Class<?> targetClass = this.advised.getTargetClass();
            // Proxy is not yet available, but that shouldn't matter.
            List<?> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
            boolean haveAdvice = !chain.isEmpty();
            boolean isStatic = this.advised.getTargetSource().isStatic();
            boolean isFrozen = this.advised.isFrozen();
            boolean exposeProxy = this.advised.isExposeProxy();
            if (haveAdvice || !isFrozen) {
                // If exposing the proxy, then AOP_PROXY must be used.
                if (exposeProxy) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Must expose proxy on advised method: " + method);
                    }
                    return AOP_PROXY;
                }
                // Check to see if we have fixed interceptor to serve this method.
                // Else use the AOP_PROXY.
                if (isStatic && isFrozen && this.fixedInterceptorMap.containsKey(method)) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Method has advice and optimizations are enabled: " + method);
                    }
                    // We know that we are optimizing so we can use the FixedStaticChainInterceptors.
                    int index = this.fixedInterceptorMap.get(method);
                    return (index + this.fixedInterceptorOffset);
                }
                else {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Unable to apply any optimizations to advised method: " + method);
                    }
                    return AOP_PROXY;
                }
            }
            else {
                // See if the return type of the method is outside the class hierarchy of the target type.
                // If so we know it never needs to have return type massage and can use a dispatcher.
                // If the proxy is being exposed, then must use the interceptor the correct one is already
                // configured. If the target is not static, then we cannot use a dispatcher because the
                // target needs to be explicitly released after the invocation.
                if (exposeProxy || !isStatic) {
                    return INVOKE_TARGET;
                }
                Class<?> returnType = method.getReturnType();
                if (targetClass != null && returnType.isAssignableFrom(targetClass)) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Method return type is assignable from target type and " +
                                "may therefore return 'this' - using INVOKE_TARGET: " + method);
                    }
                    return INVOKE_TARGET;
                }
                else {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Method return type ensures 'this' cannot be returned - " +
                                "using DISPATCH_TARGET: " + method);
                    }
                    return DISPATCH_TARGET;
                }
            }
        }

        @Override
        public boolean equals( Object other) {
            return (this == other || (other instanceof ProxyCallbackFilter that &&
                    this.advised.getAdvisorKey().equals(that.advised.getAdvisorKey()) &&
                    AopProxyUtils.equalsProxiedInterfaces(this.advised, that.advised) &&
                    ObjectUtils.nullSafeEquals(this.advised.getTargetClass(), that.advised.getTargetClass()) &&
                    this.advised.getTargetSource().isStatic() == that.advised.getTargetSource().isStatic() &&
                    this.advised.isFrozen() == that.advised.isFrozen() &&
                    this.advised.isExposeProxy() == that.advised.isExposeProxy() &&
                    this.advised.isOpaque() == that.advised.isOpaque()));
        }

        @Override
        public int hashCode() {
            return this.advised.getAdvisorKey().hashCode();
        }
    }

}
