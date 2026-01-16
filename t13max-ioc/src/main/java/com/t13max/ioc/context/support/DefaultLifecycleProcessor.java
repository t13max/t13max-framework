package com.t13max.ioc.context.support;

import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.BeanFactoryAware;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.context.Lifecycle;
import com.t13max.ioc.context.LifecycleProcessor;
import com.t13max.ioc.core.SpringProperties;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;

/**
 * @Author: t13max
 * @Since: 21:29 2026/1/16
 */
public class DefaultLifecycleProcessor implements LifecycleProcessor, BeanFactoryAware {    
    public static final String CHECKPOINT_PROPERTY_NAME = "spring.context.checkpoint";    
    public static final String EXIT_PROPERTY_NAME = "spring.context.exit";    
    public static final String ON_REFRESH_VALUE = "onRefresh";


    private static boolean checkpointOnRefresh = ON_REFRESH_VALUE.equalsIgnoreCase(SpringProperties.getProperty(CHECKPOINT_PROPERTY_NAME));

    private static final boolean exitOnRefresh = ON_REFRESH_VALUE.equalsIgnoreCase(SpringProperties.getProperty(EXIT_PROPERTY_NAME));

    private final Logger logger = LogManager.getLogger(getClass());

    private final Map<Integer, Long> concurrentStartupForPhases = new ConcurrentHashMap<>();

    private final Map<Integer, Long> timeoutsForShutdownPhases = new ConcurrentHashMap<>();

    private volatile long timeoutPerShutdownPhase = 10000;

    private volatile boolean running;

    private volatile  ConfigurableListableBeanFactory beanFactory;

    private volatile  Set<String> stoppedBeans;

    // Just for keeping a strong reference to the registered CRaC Resource, if any
    private  Object cracResource;


    public DefaultLifecycleProcessor() {
        if (!NativeDetector.inNativeImage() && ClassUtils.isPresent("org.crac.Core", getClass().getClassLoader())) {
            this.cracResource = new CracDelegate().registerResource();
        }
        else if (checkpointOnRefresh) {
            throw new IllegalStateException(
                    "Checkpoint on refresh requires a CRaC-enabled JVM and 'org.crac:crac' on the classpath");
        }
    }
    
