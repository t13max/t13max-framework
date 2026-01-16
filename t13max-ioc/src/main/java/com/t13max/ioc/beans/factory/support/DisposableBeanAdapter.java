package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.DisposableBean;
import com.t13max.ioc.utils.*;

import java.beans.MethodDescriptor;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @Author: t13max
 * @Since: 21:00 2026/1/16
 */
public class DisposableBeanAdapter implements DisposableBean, Runnable, Serializable {

    private static final String DESTROY_METHOD_NAME = "destroy";

    private static final String CLOSE_METHOD_NAME = "close";

    private static final String SHUTDOWN_METHOD_NAME = "shutdown";


    private static final Logger logger = LogManager.getLogger(DisposableBeanAdapter.class);

    private static final boolean reactiveStreamsPresent = ClassUtils.isPresent(
            "org.reactivestreams.Publisher", DisposableBeanAdapter.class.getClassLoader());


    private final Object bean;

    private final String beanName;

    private final boolean nonPublicAccessAllowed;

    private final boolean invokeDisposableBean;

    private boolean invokeAutoCloseable;

    private String  [] destroyMethodNames;

    private transient Method  [] destroyMethods;

    private final  List<DestructionAwareBeanPostProcessor> beanPostProcessors;

    public DisposableBeanAdapter(Object bean, String beanName, RootBeanDefinition beanDefinition,
                                 List<DestructionAwareBeanPostProcessor> postProcessors) {

        Assert.notNull(bean, "Disposable bean must not be null");
        this.bean = bean;
        this.beanName = beanName;
        this.nonPublicAccessAllowed = beanDefinition.isNonPublicAccessAllowed();
        this.invokeDisposableBean = (bean instanceof DisposableBean &&
                !beanDefinition.hasAnyExternallyManagedDestroyMethod(DESTROY_METHOD_NAME));

        String[] destroyMethodNames = inferDestroyMethodsIfNecessary(bean.getClass(), beanDefinition);
        if (!ObjectUtils.isEmpty(destroyMethodNames) &&
                !(this.invokeDisposableBean && DESTROY_METHOD_NAME.equals(destroyMethodNames[0])) &&
                !beanDefinition.hasAnyExternallyManagedDestroyMethod(destroyMethodNames[0])) {

            this.invokeAutoCloseable =
                    (bean instanceof AutoCloseable && CLOSE_METHOD_NAME.equals(destroyMethodNames[0]));
            if (!this.invokeAutoCloseable) {
                this.destroyMethodNames = destroyMethodNames;
                List<Method> destroyMethods = new ArrayList<>(destroyMethodNames.length);
                for (String destroyMethodName : destroyMethodNames) {
                    Method destroyMethod = determineDestroyMethod(destroyMethodName);
                    if (destroyMethod == null) {
                        if (beanDefinition.isEnforceDestroyMethod()) {
                            throw new BeanDefinitionValidationException("Could not find a destroy method named '" +
                                    destroyMethodName + "' on bean with name '" + beanName + "'");
                        }
                    }
                    else {
                        if (destroyMethod.getParameterCount() > 0) {
                            Class<?>[] paramTypes = destroyMethod.getParameterTypes();
                            if (paramTypes.length > 1) {
                                throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
                                        beanName + "' has more than one parameter - not supported as destroy method");
                            }
                            else if (paramTypes.length == 1 && boolean.class != paramTypes[0]) {
                                throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
                                        beanName + "' has a non-boolean parameter - not supported as destroy method");
                            }
                        }
                        destroyMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(destroyMethod, bean.getClass());
                        destroyMethods.add(destroyMethod);
                    }
                }
                this.destroyMethods = destroyMethods.toArray(Method[]::new);
            }
        }

        this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
    }
    public DisposableBeanAdapter(Object bean, List<DestructionAwareBeanPostProcessor> postProcessors) {
        Assert.notNull(bean, "Disposable bean must not be null");
        this.bean = bean;
        this.beanName = bean.getClass().getName();
        this.nonPublicAccessAllowed = true;
        this.invokeDisposableBean = (this.bean instanceof DisposableBean);
        this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
    }
    private DisposableBeanAdapter(Object bean, String beanName, boolean nonPublicAccessAllowed,
                                  boolean invokeDisposableBean, boolean invokeAutoCloseable, String  [] destroyMethodNames,
                                   List<DestructionAwareBeanPostProcessor> postProcessors) {

        this.bean = bean;
        this.beanName = beanName;
        this.nonPublicAccessAllowed = nonPublicAccessAllowed;
        this.invokeDisposableBean = invokeDisposableBean;
        this.invokeAutoCloseable = invokeAutoCloseable;
        this.destroyMethodNames = destroyMethodNames;
        this.beanPostProcessors = postProcessors;
    }


    @Override
    public void run() {
        destroy();
    }

    @Override
    public void destroy() {
        if (!CollectionUtils.isEmpty(this.beanPostProcessors)) {
            for (DestructionAwareBeanPostProcessor processor : this.beanPostProcessors) {
                processor.postProcessBeforeDestruction(this.bean, this.beanName);
            }
        }

        if (this.invokeDisposableBean) {
            if (logger.isTraceEnabled()) {
                logger.trace("Invoking destroy() on bean with name '" + this.beanName + "'");
            }
            try {
                ((DisposableBean) this.bean).destroy();
            }
            catch (Throwable ex) {
                if (logger.isWarnEnabled()) {
                    String msg = "Invocation of destroy method failed on bean with name '" + this.beanName + "'";
                    if (logger.isDebugEnabled()) {
                        // Log at warn level like below but add the exception stacktrace only with debug level
                        logger.warn(msg, ex);
                    }
                    else {
                        logger.warn(msg + ": " + ex);
                    }
                }
            }
        }

        if (this.invokeAutoCloseable) {
            if (logger.isTraceEnabled()) {
                logger.trace("Invoking close() on bean with name '" + this.beanName + "'");
            }
            try {
                ((AutoCloseable) this.bean).close();
            }
            catch (Throwable ex) {
                if (logger.isWarnEnabled()) {
                    String msg = "Invocation of close method failed on bean with name '" + this.beanName + "'";
                    if (logger.isDebugEnabled()) {
                        // Log at warn level like below but add the exception stacktrace only with debug level
                        logger.warn(msg, ex);
                    }
                    else {
                        logger.warn(msg + ": " + ex);
                    }
                }
            }
        }
        else if (this.destroyMethods != null) {
            for (Method destroyMethod : this.destroyMethods) {
                invokeCustomDestroyMethod(destroyMethod);
            }
        }
        else if (this.destroyMethodNames != null) {
            for (String destroyMethodName : this.destroyMethodNames) {
                Method destroyMethod = determineDestroyMethod(destroyMethodName);
                if (destroyMethod != null) {
                    destroyMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(destroyMethod, this.bean.getClass());
                    invokeCustomDestroyMethod(destroyMethod);
                }
            }
        }
    }


    private  Method determineDestroyMethod(String destroyMethodName) {
        try {
            Class<?> beanClass = this.bean.getClass();
            MethodDescriptor descriptor = MethodDescriptor.create(this.beanName, beanClass, destroyMethodName);
            String methodName = descriptor.methodName();

            Method destroyMethod = findDestroyMethod(descriptor.declaringClass(), methodName);
            if (destroyMethod != null) {
                return destroyMethod;
            }
            for (Class<?> beanInterface : ClassUtils.getAllInterfacesForClass(beanClass)) {
                destroyMethod = findDestroyMethod(beanInterface, methodName);
                if (destroyMethod != null) {
                    return destroyMethod;
                }
            }
            return null;
        }
        catch (IllegalArgumentException ex) {
            throw new BeanDefinitionValidationException("Could not find unique destroy method on bean with name '" +
                    this.beanName + ": " + ex.getMessage());
        }
    }

    private  Method findDestroyMethod(Class<?> clazz, String name) {
        return (this.nonPublicAccessAllowed ?
                BeanUtils.findMethodWithMinimalParameters(clazz, name) :
                BeanUtils.findMethodWithMinimalParameters(clazz.getMethods(), name));
    }
    private void invokeCustomDestroyMethod(Method destroyMethod) {
        if (logger.isTraceEnabled()) {
            logger.trace("Invoking custom destroy method '" + destroyMethod.getName() +
                    "' on bean with name '" + this.beanName + "': " + destroyMethod);
        }

        int paramCount = destroyMethod.getParameterCount();
        Object[] args = new Object[paramCount];
        if (paramCount == 1) {
            args[0] = Boolean.TRUE;
        }

        try {
            ReflectionUtils.makeAccessible(destroyMethod);
            Object returnValue = destroyMethod.invoke(this.bean, args);

            if (returnValue == null) {
                // Regular case: a void method
                logDestroyMethodCompletion(destroyMethod, false);
            }
            else if (returnValue instanceof Future<?> future) {
                // An async task: await its completion.
                future.get();
                logDestroyMethodCompletion(destroyMethod, true);
            }
            else if (!reactiveStreamsPresent || !new ReactiveDestroyMethodHandler().await(destroyMethod, returnValue)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Unknown return value type from custom destroy method '" + destroyMethod.getName() +
                            "' on bean with name '" + this.beanName + "': " + returnValue.getClass());
                }
            }
        }
        catch (InvocationTargetException | ExecutionException ex) {
            logDestroyMethodException(destroyMethod, ex.getCause());
        }
        catch (Throwable ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to invoke custom destroy method '" + destroyMethod.getName() +
                        "' on bean with name '" + this.beanName + "'", ex);
            }
        }
    }

    void logDestroyMethodException(Method destroyMethod,  Throwable ex) {
        if (logger.isWarnEnabled()) {
            String msg = "Custom destroy method '" + destroyMethod.getName() + "' on bean with name '" +
                    this.beanName + "' propagated an exception";
            if (logger.isDebugEnabled()) {
                // Log at warn level like below but add the exception stacktrace only with debug level
                logger.warn(msg, ex);
            }
            else {
                logger.warn(msg + ": " + ex);
            }
        }
    }

    void logDestroyMethodCompletion(Method destroyMethod, boolean async) {
        if (logger.isDebugEnabled()) {
            logger.debug("Custom destroy method '" + destroyMethod.getName() +
                    "' on bean with name '" + this.beanName + "' completed" + (async ? " asynchronously" : ""));
        }
    }

    protected Object writeReplace() {
        List<DestructionAwareBeanPostProcessor> serializablePostProcessors = null;
        if (this.beanPostProcessors != null) {
            serializablePostProcessors = new ArrayList<>();
            for (DestructionAwareBeanPostProcessor postProcessor : this.beanPostProcessors) {
                if (postProcessor instanceof Serializable) {
                    serializablePostProcessors.add(postProcessor);
                }
            }
        }
        return new DisposableBeanAdapter(
                this.bean, this.beanName, this.nonPublicAccessAllowed, this.invokeDisposableBean,
                this.invokeAutoCloseable, this.destroyMethodNames, serializablePostProcessors);
    }

    public static boolean hasDestroyMethod(Object bean, RootBeanDefinition beanDefinition) {
        return (bean instanceof DisposableBean ||
                inferDestroyMethodsIfNecessary(bean.getClass(), beanDefinition) != null);
    }

    static String  [] inferDestroyMethodsIfNecessary(Class<?> target, RootBeanDefinition beanDefinition) {
        String[] destroyMethodNames = beanDefinition.getDestroyMethodNames();
        if (destroyMethodNames != null && destroyMethodNames.length > 1) {
            return destroyMethodNames;
        }

        String destroyMethodName = beanDefinition.resolvedDestroyMethodName;
        if (destroyMethodName == null) {
            destroyMethodName = beanDefinition.getDestroyMethodName();
            boolean autoCloseable = (AutoCloseable.class.isAssignableFrom(target));
            if (AbstractBeanDefinition.INFER_METHOD.equals(destroyMethodName) ||
                    (destroyMethodName == null && autoCloseable)) {
                // Only perform destroy method inference in case of the bean
                // not explicitly implementing the DisposableBean interface
                destroyMethodName = null;
                if (!(DisposableBean.class.isAssignableFrom(target))) {
                    if (autoCloseable) {
                        destroyMethodName = CLOSE_METHOD_NAME;
                    }
                    else {
                        try {
                            destroyMethodName = target.getMethod(CLOSE_METHOD_NAME).getName();
                        }
                        catch (NoSuchMethodException ex) {
                            try {
                                destroyMethodName = target.getMethod(SHUTDOWN_METHOD_NAME).getName();
                            }
                            catch (NoSuchMethodException ex2) {
                                // no candidate destroy method found
                            }
                        }
                    }
                }
            }
            beanDefinition.resolvedDestroyMethodName = (destroyMethodName != null ? destroyMethodName : "");
        }
        return (StringUtils.hasLength(destroyMethodName) ? new String[] {destroyMethodName} : null);
    }
    public static boolean hasApplicableProcessors(Object bean, List<DestructionAwareBeanPostProcessor> postProcessors) {
        if (!CollectionUtils.isEmpty(postProcessors)) {
            for (DestructionAwareBeanPostProcessor processor : postProcessors) {
                if (processor.requiresDestruction(bean)) {
                    return true;
                }
            }
        }
        return false;
    }
    private static  List<DestructionAwareBeanPostProcessor> filterPostProcessors(
            List<DestructionAwareBeanPostProcessor> processors, Object bean) {

        List<DestructionAwareBeanPostProcessor> filteredPostProcessors = null;
        if (!CollectionUtils.isEmpty(processors)) {
            filteredPostProcessors = new ArrayList<>(processors.size());
            for (DestructionAwareBeanPostProcessor processor : processors) {
                if (processor.requiresDestruction(bean)) {
                    filteredPostProcessors.add(processor);
                }
            }
        }
        return filteredPostProcessors;
    }

    private class ReactiveDestroyMethodHandler {

        public boolean await(Method destroyMethod, Object returnValue) throws InterruptedException {
            ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(returnValue.getClass());
            if (adapter != null) {
                CountDownLatch latch = new CountDownLatch(1);
                adapter.toPublisher(returnValue).subscribe(new DestroyMethodSubscriber(destroyMethod, latch));
                latch.await();
                return true;
            }
            return false;
        }
    }

    private class DestroyMethodSubscriber implements Subscriber<Object> {

        private final Method destroyMethod;

        private final CountDownLatch latch;

        public DestroyMethodSubscriber(Method destroyMethod, CountDownLatch latch) {
            this.destroyMethod = destroyMethod;
            this.latch = latch;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Integer.MAX_VALUE);
        }

        @Override
        public void onNext(Object o) {
        }

        @Override
        public void onError(Throwable t) {
            this.latch.countDown();
            logDestroyMethodException(this.destroyMethod, t);
        }

        @Override
        public void onComplete() {
            this.latch.countDown();
            logDestroyMethodCompletion(this.destroyMethod, true);
        }
    }
}
