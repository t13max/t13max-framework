package com.t13max.ioc.transaction.interceptor;

import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ConcurrentReferenceHashMap;
import com.t13max.ioc.utils.StringUtils;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @Author: t13max
 * @Since: 20:33 2026/1/16
 */
public class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {

    // NOTE: This class must not implement Serializable because it serves as base
    // class for AspectJ aspects (which are not allowed to implement Serializable)!

    private static final Object DEFAULT_TRANSACTION_MANAGER_KEY = new Object();

    private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";
    private static final boolean reactiveStreamsPresent = ClassUtils.isPresent(
            "org.reactivestreams.Publisher", TransactionAspectSupport.class.getClassLoader());
    private static final boolean vavrPresent = ClassUtils.isPresent(
            "io.vavr.control.Try", TransactionAspectSupport.class.getClassLoader());
    private static final ThreadLocal<TransactionInfo> transactionInfoHolder =
            new NamedThreadLocal<>("Current aspect-driven transaction");

    protected static  TransactionInfo currentTransactionInfo() throws NoTransactionException {
        return transactionInfoHolder.get();
    }
    public static TransactionStatus currentTransactionStatus() throws NoTransactionException {
        TransactionInfo info = currentTransactionInfo();
        if (info == null || info.transactionStatus == null) {
            throw new NoTransactionException("No transaction aspect-managed TransactionStatus in scope");
        }
        return info.transactionStatus;
    }


    protected final Logger logger = LogManager.getLogger(getClass());

    private final  ReactiveAdapterRegistry reactiveAdapterRegistry;

    private  String transactionManagerBeanName;

    private  TransactionManager transactionManager;

    private  TransactionAttributeSource transactionAttributeSource;

    private  BeanFactory beanFactory;

    private final ConcurrentMap<Object, TransactionManager> transactionManagerCache =
            new ConcurrentReferenceHashMap<>(4);

    private final ConcurrentMap<Method, ReactiveTransactionSupport> transactionSupportCache =
            new ConcurrentReferenceHashMap<>(1024);


    protected TransactionAspectSupport() {
        if (reactiveStreamsPresent) {
            this.reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
        } else {
            this.reactiveAdapterRegistry = null;
        }
    }

