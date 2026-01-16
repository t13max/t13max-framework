package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.*;
import com.t13max.ioc.beans.factory.config.SingletonBeanRegistry;
import com.t13max.ioc.core.SimpleAliasRegistry;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 单例注册表默认实现
 *
 * @Author: t13max
 * @Since: 22:43 2026/1/15
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

    private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;

    //单例创建锁
    final Lock singletonLock = new ReentrantLock();

    //单例 一级缓存
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

    //单例 三级缓存(对象工厂)
    private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>(16);

    private final Map<String, Consumer<Object>> singletonCallbacks = new ConcurrentHashMap<>(16);

    //单例 二级缓存(早期bean)
    private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

    private final Set<String> registeredSingletons = Collections.synchronizedSet(new LinkedHashSet<>(256));

    private final Set<String> singletonsCurrentlyInCreation = ConcurrentHashMap.newKeySet(16);

    private final Set<String> inCreationCheckExclusions = ConcurrentHashMap.newKeySet(16);

    private final Lock lenientCreationLock = new ReentrantLock();

    private final Condition lenientCreationFinished = this.lenientCreationLock.newCondition();

    private final Set<String> singletonsInLenientCreation = new HashSet<>();

    private final Map<Thread, Thread> lenientWaitingThreads = new HashMap<>();

    private final Map<String, Thread> currentCreationThreads = new ConcurrentHashMap<>();

    private volatile boolean singletonsCurrentlyInDestruction = false;

    private Set<Exception> suppressedExceptions;

    private final Map<String, DisposableBean> disposableBeans = new LinkedHashMap<>();

    private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);
    //依赖映射集合
    private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

    private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


    @Override
    public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
        Assert.notNull(beanName, "Bean name must not be null");
        Assert.notNull(singletonObject, "Singleton object must not be null");
        this.singletonLock.lock();
        try {
            addSingleton(beanName, singletonObject);
        } finally {
            this.singletonLock.unlock();
        }
    }

    protected void addSingleton(String beanName, Object singletonObject) {
        Object oldObject = this.singletonObjects.putIfAbsent(beanName, singletonObject);
        if (oldObject != null) {
            throw new IllegalStateException("Could not register object [" + singletonObject + "] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
        }
        this.singletonFactories.remove(beanName);
        this.earlySingletonObjects.remove(beanName);
        this.registeredSingletons.add(beanName);

        Consumer<Object> callback = this.singletonCallbacks.get(beanName);
        if (callback != null) {
            callback.accept(singletonObject);
        }
    }

    protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(singletonFactory, "Singleton factory must not be null");
        this.singletonFactories.put(beanName, singletonFactory);
        this.earlySingletonObjects.remove(beanName);
        this.registeredSingletons.add(beanName);
    }

    @Override
    public void addSingletonCallback(String beanName, Consumer<Object> singletonConsumer) {
        this.singletonCallbacks.put(beanName, singletonConsumer);
    }

    //获取单例bean
    @Override
    public Object getSingleton(String beanName) {
        return getSingleton(beanName, true);
    }

    protected Object getSingleton(String beanName, boolean allowEarlyReference) {
        // 尝试从单例缓存中获取
        Object singletonObject = this.singletonObjects.get(beanName);
        //为空 && 正在创建
        if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
            //从二级缓存获取
            singletonObject = this.earlySingletonObjects.get(beanName);
            //为空 && 允许提前应用
            if (singletonObject == null && allowEarlyReference) {
                //加锁
                if (!this.singletonLock.tryLock()) {
                    // 避免在Bean原始创建线程之外,过早推断单例Bean
                    return null;
                }
                try {
                    // 双重检测
                    singletonObject = this.singletonObjects.get(beanName);
                    //一级缓存为空
                    if (singletonObject == null) {
                        singletonObject = this.earlySingletonObjects.get(beanName);
                        //二级缓存为空
                        if (singletonObject == null) {
                            ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                            //三级缓存不为空
                            if (singletonFactory != null) {
                                singletonObject = singletonFactory.getObject();
                                //从三级缓存拿出来
                                if (this.singletonFactories.remove(beanName) != null) {
                                    //放入二级缓存
                                    this.earlySingletonObjects.put(beanName, singletonObject);
                                } else {
                                    //没remove掉 兜底从一级缓存拿
                                    singletonObject = this.singletonObjects.get(beanName);
                                }
                            }
                        }
                    }
                } finally {
                    this.singletonLock.unlock();
                }
            }
        }
        return singletonObject;
    }

    @SuppressWarnings("NullAway") // Dataflow analysis limitation
    public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(beanName, "Bean name must not be null");

        Thread currentThread = Thread.currentThread();
        Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
        boolean acquireLock = !Boolean.FALSE.equals(lockFlag);
        boolean locked = (acquireLock && this.singletonLock.tryLock());

        try {
            Object singletonObject = this.singletonObjects.get(beanName);
            if (singletonObject == null) {
                if (acquireLock && !locked) {
                    if (Boolean.TRUE.equals(lockFlag)) {
                        // Another thread is busy in a singleton factory callback, potentially blocked.
                        // Fallback as of 6.2: process given singleton bean outside of singleton lock.
                        // Thread-safe exposure is still guaranteed, there is just a risk of collisions
                        // when triggering creation of other beans as dependencies of the current bean.
                        this.lenientCreationLock.lock();
                        try {
                            if (logger.isInfoEnabled()) {
                                Set<String> lockedBeans = new HashSet<>(this.singletonsCurrentlyInCreation);
                                lockedBeans.removeAll(this.singletonsInLenientCreation);
                                logger.info("Obtaining singleton bean '" + beanName + "' in thread \"" +
                                        currentThread.getName() + "\" while other thread holds singleton " +
                                        "lock for other beans " + lockedBeans);
                            }
                            this.singletonsInLenientCreation.add(beanName);
                        } finally {
                            this.lenientCreationLock.unlock();
                        }
                    } else {
                        // No specific locking indication (outside a coordinated bootstrap) and
                        // singleton lock currently held by some other creation method -> wait.
                        this.singletonLock.lock();
                        locked = true;
                        // Singleton object might have possibly appeared in the meantime.
                        singletonObject = this.singletonObjects.get(beanName);
                        if (singletonObject != null) {
                            return singletonObject;
                        }
                    }
                }

                if (this.singletonsCurrentlyInDestruction) {
                    throw new BeanCreationNotAllowedException(beanName, "Singleton bean creation not allowed while singletons of this factory are in destruction " + "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Creating shared instance of singleton bean '{}'", beanName);
                }

                try {
                    beforeSingletonCreation(beanName);
                } catch (BeanCurrentlyInCreationException ex) {
                    this.lenientCreationLock.lock();
                    try {
                        while ((singletonObject = this.singletonObjects.get(beanName)) == null) {
                            Thread otherThread = this.currentCreationThreads.get(beanName);
                            if (otherThread != null && (otherThread == currentThread ||
                                    checkDependentWaitingThreads(otherThread, currentThread))) {
                                throw ex;
                            }
                            if (!this.singletonsInLenientCreation.contains(beanName)) {
                                break;
                            }
                            if (otherThread != null) {
                                this.lenientWaitingThreads.put(currentThread, otherThread);
                            }
                            try {
                                this.lenientCreationFinished.await();
                            } catch (InterruptedException ie) {
                                currentThread.interrupt();
                            } finally {
                                if (otherThread != null) {
                                    this.lenientWaitingThreads.remove(currentThread);
                                }
                            }
                        }
                    } finally {
                        this.lenientCreationLock.unlock();
                    }
                    if (singletonObject != null) {
                        return singletonObject;
                    }
                    if (locked) {
                        throw ex;
                    }
                    // Try late locking for waiting on specific bean to be finished.
                    this.singletonLock.lock();
                    locked = true;
                    // Lock-created singleton object should have appeared in the meantime.
                    singletonObject = this.singletonObjects.get(beanName);
                    if (singletonObject != null) {
                        return singletonObject;
                    }
                    beforeSingletonCreation(beanName);
                }

                boolean newSingleton = false;
                boolean recordSuppressedExceptions = (locked && this.suppressedExceptions == null);
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = new LinkedHashSet<>();
                }
                try {
                    // Leniently created singleton object could have appeared in the meantime.
                    singletonObject = this.singletonObjects.get(beanName);
                    if (singletonObject == null) {
                        this.currentCreationThreads.put(beanName, currentThread);
                        try {
                            singletonObject = singletonFactory.getObject();
                        } finally {
                            this.currentCreationThreads.remove(beanName);
                        }
                        newSingleton = true;
                    }
                } catch (IllegalStateException ex) {
                    // Has the singleton object implicitly appeared in the meantime ->
                    // if yes, proceed with it since the exception indicates that state.
                    singletonObject = this.singletonObjects.get(beanName);
                    if (singletonObject == null) {
                        throw ex;
                    }
                } catch (BeanCreationException ex) {
                    if (recordSuppressedExceptions) {
                        for (Exception suppressedException : this.suppressedExceptions) {
                            ex.addRelatedCause(suppressedException);
                        }
                    }
                    throw ex;
                } finally {
                    if (recordSuppressedExceptions) {
                        this.suppressedExceptions = null;
                    }
                    afterSingletonCreation(beanName);
                }

                if (newSingleton) {
                    try {
                        addSingleton(beanName, singletonObject);
                    } catch (IllegalStateException ex) {
                        // Leniently accept same instance if implicitly appeared.
                        Object object = this.singletonObjects.get(beanName);
                        if (singletonObject != object) {
                            throw ex;
                        }
                    }
                }
            }
            return singletonObject;
        } finally {
            if (locked) {
                this.singletonLock.unlock();
            }
            this.lenientCreationLock.lock();
            try {
                this.singletonsInLenientCreation.remove(beanName);
                this.lenientWaitingThreads.entrySet().removeIf(
                        entry -> entry.getValue() == currentThread);
                this.lenientCreationFinished.signalAll();
            } finally {
                this.lenientCreationLock.unlock();
            }
        }
    }

    private boolean checkDependentWaitingThreads(Thread waitingThread, Thread candidateThread) {
        Thread threadToCheck = waitingThread;
        while ((threadToCheck = this.lenientWaitingThreads.get(threadToCheck)) != null) {
            if (threadToCheck == candidateThread) {
                return true;
            }
        }
        return false;
    }

    //当前线程能否持有单例锁
    protected Boolean isCurrentThreadAllowedToHoldSingletonLock() {
        return null;
    }

    protected void onSuppressedException(Exception ex) {
        if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
            this.suppressedExceptions.add(ex);
        }
    }

    protected void removeSingleton(String beanName) {
        this.singletonObjects.remove(beanName);
        this.singletonFactories.remove(beanName);
        this.earlySingletonObjects.remove(beanName);
        this.registeredSingletons.remove(beanName);
    }

    //是否已经包含这个bean
    @Override
    public boolean containsSingleton(String beanName) {
        return this.singletonObjects.containsKey(beanName);
    }

    @Override
    public String[] getSingletonNames() {
        return StringUtils.toStringArray(this.registeredSingletons);
    }

    @Override
    public int getSingletonCount() {
        return this.registeredSingletons.size();
    }


    public void setCurrentlyInCreation(String beanName, boolean inCreation) {
        Assert.notNull(beanName, "Bean name must not be null");
        if (!inCreation) {
            this.inCreationCheckExclusions.add(beanName);
        } else {
            this.inCreationCheckExclusions.remove(beanName);
        }
    }

    public boolean isCurrentlyInCreation(String beanName) {
        Assert.notNull(beanName, "Bean name must not be null");
        return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
    }

    protected boolean isActuallyInCreation(String beanName) {
        return isSingletonCurrentlyInCreation(beanName);
    }

    public boolean isSingletonCurrentlyInCreation(String beanName) {
        return this.singletonsCurrentlyInCreation.contains(beanName);
    }

    //创建前验证
    protected void beforeSingletonCreation(String beanName) {
        //排除的bean是否包含, 添加到当前正在初始化集合
        if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }
    }

    //创建后验证
    protected void afterSingletonCreation(String beanName) {
        //排除的bean是否包含, 移除出当前正在初始化集合
        if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
            throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
        }
    }


    public void registerDisposableBean(String beanName, DisposableBean bean) {
        synchronized (this.disposableBeans) {
            this.disposableBeans.put(beanName, bean);
        }
    }

    public void registerContainedBean(String containedBeanName, String containingBeanName) {
        synchronized (this.containedBeanMap) {
            Set<String> containedBeans =
                    this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
            if (!containedBeans.add(containedBeanName)) {
                return;
            }
        }
        registerDependentBean(containedBeanName, containingBeanName);
    }

    //注册依赖关系
    public void registerDependentBean(String beanName, String dependentBeanName) {

        //别名
        String canonicalName = canonicalName(beanName);

        //存入依赖映射
        synchronized (this.dependentBeanMap) {
            Set<String> dependentBeans = this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
            if (!dependentBeans.add(dependentBeanName)) {
                return;
            }
        }

        synchronized (this.dependenciesForBeanMap) {
            Set<String> dependenciesForBean = this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
            dependenciesForBean.add(canonicalName);
        }
    }

    //是否存在依赖关系
    protected boolean isDependent(String beanName, String dependentBeanName) {
        synchronized (this.dependentBeanMap) {
            return isDependent(beanName, dependentBeanName, null);
        }
    }

    private boolean isDependent(String beanName, String dependentBeanName, Set<String> alreadySeen) {
        if (alreadySeen != null && alreadySeen.contains(beanName)) {
            return false;
        }
        //别名
        String canonicalName = canonicalName(beanName);
        Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
        if (dependentBeans == null || dependentBeans.isEmpty()) {
            return false;
        }
        if (dependentBeans.contains(dependentBeanName)) {
            return true;
        }
        if (alreadySeen == null) {
            alreadySeen = new HashSet<>();
        }
        alreadySeen.add(beanName);
        for (String transitiveDependency : dependentBeans) {
            if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasDependentBean(String beanName) {
        return this.dependentBeanMap.containsKey(beanName);
    }

    public String[] getDependentBeans(String beanName) {
        Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
        if (dependentBeans == null) {
            return new String[0];
        }
        synchronized (this.dependentBeanMap) {
            return StringUtils.toStringArray(dependentBeans);
        }
    }

    public String[] getDependenciesForBean(String beanName) {
        Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
        if (dependenciesForBean == null) {
            return new String[0];
        }
        synchronized (this.dependenciesForBeanMap) {
            return StringUtils.toStringArray(dependenciesForBean);
        }
    }

    public void destroySingletons() {
        if (logger.isTraceEnabled()) {
            logger.trace("Destroying singletons in " + this);
        }
        this.singletonsCurrentlyInDestruction = true;

        String[] disposableBeanNames;
        synchronized (this.disposableBeans) {
            disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
        }
        for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
            destroySingleton(disposableBeanNames[i]);
        }

        this.containedBeanMap.clear();
        this.dependentBeanMap.clear();
        this.dependenciesForBeanMap.clear();

        this.singletonLock.lock();
        try {
            clearSingletonCache();
        } finally {
            this.singletonLock.unlock();
        }
    }

    protected void clearSingletonCache() {
        this.singletonObjects.clear();
        this.singletonFactories.clear();
        this.earlySingletonObjects.clear();
        this.registeredSingletons.clear();
        this.singletonsCurrentlyInDestruction = false;
    }

    public void destroySingleton(String beanName) {
        // Destroy the corresponding DisposableBean instance.
        // This also triggers the destruction of dependent beans.
        DisposableBean disposableBean;
        synchronized (this.disposableBeans) {
            disposableBean = this.disposableBeans.remove(beanName);
        }
        destroyBean(beanName, disposableBean);

        // destroySingletons() removes all singleton instances at the end,
        // leniently tolerating late retrieval during the shutdown phase.
        if (!this.singletonsCurrentlyInDestruction) {
            // For an individual destruction, remove the registered instance now.
            // As of 6.2, this happens after the current bean's destruction step,
            // allowing for late bean retrieval by on-demand suppliers etc.
            if (this.currentCreationThreads.get(beanName) == Thread.currentThread()) {
                // Local remove after failed creation step -> without singleton lock
                // since bean creation may have happened leniently without any lock.
                removeSingleton(beanName);
            } else {
                this.singletonLock.lock();
                try {
                    removeSingleton(beanName);
                } finally {
                    this.singletonLock.unlock();
                }
            }
        }
    }

    protected void destroyBean(String beanName, DisposableBean bean) {
        // Trigger destruction of dependent beans first...
        Set<String> dependentBeanNames;
        synchronized (this.dependentBeanMap) {
            // Within full synchronization in order to guarantee a disconnected Set
            dependentBeanNames = this.dependentBeanMap.remove(beanName);
        }
        if (dependentBeanNames != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependentBeanNames);
            }
            for (String dependentBeanName : dependentBeanNames) {
                destroySingleton(dependentBeanName);
            }
        }

        // Actually destroy the bean now...
        if (bean != null) {
            try {
                bean.destroy();
            } catch (Throwable ex) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
                }
            }
        }

        // Trigger destruction of contained beans...
        Set<String> containedBeans;
        synchronized (this.containedBeanMap) {
            // Within full synchronization in order to guarantee a disconnected Set
            containedBeans = this.containedBeanMap.remove(beanName);
        }
        if (containedBeans != null) {
            for (String containedBeanName : containedBeans) {
                destroySingleton(containedBeanName);
            }
        }

        // Remove destroyed bean from other beans' dependencies.
        synchronized (this.dependentBeanMap) {
            for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Set<String>> entry = it.next();
                Set<String> dependenciesToClean = entry.getValue();
                dependenciesToClean.remove(beanName);
                if (dependenciesToClean.isEmpty()) {
                    it.remove();
                }
            }
        }

        // Remove destroyed bean's prepared dependency information.
        this.dependenciesForBeanMap.remove(beanName);
    }

}
