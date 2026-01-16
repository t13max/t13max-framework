package com.t13max.ioc.context.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.CachedIntrospectionResults;
import com.t13max.ioc.beans.factory.*;
import com.t13max.ioc.beans.factory.config.AutowireCapableBeanFactory;
import com.t13max.ioc.beans.factory.config.BeanFactoryPostProcessor;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.beans.support.ResourceEditorRegistrar;
import com.t13max.ioc.context.*;
import com.t13max.ioc.context.event.*;
import com.t13max.ioc.context.expression.StandardBeanExpressionResolver;
import com.t13max.ioc.context.weaving.LoadTimeWeaverAware;
import com.t13max.ioc.context.weaving.LoadTimeWeaverAwareProcessor;
import com.t13max.ioc.core.NativeDetector;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.env.ConfigurableEnvironment;
import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.core.env.StandardEnvironment;
import com.t13max.ioc.core.io.DefaultResourceLoader;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.io.support.PathMatchingResourcePatternResolver;
import com.t13max.ioc.core.io.support.ResourcePatternResolver;
import com.t13max.ioc.core.metrics.ApplicationStartup;
import com.t13max.ioc.core.metrics.StartupStep;
import com.t13max.ioc.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 应用上下文抽象父类
 *
 * @Author: t13max
 * @Since: 22:49 2026/1/14
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader implements ConfigurableApplicationContext {

    protected final Logger logger = LogManager.getLogger(getClass());

    //id
    private String id = ObjectUtils.identityToString(this);
    //名字
    private String displayName = ObjectUtils.identityToString(this);
    //父容器
    private ApplicationContext parent;
    //
    private ConfigurableEnvironment environment;
    //
    private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();
    //启动时间戳
    private long startupDate;
    //容器已启动标志位
    private final AtomicBoolean active = new AtomicBoolean();
    //容器已关闭标志位
    private final AtomicBoolean closed = new AtomicBoolean();
    //启动锁
    private final Lock startupShutdownLock = new ReentrantLock();
    //启动线程
    private volatile Thread startupShutdownThread;
    //
    private Thread shutdownHook;
    //
    private final ResourcePatternResolver resourcePatternResolver;
    //
    private LifecycleProcessor lifecycleProcessor;
    //
    private MessageSource messageSource;
    //
    private ApplicationEventMulticaster applicationEventMulticaster;
    //性能指标
    private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;
    //
    private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();
    //
    private Set<ApplicationListener<?>> earlyApplicationListeners;
    //
    private Set<ApplicationEvent> earlyApplicationEvents;

    public AbstractApplicationContext() {
        this.resourcePatternResolver = getResourcePatternResolver();
    }

    /**
     * Create a new AbstractApplicationContext with the given parent context.
     *
     * @param parent the parent context
     */
    public AbstractApplicationContext(ApplicationContext parent) {
        this();
        setParent(parent);
    }

    //---------------------------------------------------------------------
    // ApplicationContext实现
    //---------------------------------------------------------------------

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getApplicationName() {
        return "";
    }

    public void setDisplayName(String displayName) {
        Assert.hasLength(displayName, "Display name must not be empty");
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public ApplicationContext getParent() {
        return this.parent;
    }

    @Override
    public void setEnvironment(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public ConfigurableEnvironment getEnvironment() {
        if (this.environment == null) {
            this.environment = createEnvironment();
        }
        return this.environment;
    }

    protected ConfigurableEnvironment createEnvironment() {
        return new StandardEnvironment();
    }

    @Override
    public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return getBeanFactory();
    }

    @Override
    public long getStartupDate() {
        return this.startupDate;
    }

    @Override
    public void publishEvent(ApplicationEvent event) {
        //publishEvent(event, null);
    }

    @Override
    public void publishEvent(Object event) {
        //publishEvent(event, null);
    }    
    /*protected void publishEvent(Object event, ResolvableType typeHint) {
        Assert.notNull(event, "Event must not be null");
        ResolvableType eventType = null;

        // Decorate event as an ApplicationEvent if necessary
        ApplicationEvent applicationEvent;
        if (event instanceof ApplicationEvent applEvent) {
            applicationEvent = applEvent;
            eventType = typeHint;
        }
        else {
            ResolvableType payloadType = null;
            if (typeHint != null && ApplicationEvent.class.isAssignableFrom(typeHint.toClass())) {
                eventType = typeHint;
            }
            else {
                payloadType = typeHint;
            }
            applicationEvent = new PayloadApplicationEvent<>(this, event, payloadType);
        }

        // Determine event type only once (for multicast and parent publish)
        if (eventType == null) {
            eventType = ResolvableType.forInstance(applicationEvent);
            if (typeHint == null) {
                typeHint = eventType;
            }
        }

        // Multicast right now if possible - or lazily once the multicaster is initialized
        if (this.earlyApplicationEvents != null) {
            this.earlyApplicationEvents.add(applicationEvent);
        }
        else if (this.applicationEventMulticaster != null) {
            this.applicationEventMulticaster.multicastEvent(applicationEvent, eventType);
        }

        // Publish event via parent context as well...
        if (this.parent != null) {
            if (this.parent instanceof AbstractApplicationContext abstractApplicationContext) {
                abstractApplicationContext.publishEvent(event, typeHint);
            }
            else {
                this.parent.publishEvent(event);
            }
        }
    }*/

    @Override
    public void setApplicationStartup(ApplicationStartup applicationStartup) {
        Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
        this.applicationStartup = applicationStartup;
    }

    @Override
    public ApplicationStartup getApplicationStartup() {
        return this.applicationStartup;
    }

    LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
        if (this.lifecycleProcessor == null) {
            throw new IllegalStateException("LifecycleProcessor not initialized - " +
                    "call 'refresh' before invoking lifecycle methods via the context: " + this);
        }
        return this.lifecycleProcessor;
    }

    protected ResourcePatternResolver getResourcePatternResolver() {
        return new PathMatchingResourcePatternResolver(this);
    }

    //---------------------------------------------------------------------
    // ConfigurableApplicationContext实现
    //---------------------------------------------------------------------

    @Override
    public void setParent(ApplicationContext parent) {
        this.parent = parent;
        if (parent != null) {
            Environment parentEnvironment = parent.getEnvironment();
            if (parentEnvironment instanceof ConfigurableEnvironment configurableEnvironment) {
                getEnvironment().merge(configurableEnvironment);
            }
        }
    }

    @Override
    public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
        Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
        this.beanFactoryPostProcessors.add(postProcessor);
    }

    public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
        return this.beanFactoryPostProcessors;
    }

    @Override
    public void addApplicationListener(ApplicationListener<?> listener) {
        Assert.notNull(listener, "ApplicationListener must not be null");
        if (this.applicationEventMulticaster != null) {
            this.applicationEventMulticaster.addApplicationListener(listener);
        }
        this.applicationListeners.add(listener);
    }

    @Override
    public void removeApplicationListener(ApplicationListener<?> listener) {
        Assert.notNull(listener, "ApplicationListener must not be null");
        if (this.applicationEventMulticaster != null) {
            this.applicationEventMulticaster.removeApplicationListener(listener);
        }
        this.applicationListeners.remove(listener);
    }

    /**
     * 刷新容器
     * 每次调用后重新加载bean
     *
     * @Author: t13max
     * @Since: 20:35 2026/1/15
     */
    @Override
    public void refresh() throws BeansException, IllegalStateException {

        this.startupShutdownLock.lock();

        try {
            this.startupShutdownThread = Thread.currentThread();

            StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");

            // 调用容器准备刷新, 获取容器的当前时间, 同时给容器设置同步标识
            prepareRefresh();

            // 告诉子类启动refreshBeanFactory()方法, BeanDefinition资源文件的载入从子类的refreshBeanFactory()方法启动开始
            ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

            // 为BeanFactory配置容器特性, 例如类加载器,事件处理器等
            prepareBeanFactory(beanFactory);

            try {
                // 为容器的某些子类指定特殊的BeanPost事件处理器
                postProcessBeanFactory(beanFactory);

                StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");

                // 调用所有注册的BeanFactoryPostProcessor的Bean
                invokeBeanFactoryPostProcessors(beanFactory);

                // BeanPostProcessor是Bean后置处理器,用于监听容器触发的事件
                registerBeanPostProcessors(beanFactory);

                beanPostProcess.end();

                // 初始化信息源，和国际化相关.
                initMessageSource();

                // 初始化容器事件传播器
                initApplicationEventMulticaster();

                // 调用子类的某些特殊Bean初始化方法
                onRefresh();

                // 为事件传播器注册事件监听器
                registerListeners();

                // 初始化Bean, 并对lazy-init属性进行处理
                finishBeanFactoryInitialization(beanFactory);

                // 初始化容器的生命周期事件处理器,并发布容器的生命周期事件
                finishRefresh();
            } catch (RuntimeException | Error ex) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Exception encountered during context initialization - cancelling refresh attempt:{}", ex.getMessage(), ex);
                }

                // 销毁以创建的单态Bean
                destroyBeans();

                // 取消refresh操作, 重置容器的同步标识
                cancelRefresh(ex);

                throw ex;
            } finally {
                contextRefresh.end();
            }
        } finally {
            this.startupShutdownThread = null;
            this.startupShutdownLock.unlock();
        }
    }

    protected void prepareRefresh() {
        // 启动!
        this.startupDate = System.currentTimeMillis();
        this.closed.set(false);
        this.active.set(true);

        if (logger.isDebugEnabled()) {
            if (logger.isTraceEnabled()) {
                logger.trace("Refreshing {}", this);
            } else {
                logger.debug("Refreshing {}", getDisplayName());
            }
        }

        // Initialize any placeholder property sources in the context environment.
        initPropertySources();

        // Validate that all properties marked as required are resolvable:
        // see ConfigurablePropertyResolver#setRequiredProperties
        getEnvironment().validateRequiredProperties();

        // Store pre-refresh ApplicationListeners...
        if (this.earlyApplicationListeners == null) {
            this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
        } else {
            // Reset local application listeners to pre-refresh state.
            this.applicationListeners.clear();
            this.applicationListeners.addAll(this.earlyApplicationListeners);
        }

        // Allow for the collection of early ApplicationEvents,
        // to be published once the multicaster is available...
        this.earlyApplicationEvents = new LinkedHashSet<>();
    }

    protected void initPropertySources() {
        // For subclasses: do nothing by default.
    }

    protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
        refreshBeanFactory();
        return getBeanFactory();
    }

    protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // Tell the internal bean factory to use the context's class loader etc.
        beanFactory.setBeanClassLoader(getClassLoader());
        beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
        beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

        // Configure the bean factory with context callbacks.
        beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
        beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
        beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
        beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
        beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
        beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
        beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
        beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);

        // BeanFactory interface not registered as resolvable type in a plain factory.
        // MessageSource registered (and found for autowiring) as a bean.
        beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
        beanFactory.registerResolvableDependency(ResourceLoader.class, this);
        beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
        beanFactory.registerResolvableDependency(ApplicationContext.class, this);

        // Register early post-processor for detecting inner beans as ApplicationListeners.
        beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

        // Detect a LoadTimeWeaver and prepare for weaving, if found.
        if (!NativeDetector.inNativeImage() && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
            beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
            // Set a temporary ClassLoader for type matching.
            beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
        }

        // Register default environment beans.
        if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
            beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
        }
        if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
            beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
        }
        if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
            beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
        }
        if (!beanFactory.containsLocalBean(APPLICATION_STARTUP_BEAN_NAME)) {
            beanFactory.registerSingleton(APPLICATION_STARTUP_BEAN_NAME, getApplicationStartup());
        }
    }

    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
        PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

        // Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
        // (for example, through an @Bean method registered by ConfigurationClassPostProcessor)
        if (!NativeDetector.inNativeImage() && beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
            beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
            beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
        }
    }

    protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
        PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
    }

    protected void initMessageSource() {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
            this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
            // Make MessageSource aware of parent MessageSource.
            if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource hms && hms.getParentMessageSource() == null) {
                // Only set parent context as parent MessageSource if no parent MessageSource
                // registered already.
                hms.setParentMessageSource(getInternalParentMessageSource());
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Using MessageSource [{}]", this.messageSource);
            }
        } else {
            // Use empty MessageSource to be able to accept getMessage calls.
            DelegatingMessageSource dms = new DelegatingMessageSource();
            dms.setParentMessageSource(getInternalParentMessageSource());
            this.messageSource = dms;
            beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
            if (logger.isTraceEnabled()) {
                logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
            }
        }
    }

    protected void initApplicationEventMulticaster() {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
            this.applicationEventMulticaster = beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
            if (logger.isTraceEnabled()) {
                logger.trace("Using ApplicationEventMulticaster [{}]", this.applicationEventMulticaster);
            }
        } else {
            this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
            beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
            if (logger.isTraceEnabled()) {
                logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " + "[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
            }
        }
    }

    protected void initLifecycleProcessor() {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
            this.lifecycleProcessor = beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
            if (logger.isTraceEnabled()) {
                logger.trace("Using LifecycleProcessor [{}]", this.lifecycleProcessor);
            }
        } else {
            DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
            defaultProcessor.setBeanFactory(beanFactory);
            this.lifecycleProcessor = defaultProcessor;
            beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
            if (logger.isTraceEnabled()) {
                logger.trace("No '" + LIFECYCLE_PROCESSOR_BEAN_NAME + "' bean, using " + "[" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
            }
        }
    }

    protected void onRefresh() throws BeansException {
        // For subclasses: do nothing by default.
    }

    protected void registerListeners() {
        // Register statically specified listeners first.
        for (ApplicationListener<?> listener : getApplicationListeners()) {
            getApplicationEventMulticaster().addApplicationListener(listener);
        }

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let post-processors apply to them!
        String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
        for (String listenerBeanName : listenerBeanNames) {
            getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
        }

        // Publish early application events now that we finally have a multicaster...
        Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
        this.earlyApplicationEvents = null;
        if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
            for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
                getApplicationEventMulticaster().multicastEvent(earlyEvent);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
        // Initialize bootstrap executor for this context.
        if (beanFactory.containsBean(BOOTSTRAP_EXECUTOR_BEAN_NAME) && beanFactory.isTypeMatch(BOOTSTRAP_EXECUTOR_BEAN_NAME, Executor.class)) {
            beanFactory.setBootstrapExecutor(beanFactory.getBean(BOOTSTRAP_EXECUTOR_BEAN_NAME, Executor.class));
        }

        // Initialize conversion service for this context.
        if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) && beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
            beanFactory.setConversionService(beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
        }

        // Register a default embedded value resolver if no BeanFactoryPostProcessor
        // (such as a PropertySourcesPlaceholderConfigurer bean) registered any before:
        // at this point, primarily for resolution in annotation attribute values.
        if (!beanFactory.hasEmbeddedValueResolver()) {
            beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
        }

        // Call BeanFactoryInitializer beans early to allow for initializing specific other beans early.
        String[] initializerNames = beanFactory.getBeanNamesForType(BeanFactoryInitializer.class, false, false);
        for (String initializerName : initializerNames) {
            beanFactory.getBean(initializerName, BeanFactoryInitializer.class).initialize(beanFactory);
        }

        // Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
        String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
        for (String weaverAwareName : weaverAwareNames) {
            try {
                beanFactory.getBean(weaverAwareName, LoadTimeWeaverAware.class);
            } catch (BeanNotOfRequiredTypeException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to initialize LoadTimeWeaverAware bean '{}' due to unexpected type mismatch: {}", weaverAwareName, ex.getMessage());
                }
            }
        }

        // Stop using the temporary ClassLoader for type matching.
        beanFactory.setTempClassLoader(null);

        // Allow for caching all bean definition metadata, not expecting further changes.
        beanFactory.freezeConfiguration();

        // Instantiate all remaining (non-lazy-init) singletons.
        beanFactory.preInstantiateSingletons();
    }

    protected void finishRefresh() {
        // Reset common introspection caches in Spring's core infrastructure.
        resetCommonCaches();

        // Clear context-level resource caches (such as ASM metadata from scanning).
        clearResourceCaches();

        // Initialize lifecycle processor for this context.
        initLifecycleProcessor();

        // Propagate refresh to lifecycle processor first.
        getLifecycleProcessor().onRefresh();

        // Publish the final event.
        publishEvent(new ContextRefreshedEvent(this));
    }

    protected void cancelRefresh(Throwable ex) {
        this.active.set(false);

        // Reset common introspection caches in Spring's core infrastructure.
        resetCommonCaches();
    }

    protected void resetCommonCaches() {
        ReflectionUtils.clearCache();
        AnnotationUtils.clearCache();
        ResolvableType.clearCache();
        CachedIntrospectionResults.clearClassLoader(getClassLoader());
    }

    @Override
    public void clearResourceCaches() {
        super.clearResourceCaches();
        if (this.resourcePatternResolver instanceof PathMatchingResourcePatternResolver pmrpr) {
            pmrpr.clearCache();
        }
    }

    @Override
    public void registerShutdownHook() {
        if (this.shutdownHook == null) {
            // No shutdown hook registered yet.
            this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
                @Override
                public void run() {
                    if (isStartupShutdownThreadStuck()) {
                        active.set(false);
                        return;
                    }
                    startupShutdownLock.lock();
                    try {
                        doClose();
                    } finally {
                        startupShutdownLock.unlock();
                    }
                }
            };
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        }
    }

    private boolean isStartupShutdownThreadStuck() {
        Thread activeThread = this.startupShutdownThread;
        if (activeThread != null && activeThread.getState() == Thread.State.WAITING) {
            // Indefinitely waiting: might be Thread.join or the like, or System.exit
            activeThread.interrupt();
            try {
                // Leave just a little bit of time for the interruption to show effect
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (activeThread.getState() == Thread.State.WAITING) {
                // Interrupted but still waiting: very likely a System.exit call
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        if (isStartupShutdownThreadStuck()) {
            this.active.set(false);
            return;
        }

        this.startupShutdownLock.lock();
        try {
            this.startupShutdownThread = Thread.currentThread();

            doClose();

            // If we registered a JVM shutdown hook, we don't need it anymore now:
            // We've already explicitly closed the context.
            if (this.shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
                } catch (IllegalStateException ex) {
                    // ignore - VM is already shutting down
                }
            }
        } finally {
            this.startupShutdownThread = null;
            this.startupShutdownLock.unlock();
        }
    }

    protected void doClose() {
        // Check whether an actual close attempt is necessary...
        if (this.active.get() && this.closed.compareAndSet(false, true)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing " + this);
            }

            try {
                // Publish shutdown event.
                publishEvent(new ContextClosedEvent(this));
            } catch (Throwable ex) {
                logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
            }

            // Stop all Lifecycle beans, to avoid delays during individual destruction.
            if (this.lifecycleProcessor != null) {
                try {
                    this.lifecycleProcessor.onClose();
                } catch (Throwable ex) {
                    logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
                }
            }

            // Destroy all cached singletons in the context's BeanFactory.
            destroyBeans();

            // Close the state of this context itself.
            closeBeanFactory();

            // Let subclasses do some final clean-up if they wish...
            onClose();

            // Reset common introspection caches to avoid class reference leaks.
            resetCommonCaches();

            // Reset local application listeners to pre-refresh state.
            if (this.earlyApplicationListeners != null) {
                this.applicationListeners.clear();
                this.applicationListeners.addAll(this.earlyApplicationListeners);
            }

            // Reset internal delegates.
            this.applicationEventMulticaster = null;
            this.messageSource = null;
            this.lifecycleProcessor = null;

            // Switch to inactive.
            this.active.set(false);
        }
    }

    protected void destroyBeans() {
        getBeanFactory().destroySingletons();
    }

    protected void onClose() {
        // For subclasses: do nothing by default.
    }

    @Override
    public boolean isClosed() {
        return this.closed.get();
    }

    @Override
    public boolean isActive() {
        return this.active.get();
    }

    //判断BeanFactory是否存活
    protected void assertBeanFactoryActive() {
        //是否存活
        if (!this.active.get()) {
            //是否关闭
            if (this.closed.get()) {
                throw new IllegalStateException(getDisplayName() + " has been closed already");
            } else {
                throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
            }
        }
    }

    //---------------------------------------------------------------------
    // BeanFactory实现
    //---------------------------------------------------------------------

    @Override
    public Object getBean(String name) throws BeansException {
        //判断BeanFactory是否存活
        assertBeanFactoryActive();
        //从BeanFactory里获取Bean
        return getBeanFactory().getBean(name);
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        assertBeanFactoryActive();
        return getBeanFactory().getBean(name, requiredType);
    }

    @Override
    public Object getBean(String name, Object... args) throws BeansException {
        assertBeanFactoryActive();
        return getBeanFactory().getBean(name, args);
    }

    @Override
    public <T> T getBean(Class<T> requiredType) throws BeansException {
        assertBeanFactoryActive();
        return getBeanFactory().getBean(requiredType);
    }

    @Override
    public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
        assertBeanFactoryActive();
        return getBeanFactory().getBean(requiredType, args);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanProvider(requiredType);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanProvider(requiredType);
    }

    @Override
    public boolean containsBean(String name) {
        return getBeanFactory().containsBean(name);
    }

    @Override
    public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        assertBeanFactoryActive();
        return getBeanFactory().isSingleton(name);
    }

    @Override
    public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
        assertBeanFactoryActive();
        return getBeanFactory().isPrototype(name);
    }

    @Override
    public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        assertBeanFactoryActive();
        return getBeanFactory().isTypeMatch(name, typeToMatch);
    }

    @Override
    public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
        assertBeanFactoryActive();
        return getBeanFactory().isTypeMatch(name, typeToMatch);
    }

    @Override
    public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        assertBeanFactoryActive();
        return getBeanFactory().getType(name);
    }

    @Override
    public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        assertBeanFactoryActive();
        return getBeanFactory().getType(name, allowFactoryBeanInit);
    }

    @Override
    public String[] getAliases(String name) {
        return getBeanFactory().getAliases(name);
    }


    //---------------------------------------------------------------------
    // ListableBeanFactory实现
    //---------------------------------------------------------------------

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return getBeanFactory().containsBeanDefinition(beanName);
    }

    @Override
    public int getBeanDefinitionCount() {
        return getBeanFactory().getBeanDefinitionCount();
    }

    @Override
    public String[] getBeanDefinitionNames() {
        return getBeanFactory().getBeanDefinitionNames();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanNamesForType(type);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanNamesForType(type);
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        assertBeanFactoryActive();
        return getBeanFactory().getBeansOfType(type);
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
            throws BeansException {

        assertBeanFactoryActive();
        return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
    }

    @Override
    public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        assertBeanFactoryActive();
        return getBeanFactory().getBeanNamesForAnnotation(annotationType);
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
            throws BeansException {

        assertBeanFactoryActive();
        return getBeanFactory().getBeansWithAnnotation(annotationType);
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
            throws NoSuchBeanDefinitionException {

        assertBeanFactoryActive();
        return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {

        assertBeanFactoryActive();
        return getBeanFactory().findAnnotationOnBean(beanName, annotationType, allowFactoryBeanInit);
    }

    @Override
    public <A extends Annotation> Set<A> findAllAnnotationsOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {

        assertBeanFactoryActive();
        return getBeanFactory().findAllAnnotationsOnBean(beanName, annotationType, allowFactoryBeanInit);
    }


    //---------------------------------------------------------------------
    // HierarchicalBeanFactory实现
    //---------------------------------------------------------------------

    @Override
    public BeanFactory getParentBeanFactory() {
        return getParent();
    }

    @Override
    public boolean containsLocalBean(String name) {
        return getBeanFactory().containsLocalBean(name);
    }

    protected BeanFactory getInternalParentBeanFactory() {
        return (getParent() instanceof ConfigurableApplicationContext cac ?
                cac.getBeanFactory() : getParent());
    }


    //---------------------------------------------------------------------
    // MessageSource实现
    //---------------------------------------------------------------------

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        return getMessageSource().getMessage(code, args, defaultMessage, locale);
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        return getMessageSource().getMessage(code, args, locale);
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        return getMessageSource().getMessage(resolvable, locale);
    }

    private MessageSource getMessageSource() throws IllegalStateException {
        if (this.messageSource == null) {
            throw new IllegalStateException("MessageSource not initialized - " +
                    "call 'refresh' before accessing messages via the context: " + this);
        }
        return this.messageSource;
    }

    protected MessageSource getInternalParentMessageSource() {
        return (getParent() instanceof AbstractApplicationContext abstractApplicationContext ?
                abstractApplicationContext.messageSource : getParent());
    }


    //---------------------------------------------------------------------
    // ResourcePatternResolver实现
    //---------------------------------------------------------------------

    @Override
    public Resource[] getResources(String locationPattern) throws IOException {
        return this.resourcePatternResolver.getResources(locationPattern);
    }


    //---------------------------------------------------------------------
    // Lifecycle实现
    //---------------------------------------------------------------------

    @Override
    public void start() {
        getLifecycleProcessor().start();
        publishEvent(new ContextStartedEvent(this));
    }

    @Override
    public void stop() {
        getLifecycleProcessor().stop();
        publishEvent(new ContextStoppedEvent(this));
    }

    @Override
    public boolean isRunning() {
        return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
    }


    //---------------------------------------------------------------------
    // 子类必须实现的方法
    //---------------------------------------------------------------------

    protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

    protected abstract void closeBeanFactory();

    //获取BeanFactory, 子类实现
    @Override
    public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getDisplayName());
        sb.append(", started on ").append(new Date(getStartupDate()));
        ApplicationContext parent = getParent();
        if (parent != null) {
            sb.append(", parent: ").append(parent.getDisplayName());
        }
        return sb.toString();
    }
}