    public void setTransactionManagerBeanName( String transactionManagerBeanName) {
        this.transactionManagerBeanName = transactionManagerBeanName;
    }
    protected final  String getTransactionManagerBeanName() {
        return this.transactionManagerBeanName;
    }
    public void setTransactionManager( TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }
    public  TransactionManager getTransactionManager() {
        return this.transactionManager;
    }
    public void setTransactionAttributes(Properties transactionAttributes) {
        NameMatchTransactionAttributeSource tas = new NameMatchTransactionAttributeSource();
        tas.setProperties(transactionAttributes);
        this.transactionAttributeSource = tas;
    }
    public void setTransactionAttributeSources(TransactionAttributeSource... transactionAttributeSources) {
        this.transactionAttributeSource = new CompositeTransactionAttributeSource(transactionAttributeSources);
    }
    public void setTransactionAttributeSource( TransactionAttributeSource transactionAttributeSource) {
        this.transactionAttributeSource = transactionAttributeSource;
    }
    public  TransactionAttributeSource getTransactionAttributeSource() {
        return this.transactionAttributeSource;
    }
    @Override
    public void setBeanFactory( BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    protected final  BeanFactory getBeanFactory() {
        return this.beanFactory;
    }
    @Override
    public void afterPropertiesSet() {
        if (getTransactionManager() == null && this.beanFactory == null) {
            throw new IllegalStateException(
                    "Set the 'transactionManager' property or make sure to run within a BeanFactory " +
                            "containing a TransactionManager bean!");
        }
        if (getTransactionAttributeSource() == null) {
            throw new IllegalStateException(
                    "Either 'transactionAttributeSource' or 'transactionAttributes' is required: " +
                            "If there are no transactional methods, then don't use a transaction aspect.");
        }
    }

    protected  Object invokeWithinTransaction(Method method,  Class<?> targetClass,
                                                       final InvocationCallback invocation) throws Throwable {

        // If the transaction attribute is null, the method is non-transactional.
        TransactionAttributeSource tas = getTransactionAttributeSource();
        final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
        final TransactionManager tm = determineTransactionManager(txAttr, targetClass);

        if (this.reactiveAdapterRegistry != null && tm instanceof ReactiveTransactionManager rtm) {
            boolean isSuspendingFunction = KotlinDetector.isSuspendingFunction(method);
            boolean hasSuspendingFlowReturnType = isSuspendingFunction &&
                    COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName());

            ReactiveTransactionSupport txSupport = this.transactionSupportCache.computeIfAbsent(method, key -> {
                Class<?> reactiveType =
                        (isSuspendingFunction ? (hasSuspendingFlowReturnType ? Flux.class : Mono.class) : method.getReturnType());
                ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(reactiveType);
                if (adapter == null) {
                    throw new IllegalStateException("Cannot apply reactive transaction to non-reactive return type [" +
                            method.getReturnType() + "] with specified transaction manager: " + tm);
                }
                return new ReactiveTransactionSupport(adapter);
            });

            return txSupport.invokeWithinTransaction(method, targetClass, invocation, txAttr, rtm);
        }

        PlatformTransactionManager ptm = asPlatformTransactionManager(tm);
        final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

        if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager cpptm)) {
            // Standard transaction demarcation with getTransaction and commit/rollback calls.
            TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);

            Object retVal;
            try {
                // This is an around advice: Invoke the next interceptor in the chain.
                // This will normally result in a target object being invoked.
                retVal = invocation.proceedWithInvocation();
            } catch (Throwable ex) {
                // target invocation exception
                completeTransactionAfterThrowing(txInfo, ex);
                throw ex;
            } finally {
                cleanupTransactionInfo(txInfo);
            }

            if (retVal != null && txAttr != null) {
                TransactionStatus status = txInfo.getTransactionStatus();
                if (status != null) {
                    if (retVal instanceof Future<?> future && future.isDone()) {
                        try {
                            future.get();
                        } catch (ExecutionException ex) {
                            Throwable cause = ex.getCause();
                            Assert.state(cause != null, "Cause must not be null");
                            if (txAttr.rollbackOn(cause)) {
                                status.setRollbackOnly();
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    } else if (vavrPresent && VavrDelegate.isVavrTry(retVal)) {
                        // Set rollback-only in case of Vavr failure matching our rollback rules...
                        retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
                    }
                }
            }

            commitTransactionAfterReturning(txInfo);
            return retVal;
        } else {
            Object result;
            final ThrowableHolder throwableHolder = new ThrowableHolder();

            // It's a CallbackPreferringPlatformTransactionManager: pass a TransactionCallback in.
            try {
                result = cpptm.execute(txAttr, status -> {
                    TransactionInfo txInfo = prepareTransactionInfo(ptm, txAttr, joinpointIdentification, status);
                    try {
                        Object retVal = invocation.proceedWithInvocation();
                        if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
                            // Set rollback-only in case of Vavr failure matching our rollback rules...
                            retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
                        }
                        return retVal;
                    } catch (Throwable ex) {
                        if (txAttr.rollbackOn(ex)) {
                            // A RuntimeException: will lead to a rollback.
                            if (ex instanceof RuntimeException runtimeException) {
                                throw runtimeException;
                            } else {
                                throw new ThrowableHolderException(ex);
                            }
                        } else {
                            // A normal return value: will lead to a commit.
                            throwableHolder.throwable = ex;
                            return null;
                        }
                    } finally {
                        cleanupTransactionInfo(txInfo);
                    }
                });
            } catch (ThrowableHolderException ex) {
                throw ex.getCause();
            } catch (TransactionSystemException ex2) {
                if (throwableHolder.throwable != null) {
                    logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
                    ex2.initApplicationException(throwableHolder.throwable);
                }
                throw ex2;
            } catch (Throwable ex2) {
                if (throwableHolder.throwable != null) {
                    logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
                }
                throw ex2;
            }

            // Check result state: It might indicate a Throwable to rethrow.
            if (throwableHolder.throwable != null) {
                throw throwableHolder.throwable;
            }
            return result;
        }
    }
    protected void clearTransactionManagerCache() {
        this.transactionManagerCache.clear();
        this.beanFactory = null;
    }
    protected  TransactionManager determineTransactionManager(
             TransactionAttribute txAttr,  Class<?> targetClass) {

        TransactionManager tm = determineTransactionManager(txAttr);
        if (tm != null) {
            return tm;
        }

        // Do not attempt to lookup tx manager if no tx attributes are set
        if (txAttr == null || this.beanFactory == null) {
            return getTransactionManager();
        }

        String qualifier = txAttr.getQualifier();
        if (StringUtils.hasText(qualifier)) {
            return determineQualifiedTransactionManager(this.beanFactory, qualifier);
        } else if (targetClass != null) {
            // Consider type-level qualifier annotations for transaction manager selection
            String typeQualifier = BeanFactoryAnnotationUtils.getQualifierValue(targetClass);
            if (StringUtils.hasText(typeQualifier)) {
                try {
                    return determineQualifiedTransactionManager(this.beanFactory, typeQualifier);
                } catch (NoSuchBeanDefinitionException ex) {
                    // Consider type qualifier as optional, proceed with regular resolution below.
                }
            }
        }

        if (StringUtils.hasText(this.transactionManagerBeanName)) {
            return determineQualifiedTransactionManager(this.beanFactory, this.transactionManagerBeanName);
        } else {
            TransactionManager defaultTransactionManager = getTransactionManager();
            if (defaultTransactionManager == null) {
                defaultTransactionManager = this.transactionManagerCache.get(DEFAULT_TRANSACTION_MANAGER_KEY);
                if (defaultTransactionManager == null) {
                    defaultTransactionManager = this.beanFactory.getBean(TransactionManager.class);
                    this.transactionManagerCache.putIfAbsent(
                            DEFAULT_TRANSACTION_MANAGER_KEY, defaultTransactionManager);
                }
            }
            return defaultTransactionManager;
        }
    }
    @Deprecated(since = "6.2")
    protected  TransactionManager determineTransactionManager( TransactionAttribute txAttr) {
        return null;
    }

    private TransactionManager determineQualifiedTransactionManager(BeanFactory beanFactory, String qualifier) {
        TransactionManager txManager = this.transactionManagerCache.get(qualifier);
        if (txManager == null) {
            txManager = BeanFactoryAnnotationUtils.qualifiedBeanOfType(
                    beanFactory, TransactionManager.class, qualifier);
            this.transactionManagerCache.putIfAbsent(qualifier, txManager);
        }
        return txManager;
    }

    private  PlatformTransactionManager asPlatformTransactionManager( Object transactionManager) {
        if (transactionManager == null) {
            return null;
        }
        if (transactionManager instanceof PlatformTransactionManager ptm) {
            return ptm;
        } else {
            throw new IllegalStateException(
                    "Specified transaction manager is not a PlatformTransactionManager: " + transactionManager);
        }
    }

    private String methodIdentification(Method method,  Class<?> targetClass,
                                         TransactionAttribute txAttr) {

        String methodIdentification = methodIdentification(method, targetClass);
        if (methodIdentification == null) {
            if (txAttr instanceof DefaultTransactionAttribute dta) {
                methodIdentification = dta.getDescriptor();
            }
            if (methodIdentification == null) {
                methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
            }
        }
        return methodIdentification;
    }
    protected  String methodIdentification(Method method,  Class<?> targetClass) {
        return null;
    }
    @SuppressWarnings("serial")
    protected TransactionInfo createTransactionIfNecessary( PlatformTransactionManager tm,
                                                            TransactionAttribute txAttr, final String joinpointIdentification) {

        // If no name specified, apply method identification as transaction name.
        if (txAttr != null && txAttr.getName() == null) {
            txAttr = new DelegatingTransactionAttribute(txAttr) {
                @Override
                public String getName() {
                    return joinpointIdentification;
                }
            };
        }

        TransactionStatus status = null;
        if (txAttr != null) {
            if (tm != null) {
                status = tm.getTransaction(txAttr);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping transactional joinpoint [" + joinpointIdentification +
                            "] because no transaction manager has been configured");
                }
            }
        }
        return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
    }
    protected TransactionInfo prepareTransactionInfo( PlatformTransactionManager tm,
                                                      TransactionAttribute txAttr, String joinpointIdentification,
                                                      TransactionStatus status) {

        TransactionInfo txInfo = new TransactionInfo(tm, txAttr, joinpointIdentification);
        if (txAttr != null) {
            // We need a transaction for this method...
            if (logger.isTraceEnabled()) {
                logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
            }
            // The transaction manager will flag an error if an incompatible tx already exists.
            txInfo.newTransactionStatus(status);
        } else {
            // The TransactionInfo.hasTransaction() method will return false. We created it only
            // to preserve the integrity of the ThreadLocal stack maintained in this class.
            if (logger.isTraceEnabled()) {
                logger.trace("No need to create transaction for [" + joinpointIdentification +
                        "]: This method is not transactional.");
            }
        }

        // We always bind the TransactionInfo to the thread, even if we didn't create
        // a new transaction here. This guarantees that the TransactionInfo stack
        // will be managed correctly even if no transaction was created by this aspect.
        txInfo.bindToThread();
        return txInfo;
    }
    protected void commitTransactionAfterReturning( TransactionInfo txInfo) {
        if (txInfo != null && txInfo.getTransactionStatus() != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
            }
            txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
        }
    }
    protected void completeTransactionAfterThrowing( TransactionInfo txInfo, Throwable ex) {
        if (txInfo != null && txInfo.getTransactionStatus() != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
                        "] after exception: " + ex);
            }
            if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
                try {
                    txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
                } catch (TransactionSystemException ex2) {
                    logger.error("Application exception overridden by rollback exception", ex);
                    ex2.initApplicationException(ex);
                    throw ex2;
                } catch (RuntimeException | Error ex2) {
                    logger.error("Application exception overridden by rollback exception", ex);
                    throw ex2;
                }
            } else {
                // We don't roll back on this exception.
                // Will still roll back if TransactionStatus.isRollbackOnly() is true.
                try {
                    txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
                } catch (TransactionSystemException ex2) {
                    logger.error("Application exception overridden by commit exception", ex);
                    ex2.initApplicationException(ex);
                    throw ex2;
                } catch (RuntimeException | Error ex2) {
                    logger.error("Application exception overridden by commit exception", ex);
                    throw ex2;
                }
            }
        }
    }
    protected void cleanupTransactionInfo( TransactionInfo txInfo) {
        if (txInfo != null) {
            txInfo.restoreThreadLocalStatus();
        }
    }

    protected static final class TransactionInfo {

        private final  PlatformTransactionManager transactionManager;

        private final  TransactionAttribute transactionAttribute;

        private final String joinpointIdentification;

        private  TransactionStatus transactionStatus;

        private  TransactionInfo oldTransactionInfo;

        public TransactionInfo( PlatformTransactionManager transactionManager,
                                TransactionAttribute transactionAttribute, String joinpointIdentification) {

            this.transactionManager = transactionManager;
            this.transactionAttribute = transactionAttribute;
            this.joinpointIdentification = joinpointIdentification;
        }

        public PlatformTransactionManager getTransactionManager() {
            Assert.state(this.transactionManager != null, "No PlatformTransactionManager set");
            return this.transactionManager;
        }

        public  TransactionAttribute getTransactionAttribute() {
            return this.transactionAttribute;
        }

        
        public String getJoinpointIdentification() {
            return this.joinpointIdentification;
        }

        public void newTransactionStatus( TransactionStatus status) {
            this.transactionStatus = status;
        }

        public  TransactionStatus getTransactionStatus() {
            return this.transactionStatus;
        }

        
        public boolean hasTransaction() {
            return (this.transactionStatus != null);
        }

        private void bindToThread() {
            // Expose current TransactionStatus, preserving any existing TransactionStatus
            // for restoration after this transaction is complete.
            this.oldTransactionInfo = transactionInfoHolder.get();
            transactionInfoHolder.set(this);
        }

        private void restoreThreadLocalStatus() {
            // Use stack to restore old transaction TransactionInfo.
            // Will be null if none was set.
            transactionInfoHolder.set(this.oldTransactionInfo);
        }

        @Override
        public String toString() {
            return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
        }
    }

    @FunctionalInterface
    protected interface InvocationCallback {

        
        Object proceedWithInvocation() throws Throwable;
    }

    private static class ThrowableHolder {

        public  Throwable throwable;
    }

    @SuppressWarnings("serial")
    private static class ThrowableHolderException extends RuntimeException {

        public ThrowableHolderException(Throwable throwable) {
            super(throwable);
        }

        @Override
        public String toString() {
            Throwable cause = getCause();
            Assert.state(cause != null, "Cause must not be null");
            return cause.toString();
        }
    }

    private static class VavrDelegate {

        public static boolean isVavrTry(Object retVal) {
            return (retVal instanceof Try);
        }

        public static Object evaluateTryFailure(Object retVal, TransactionAttribute txAttr, TransactionStatus status) {
            return ((Try<?>) retVal).onFailure(ex -> {
                if (txAttr.rollbackOn(ex)) {
                    status.setRollbackOnly();
                }
            });
        }
    }

    private class ReactiveTransactionSupport {

        private final ReactiveAdapter adapter;

        public ReactiveTransactionSupport(ReactiveAdapter adapter) {
            this.adapter = adapter;
        }

        public Object invokeWithinTransaction(Method method,  Class<?> targetClass,
                                              InvocationCallback invocation,  TransactionAttribute txAttr, ReactiveTransactionManager rtm) {

            String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

            // For Mono and suspending functions not returning kotlinx.coroutines.flow.Flow
            if (Mono.class.isAssignableFrom(method.getReturnType()) || (KotlinDetector.isSuspendingFunction(method) &&
                    !COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName()))) {

                return TransactionContextManager.currentContext().flatMap(context ->
                                Mono.<Object, ReactiveTransactionInfo>usingWhen(
                                                createTransactionIfNecessary(rtm, txAttr, joinpointIdentification),
                                                tx -> {
                                                    try {
                                                        return (Mono<?>) invocation.proceedWithInvocation();
                                                    } catch (Throwable ex) {
                                                        return Mono.error(ex);
                                                    }
                                                },
                                                this::commitTransactionAfterReturning,
                                                this::completeTransactionAfterThrowing,
                                                this::rollbackTransactionOnCancel)
                                        .onErrorMap(this::unwrapIfResourceCleanupFailure))
                        .contextWrite(TransactionContextManager.getOrCreateContext())
                        .contextWrite(TransactionContextManager.getOrCreateContextHolder());
            }

            // Any other reactive type, typically a Flux
            return this.adapter.fromPublisher(TransactionContextManager.currentContext().flatMapMany(context ->
                            Flux.usingWhen(
                                            createTransactionIfNecessary(rtm, txAttr, joinpointIdentification),
                                            tx -> {
                                                try {
                                                    return this.adapter.toPublisher(invocation.proceedWithInvocation());
                                                } catch (Throwable ex) {
                                                    return Mono.error(ex);
                                                }
                                            },
                                            this::commitTransactionAfterReturning,
                                            this::completeTransactionAfterThrowing,
                                            this::rollbackTransactionOnCancel)
                                    .onErrorMap(this::unwrapIfResourceCleanupFailure))
                    .contextWrite(TransactionContextManager.getOrCreateContext())
                    .contextWrite(TransactionContextManager.getOrCreateContextHolder()));
        }

        @SuppressWarnings("serial")
        private Mono<ReactiveTransactionInfo> createTransactionIfNecessary(ReactiveTransactionManager tm,
                                                                            TransactionAttribute txAttr, final String joinpointIdentification) {

            // If no name specified, apply method identification as transaction name.
            if (txAttr != null && txAttr.getName() == null) {
                txAttr = new DelegatingTransactionAttribute(txAttr) {
                    @Override
                    public String getName() {
                        return joinpointIdentification;
                    }
                };
            }

            final TransactionAttribute attrToUse = txAttr;
            Mono<ReactiveTransaction> tx = (attrToUse != null ? tm.getReactiveTransaction(attrToUse) : Mono.empty());
            return tx.map(it -> prepareTransactionInfo(tm, attrToUse, joinpointIdentification, it)).switchIfEmpty(
                    Mono.defer(() -> Mono.just(prepareTransactionInfo(tm, attrToUse, joinpointIdentification, null))));
        }

        private ReactiveTransactionInfo prepareTransactionInfo( ReactiveTransactionManager tm,
                                                                TransactionAttribute txAttr, String joinpointIdentification,
                                                                ReactiveTransaction transaction) {

            ReactiveTransactionInfo txInfo = new ReactiveTransactionInfo(tm, txAttr, joinpointIdentification);
            if (txAttr != null) {
                // We need a transaction for this method...
                if (logger.isTraceEnabled()) {
                    logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
                }
                // The transaction manager will flag an error if an incompatible tx already exists.
                txInfo.newReactiveTransaction(transaction);
            } else {
                // The TransactionInfo.hasTransaction() method will return false. We created it only
                // to preserve the integrity of the ThreadLocal stack maintained in this class.
                if (logger.isTraceEnabled()) {
                    logger.trace("Don't need to create transaction for [" + joinpointIdentification +
                            "]: This method isn't transactional.");
                }
            }

            return txInfo;
        }

        private Mono<Void> commitTransactionAfterReturning( ReactiveTransactionInfo txInfo) {
            if (txInfo != null && txInfo.getReactiveTransaction() != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
                }
                return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction());
            }
            return Mono.empty();
        }

        private Mono<Void> rollbackTransactionOnCancel( ReactiveTransactionInfo txInfo) {
            if (txInfo != null && txInfo.getReactiveTransaction() != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Rolling back transaction for [" + txInfo.getJoinpointIdentification() + "] after cancellation");
                }
                return txInfo.getTransactionManager().rollback(txInfo.getReactiveTransaction());
            }
            return Mono.empty();
        }

        private Mono<Void> completeTransactionAfterThrowing( ReactiveTransactionInfo txInfo, Throwable ex) {
            if (txInfo != null && txInfo.getReactiveTransaction() != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
                            "] after exception: " + ex);
                }
                if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
                    return txInfo.getTransactionManager().rollback(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
                                logger.error("Application exception overridden by rollback exception", ex);
                                if (ex2 instanceof TransactionSystemException systemException) {
                                    systemException.initApplicationException(ex);
                                } else {
                                    ex2.addSuppressed(ex);
                                }
                                return ex2;
                            }
                    );
                } else {
                    // We don't roll back on this exception.
                    // Will still roll back if TransactionStatus.isRollbackOnly() is true.
                    return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
                                logger.error("Application exception overridden by commit exception", ex);
                                if (ex2 instanceof TransactionSystemException systemException) {
                                    systemException.initApplicationException(ex);
                                } else {
                                    ex2.addSuppressed(ex);
                                }
                                return ex2;
                            }
                    );
                }
            }
            return Mono.empty();
        }

        
        private Throwable unwrapIfResourceCleanupFailure(Throwable ex) {
            if (ex instanceof RuntimeException && ex.getCause() != null) {
                String msg = ex.getMessage();
                if (msg != null && msg.startsWith("Async resource cleanup failed")) {
                    return ex.getCause();
                }
            }
            return ex;
        }
    }

    private static final class ReactiveTransactionInfo {

        private final  ReactiveTransactionManager transactionManager;

        private final  TransactionAttribute transactionAttribute;

        private final String joinpointIdentification;

        private  ReactiveTransaction reactiveTransaction;

        public ReactiveTransactionInfo( ReactiveTransactionManager transactionManager,
                                        TransactionAttribute transactionAttribute, String joinpointIdentification) {

            this.transactionManager = transactionManager;
            this.transactionAttribute = transactionAttribute;
            this.joinpointIdentification = joinpointIdentification;
        }

        public ReactiveTransactionManager getTransactionManager() {
            Assert.state(this.transactionManager != null, "No ReactiveTransactionManager set");
            return this.transactionManager;
        }

        public  TransactionAttribute getTransactionAttribute() {
            return this.transactionAttribute;
        }

        
        public String getJoinpointIdentification() {
            return this.joinpointIdentification;
        }

        public void newReactiveTransaction( ReactiveTransaction transaction) {
            this.reactiveTransaction = transaction;
        }

        public  ReactiveTransaction getReactiveTransaction() {
            return this.reactiveTransaction;
        }

        @Override
        public String toString() {
            return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
        }
    }
}