    public void setConcurrentStartupForPhases(Map<Integer, Long> phasesWithTimeouts) {
        this.concurrentStartupForPhases.putAll(phasesWithTimeouts);
    }    
    public void setConcurrentStartupForPhase(int phase, long timeout) {
        this.concurrentStartupForPhases.put(phase, timeout);
    }    
    public void setTimeoutsForShutdownPhases(Map<Integer, Long> phasesWithTimeouts) {
        this.timeoutsForShutdownPhases.putAll(phasesWithTimeouts);
    }    
    public void setTimeoutForShutdownPhase(int phase, long timeout) {
        this.timeoutsForShutdownPhases.put(phase, timeout);
    }    
    public void setTimeoutPerShutdownPhase(long timeoutPerShutdownPhase) {
        this.timeoutPerShutdownPhase = timeoutPerShutdownPhase;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory clbf)) {
            throw new IllegalArgumentException(
                    "DefaultLifecycleProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
        }
        if (!this.concurrentStartupForPhases.isEmpty() && clbf.getBootstrapExecutor() == null) {
            throw new IllegalStateException("'bootstrapExecutor' needs to be configured for concurrent startup");
        }
        this.beanFactory = clbf;
    }

    private ConfigurableListableBeanFactory getBeanFactory() {
        ConfigurableListableBeanFactory beanFactory = this.beanFactory;
        Assert.state(beanFactory != null, "No BeanFactory available");
        return beanFactory;
    }

    private Executor getBootstrapExecutor() {
        Executor executor = getBeanFactory().getBootstrapExecutor();
        Assert.state(executor != null, "No 'bootstrapExecutor' available");
        return executor;
    }

    private  Long determineConcurrentStartup(int phase) {
        return this.concurrentStartupForPhases.get(phase);
    }

    private long determineShutdownTimeout(int phase) {
        Long timeout = this.timeoutsForShutdownPhases.get(phase);
        return (timeout != null ? timeout : this.timeoutPerShutdownPhase);
    }


    // Lifecycle implementation    
    @Override
    public void start() {
        this.stoppedBeans = null;
        startBeans(false);
        // If any bean failed to explicitly start, the exception propagates here.
        // The caller may choose to subsequently call stop() if appropriate.
        this.running = true;
    }    
    @Override
    public void stop() {
        stopBeans();
        this.running = false;
    }

    @Override
    public void onRefresh() {
        if (checkpointOnRefresh) {
            checkpointOnRefresh = false;
            new CracDelegate().checkpointRestore();
        }
        if (exitOnRefresh) {
            Runtime.getRuntime().halt(0);
        }

        this.stoppedBeans = null;
        try {
            startBeans(true);
        }
        catch (ApplicationContextException ex) {
            // Some bean failed to auto-start within context refresh:
            // stop already started beans on context refresh failure.
            stopBeans();
            throw ex;
        }
        this.running = true;
    }

    @Override
    public void onClose() {
        stopBeans();
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }


    // Internal helpers

    void stopForRestart() {
        if (this.running) {
            this.stoppedBeans = ConcurrentHashMap.newKeySet();
            stopBeans();
            this.running = false;
        }
    }

    void restartAfterStop() {
        if (this.stoppedBeans != null) {
            startBeans(true);
            this.stoppedBeans = null;
            this.running = true;
        }
    }

    private void startBeans(boolean autoStartupOnly) {
        Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
        Map<Integer, LifecycleGroup> phases = new TreeMap<>();

        lifecycleBeans.forEach((beanName, bean) -> {
            if (!autoStartupOnly || isAutoStartupCandidate(beanName, bean)) {
                int startupPhase = getPhase(bean);
                phases.computeIfAbsent(startupPhase, phase -> new LifecycleGroup(phase, lifecycleBeans, autoStartupOnly))
                        .add(beanName, bean);
            }
        });

        if (!phases.isEmpty()) {
            phases.values().forEach(LifecycleGroup::start);
        }
    }

    private boolean isAutoStartupCandidate(String beanName, Lifecycle bean) {
        Set<String> stoppedBeans = this.stoppedBeans;
        return (stoppedBeans != null ? stoppedBeans.contains(beanName) :
                (bean instanceof SmartLifecycle smartLifecycle && smartLifecycle.isAutoStartup()));
    }    
    private void doStart(Map<String, ? extends Lifecycle> lifecycleBeans, String beanName,
                         boolean autoStartupOnly,  List<CompletableFuture<?>> futures) {

        Lifecycle bean = lifecycleBeans.remove(beanName);
        if (bean != null && bean != this) {
            String[] dependenciesForBean = getBeanFactory().getDependenciesForBean(beanName);
            for (String dependency : dependenciesForBean) {
                doStart(lifecycleBeans, dependency, autoStartupOnly, futures);
            }
            if (!bean.isRunning() && (!autoStartupOnly || toBeStarted(beanName, bean))) {
                if (futures != null) {
                    futures.add(CompletableFuture.runAsync(() -> doStart(beanName, bean), getBootstrapExecutor()));
                }
                else {
                    doStart(beanName, bean);
                }
            }
        }
    }

    private void doStart(String beanName, Lifecycle bean) {
        if (logger.isTraceEnabled()) {
            logger.trace("Starting bean '" + beanName + "' of type [" + bean.getClass().getName() + "]");
        }
        try {
            bean.start();
        }
        catch (Throwable ex) {
            throw new ApplicationContextException("Failed to start bean '" + beanName + "'", ex);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Successfully started bean '" + beanName + "'");
        }
    }

    private boolean toBeStarted(String beanName, Lifecycle bean) {
        Set<String> stoppedBeans = this.stoppedBeans;
        return (stoppedBeans != null ? stoppedBeans.contains(beanName) :
                (!(bean instanceof SmartLifecycle smartLifecycle) || smartLifecycle.isAutoStartup()));
    }

    private void stopBeans() {
        Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
        Map<Integer, LifecycleGroup> phases = new TreeMap<>(Comparator.reverseOrder());

        lifecycleBeans.forEach((beanName, bean) -> {
            int shutdownPhase = getPhase(bean);
            phases.computeIfAbsent(shutdownPhase, phase -> new LifecycleGroup(phase, lifecycleBeans, false))
                    .add(beanName, bean);
        });

        if (!phases.isEmpty()) {
            phases.values().forEach(LifecycleGroup::stop);
        }
    }    
    private void doStop(Map<String, ? extends Lifecycle> lifecycleBeans, final String beanName,
                        final CountDownLatch latch, final Set<String> countDownBeanNames) {

        Lifecycle bean = lifecycleBeans.remove(beanName);
        if (bean != null) {
            String[] dependentBeans = getBeanFactory().getDependentBeans(beanName);
            for (String dependentBean : dependentBeans) {
                doStop(lifecycleBeans, dependentBean, latch, countDownBeanNames);
            }
            try {
                if (bean.isRunning()) {
                    Set<String> stoppedBeans = this.stoppedBeans;
                    if (stoppedBeans != null) {
                        stoppedBeans.add(beanName);
                    }
                    if (bean instanceof SmartLifecycle smartLifecycle) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Asking bean '" + beanName + "' of type [" +
                                    bean.getClass().getName() + "] to stop");
                        }
                        countDownBeanNames.add(beanName);
                        smartLifecycle.stop(() -> {
                            latch.countDown();
                            countDownBeanNames.remove(beanName);
                            if (logger.isDebugEnabled()) {
                                logger.debug("Bean '" + beanName + "' completed its stop procedure");
                            }
                        });
                    }
                    else {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Stopping bean '" + beanName + "' of type [" +
                                    bean.getClass().getName() + "]");
                        }
                        bean.stop();
                        if (logger.isDebugEnabled()) {
                            logger.debug("Successfully stopped bean '" + beanName + "'");
                        }
                    }
                }
                else if (bean instanceof SmartLifecycle) {
                    // Don't wait for beans that aren't running...
                    latch.countDown();
                }
            }
            catch (Throwable ex) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to stop bean '" + beanName + "'", ex);
                }
                if (bean instanceof SmartLifecycle) {
                    latch.countDown();
                }
            }
        }
    }


    // Overridable hooks    
    protected Map<String, Lifecycle> getLifecycleBeans() {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        Map<String, Lifecycle> beans = new LinkedHashMap<>();
        String[] beanNames = beanFactory.getBeanNamesForType(Lifecycle.class, false, false);
        for (String beanName : beanNames) {
            String beanNameToRegister = BeanFactoryUtils.transformedBeanName(beanName);
            boolean isFactoryBean = beanFactory.isFactoryBean(beanNameToRegister);
            String beanNameToCheck = (isFactoryBean ? BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
            if ((beanFactory.containsSingleton(beanNameToRegister) &&
                    (!isFactoryBean || matchesBeanType(Lifecycle.class, beanNameToCheck, beanFactory))) ||
                    matchesBeanType(SmartLifecycle.class, beanNameToCheck, beanFactory)) {
                Object bean = beanFactory.getBean(beanNameToCheck);
                if (bean != this && bean instanceof Lifecycle lifecycle) {
                    beans.put(beanNameToRegister, lifecycle);
                }
            }
        }
        return beans;
    }

    private boolean matchesBeanType(Class<?> targetType, String beanName, BeanFactory beanFactory) {
        Class<?> beanType = beanFactory.getType(beanName);
        return (beanType != null && targetType.isAssignableFrom(beanType));
    }    
    protected int getPhase(Lifecycle bean) {
        return (bean instanceof Phased phased ? phased.getPhase() : 0);
    }
    
    private class LifecycleGroup {

        private final int phase;

        private final Map<String, ? extends Lifecycle> lifecycleBeans;

        private final boolean autoStartupOnly;

        private final List<LifecycleGroupMember> members = new ArrayList<>();

        private int smartMemberCount;

        public LifecycleGroup(int phase, Map<String, ? extends Lifecycle> lifecycleBeans, boolean autoStartupOnly) {
            this.phase = phase;
            this.lifecycleBeans = lifecycleBeans;
            this.autoStartupOnly = autoStartupOnly;
        }

        public void add(String name, Lifecycle bean) {
            this.members.add(new LifecycleGroupMember(name, bean));
            if (bean instanceof SmartLifecycle) {
                this.smartMemberCount++;
            }
        }

        public void start() {
            if (this.members.isEmpty()) {
                return;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Starting beans in phase " + this.phase);
            }
            Long concurrentStartup = determineConcurrentStartup(this.phase);
            List<CompletableFuture<?>> futures = (concurrentStartup != null ? new ArrayList<>() : null);
            for (LifecycleGroupMember member : this.members) {
                doStart(this.lifecycleBeans, member.name, this.autoStartupOnly, futures);
            }
            if (concurrentStartup != null && !CollectionUtils.isEmpty(futures)) {
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                            .get(concurrentStartup, TimeUnit.MILLISECONDS);
                }
                catch (Exception ex) {
                    if (ex instanceof ExecutionException exEx) {
                        Throwable cause = exEx.getCause();
                        if (cause instanceof ApplicationContextException acEx) {
                            throw acEx;
                        }
                    }
                    throw new ApplicationContextException("Failed to start beans in phase " + this.phase +
                            " within timeout of " + concurrentStartup + "ms", ex);
                }
            }
        }

        public void stop() {
            if (this.members.isEmpty()) {
                return;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Stopping beans in phase " + this.phase);
            }
            CountDownLatch latch = new CountDownLatch(this.smartMemberCount);
            Set<String> countDownBeanNames = Collections.synchronizedSet(new LinkedHashSet<>());
            Set<String> lifecycleBeanNames = new HashSet<>(this.lifecycleBeans.keySet());
            for (LifecycleGroupMember member : this.members) {
                if (lifecycleBeanNames.contains(member.name)) {
                    doStop(this.lifecycleBeans, member.name, latch, countDownBeanNames);
                }
                else if (member.bean instanceof SmartLifecycle) {
                    // Already removed: must have been a dependent bean from another phase
                    latch.countDown();
                }
            }
            try {
                long shutdownTimeout = determineShutdownTimeout(this.phase);
                if (!latch.await(shutdownTimeout, TimeUnit.MILLISECONDS)) {
                    // Count is still >0 after timeout
                    if (!countDownBeanNames.isEmpty() && logger.isInfoEnabled()) {
                        logger.info("Shutdown phase " + this.phase + " ends with " + countDownBeanNames.size() +
                                " bean" + (countDownBeanNames.size() > 1 ? "s" : "") +
                                " still running after timeout of " + shutdownTimeout + "ms: " + countDownBeanNames);
                    }
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private record LifecycleGroupMember(String name, Lifecycle bean) {}
    
    private class CracDelegate {

        public Object registerResource() {
            logger.debug("Registering JVM checkpoint/restore callback for Spring-managed lifecycle beans");
            CracResourceAdapter resourceAdapter = new CracResourceAdapter();
            org.crac.Core.getGlobalContext().register(resourceAdapter);
            return resourceAdapter;
        }

        public void checkpointRestore() {
            logger.info("Triggering JVM checkpoint/restore");
            try {
                Core.checkpointRestore();
            }
            catch (UnsupportedOperationException ex) {
                throw new ApplicationContextException("CRaC checkpoint not supported on current JVM", ex);
            }
            catch (CheckpointException ex) {
                throw new ApplicationContextException("Failed to take CRaC checkpoint on refresh", ex);
            }
            catch (RestoreException ex) {
                throw new ApplicationContextException("Failed to restore CRaC checkpoint on refresh", ex);
            }
        }
    }
    
    private class CracResourceAdapter implements org.crac.Resource {

        private  CyclicBarrier barrier;

        @Override
        public void beforeCheckpoint(org.crac.Context<? extends org.crac.Resource> context) {
            // A non-daemon thread for preventing an accidental JVM shutdown before the checkpoint
            this.barrier = new CyclicBarrier(2);

            Thread thread = new Thread(() -> {
                awaitPreventShutdownBarrier();
                // Checkpoint happens here
                awaitPreventShutdownBarrier();
            }, "prevent-shutdown");

            thread.setDaemon(false);
            thread.start();
            awaitPreventShutdownBarrier();

            logger.debug("Stopping Spring-managed lifecycle beans before JVM checkpoint");
            stopForRestart();
        }

        @Override
        public void afterRestore(org.crac.Context<? extends org.crac.Resource> context) {
            logger.info("Restarting Spring-managed lifecycle beans after JVM restore");
            restartAfterStop();

            // Barrier for prevent-shutdown thread not needed anymore
            this.barrier = null;

            if (!checkpointOnRefresh) {
                logger.info("Spring-managed lifecycle restart completed (restored JVM running for " +
                        CRaCMXBean.getCRaCMXBean().getUptimeSinceRestore() + " ms)");
            }
        }

        private void awaitPreventShutdownBarrier() {
            try {
                if (this.barrier != null) {
                    this.barrier.await();
                }
            }
            catch (Exception ex) {
                logger.trace("Exception from prevent-shutdown barrier", ex);
            }
        }
    }

}
