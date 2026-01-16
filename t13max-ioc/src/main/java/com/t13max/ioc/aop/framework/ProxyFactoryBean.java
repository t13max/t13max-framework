package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.TargetSource;
import com.t13max.ioc.aop.framework.adapter.AdvisorAdapterRegistry;
import com.t13max.ioc.aop.framework.adapter.UnknownAdviceTypeException;
import com.t13max.ioc.aop.intecept.Interceptor;
import com.t13max.ioc.aop.target.SingletonTargetSource;
import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.*;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author t13max
 * @since 16:26 2026/1/16
 */
public class ProxyFactoryBean extends ProxyCreatorSupport implements FactoryBean<Object>, BeanClassLoaderAware, BeanFactoryAware {

    public final static String GLOBAL_SUFFIX = "*";

    protected final Logger logger = LogManager.getLogger(getClass());
    private String[] interceptorNames;
    private String targetName;

    private boolean autodetectInterfaces = true;

    private boolean singleton = true;

    private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

    private boolean freezeProxy = false;
    private transient ClassLoader proxyClassLoader = ClassUtils.getDefaultClassLoader();

    private transient boolean classLoaderConfigured = false;
    private transient BeanFactory beanFactory;
    private boolean advisorChainInitialized = false;

    private Object singletonInstance;

    public void setProxyInterfaces(Class<?>[] proxyInterfaces) throws ClassNotFoundException {
        setInterfaces(proxyInterfaces);
    }

    public void setInterceptorNames(String... interceptorNames) {
        this.interceptorNames = interceptorNames;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public void setAutodetectInterfaces(boolean autodetectInterfaces) {
        this.autodetectInterfaces = autodetectInterfaces;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
        this.advisorAdapterRegistry = advisorAdapterRegistry;
    }

    @Override
    public void setFrozen(boolean frozen) {
        this.freezeProxy = frozen;
    }

    public void setProxyClassLoader(ClassLoader classLoader) {
        this.proxyClassLoader = classLoader;
        this.classLoaderConfigured = (classLoader != null);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        if (!this.classLoaderConfigured) {
            this.proxyClassLoader = classLoader;
        }
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        checkInterceptorNames();
    }

    //返回一个代理对象, 从FactoryBean中获取bean时调用创建此工厂要返回的AOP代理的实例, 该实例将作为一个单例被缓存
    @Override
    public Object getObject() throws BeansException {
        //初始化通知器链
        initializeAdvisorChain();
        //单例
        if (isSingleton()) {
            return getSingletonInstance();
        } else {
            if (this.targetName == null) {
                logger.info("Using non-singleton proxies with singleton targets is often undesirable. " + "Enable prototype proxies by setting the 'targetName' property.");
            }
            return newPrototypeInstance();
        }
    }

    @Override
    public Class<?> getObjectType() {
        synchronized (this) {
            if (this.singletonInstance != null) {
                return this.singletonInstance.getClass();
            }
        }
        try {
            // This might be incomplete since it potentially misses introduced interfaces
            // from Advisors that will be lazily retrieved via setInterceptorNames.
            return createAopProxy().getProxyClass(this.proxyClassLoader);
        } catch (AopConfigException ex) {
            if (getTargetClass() == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to determine early proxy class: " + ex.getMessage());
                }
                return null;
            } else {
                throw ex;
            }
        }
    }

    @Override
    public boolean isSingleton() {
        return this.singleton;
    }

    // 返回此类代理对象的单例实例,如果尚未创建该实例,则单例地创建它
    private synchronized Object getSingletonInstance() {
        if (this.singletonInstance == null) {
            this.targetSource = freshTargetSource();
            if (this.autodetectInterfaces && getProxiedInterfaces().length == 0 && !isProxyTargetClass()) {
                // 根据AOP框架来判断需要代理的接口
                Class<?> targetClass = getTargetClass();
                if (targetClass == null) {
                    throw new FactoryBeanNotInitializedException("Cannot determine target class for proxy");
                }
                setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
            }
            // Initialize the shared singleton instance.
            super.setFrozen(this.freezeProxy);
            //这里会通过AopProxy来得到代理对象
            this.singletonInstance = getProxy(createAopProxy());
        }
        return this.singletonInstance;
    }

    private synchronized Object newPrototypeInstance() {
        // In the case of a prototype, we need to give the proxy
        // an independent instance of the configuration.
        // In this case, no proxy will have an instance of this object's configuration,
        // but will have an independent copy.
        ProxyCreatorSupport copy = new ProxyCreatorSupport(getAopProxyFactory());

        // The copy needs a fresh advisor chain, and a fresh TargetSource.
        TargetSource targetSource = freshTargetSource();
        copy.copyConfigurationFrom(this, targetSource, freshAdvisorChain());
        if (this.autodetectInterfaces && getProxiedInterfaces().length == 0 && !isProxyTargetClass()) {
            // Rely on AOP infrastructure to tell us what interfaces to proxy.
            Class<?> targetClass = targetSource.getTargetClass();
            if (targetClass != null) {
                copy.setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
            }
        }
        copy.setFrozen(this.freezeProxy);

        return getProxy(copy.createAopProxy());
    }

    //通过createAopProxy()方法返回的aopProxy获取代理对象, 可以重写使用自定义ClassLoader
    protected Object getProxy(AopProxy aopProxy) {
        return aopProxy.getProxy(this.proxyClassLoader);
    }

    private void checkInterceptorNames() {
        if (!ObjectUtils.isEmpty(this.interceptorNames)) {
            String finalName = this.interceptorNames[this.interceptorNames.length - 1];
            if (this.targetName == null && this.targetSource == EMPTY_TARGET_SOURCE) {
                // The last name in the chain may be an Advisor/Advice or a target/TargetSource.
                // Unfortunately we don't know; we must look at type of the bean.
                if (!finalName.endsWith(GLOBAL_SUFFIX) && !isNamedBeanAnAdvisorOrAdvice(finalName)) {
                    // The target isn't an interceptor.
                    this.targetName = finalName;
                    if (logger.isDebugEnabled()) {
                        logger.debug("Bean with name '" + finalName + "' concluding interceptor chain " +
                                "is not an advisor class: treating it as a target or TargetSource");
                    }
                    this.interceptorNames = Arrays.copyOf(this.interceptorNames, this.interceptorNames.length - 1);
                }
            }
        }
    }

    private boolean isNamedBeanAnAdvisorOrAdvice(String beanName) {
        Assert.state(this.beanFactory != null, "No BeanFactory set");
        Class<?> namedBeanClass = this.beanFactory.getType(beanName);
        if (namedBeanClass != null) {
            return (Advisor.class.isAssignableFrom(namedBeanClass) || Advice.class.isAssignableFrom(namedBeanClass));
        }
        // Treat it as a target bean if we can't tell.
        if (logger.isDebugEnabled()) {
            logger.debug("Could not determine type of bean with name '" + beanName +
                    "' - assuming it is neither an Advisor nor an Advice");
        }
        return false;
    }

    //初始化Advisor链，advisor通知器也是从Ioc容器获取
    private synchronized void initializeAdvisorChain() throws AopConfigException, BeansException {
        if (!this.advisorChainInitialized && !ObjectUtils.isEmpty(this.interceptorNames)) {
            if (this.beanFactory == null) {
                throw new IllegalStateException("No BeanFactory available anymore (probably due to serialization) " + "- cannot resolve interceptor names " + Arrays.toString(this.interceptorNames));
            }

            // Globals can't be last unless we specified a targetSource using the property...
            if (this.interceptorNames[this.interceptorNames.length - 1].endsWith(GLOBAL_SUFFIX) &&
                    this.targetName == null && this.targetSource == EMPTY_TARGET_SOURCE) {
                throw new AopConfigException("Target required after globals");
            }

            // Advisor链的调用
            for (String name : this.interceptorNames) {
                if (name.endsWith(GLOBAL_SUFFIX)) {
                    if (!(this.beanFactory instanceof ListableBeanFactory lbf)) {
                        throw new AopConfigException("Can only use global advisors or interceptors with a ListableBeanFactory");
                    }
                    addGlobalAdvisors(lbf, name.substring(0, name.length() - GLOBAL_SUFFIX.length()));
                } else {
                    // 对当前的factoryBean进行类型判断, 是属于单例还是原型
                    Object advice;
                    if (this.singleton || this.beanFactory.isSingleton(name)) {
                        // 从容器获取advice
                        advice = this.beanFactory.getBean(name);
                    } else {
                        //原型
                        advice = new PrototypePlaceholderAdvisor(name);
                    }
                    addAdvisorOnChainCreation(advice);
                }
            }

            this.advisorChainInitialized = true;
        }
    }

    private List<Advisor> freshAdvisorChain() {
        Advisor[] advisors = getAdvisors();
        List<Advisor> freshAdvisors = new ArrayList<>(advisors.length);
        for (Advisor advisor : advisors) {
            if (advisor instanceof PrototypePlaceholderAdvisor ppa) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Refreshing bean named '" + ppa.getBeanName() + "'");
                }
                // Replace the placeholder with a fresh prototype instance resulting from a getBean lookup
                if (this.beanFactory == null) {
                    throw new IllegalStateException("No BeanFactory available anymore (probably due to " +
                            "serialization) - cannot resolve prototype advisor '" + ppa.getBeanName() + "'");
                }
                Object bean = this.beanFactory.getBean(ppa.getBeanName());
                Advisor refreshedAdvisor = namedBeanToAdvisor(bean);
                freshAdvisors.add(refreshedAdvisor);
            } else {
                // Add the shared instance.
                freshAdvisors.add(advisor);
            }
        }
        return freshAdvisors;
    }

    private void addGlobalAdvisors(ListableBeanFactory beanFactory, String prefix) {
        String[] globalAdvisorNames =
                BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Advisor.class);
        String[] globalInterceptorNames =
                BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Interceptor.class);
        if (globalAdvisorNames.length > 0 || globalInterceptorNames.length > 0) {
            List<Object> beans = new ArrayList<>(globalAdvisorNames.length + globalInterceptorNames.length);
            for (String name : globalAdvisorNames) {
                if (name.startsWith(prefix)) {
                    beans.add(beanFactory.getBean(name));
                }
            }
            for (String name : globalInterceptorNames) {
                if (name.startsWith(prefix)) {
                    beans.add(beanFactory.getBean(name));
                }
            }
            AnnotationAwareOrderComparator.sort(beans);
            for (Object bean : beans) {
                addAdvisorOnChainCreation(bean);
            }
        }
    }

    private void addAdvisorOnChainCreation(Object next) {
        // We need to convert to an Advisor if necessary so that our source reference
        // matches what we find from superclass interceptors.
        addAdvisor(namedBeanToAdvisor(next));
    }

    private TargetSource freshTargetSource() {
        if (this.targetName == null) {
            // Not refreshing target: bean name not specified in 'interceptorNames'
            return this.targetSource;
        } else {
            if (this.beanFactory == null) {
                throw new IllegalStateException("No BeanFactory available anymore (probably due to serialization) " +
                        "- cannot resolve target with name '" + this.targetName + "'");
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Refreshing target with name '" + this.targetName + "'");
            }
            Object target = this.beanFactory.getBean(this.targetName);
            return (target instanceof TargetSource targetSource ? targetSource : new SingletonTargetSource(target));
        }
    }

    private Advisor namedBeanToAdvisor(Object next) {
        try {
            return this.advisorAdapterRegistry.wrap(next);
        } catch (UnknownAdviceTypeException ex) {
            // We expected this to be an Advisor or Advice,
            // but it wasn't. This is a configuration error.
            throw new AopConfigException("Unknown advisor type " + next.getClass() +
                    "; can only include Advisor or Advice type beans in interceptorNames chain " +
                    "except for last entry which may also be target instance or TargetSource", ex);
        }
    }

    @Override
    protected void adviceChanged() {
        super.adviceChanged();
        if (this.singleton) {
            logger.debug("Advice has changed; re-caching singleton instance");
            synchronized (this) {
                this.singletonInstance = null;
            }
        }
    }


    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Rely on default serialization; just initialize state after deserialization.
        ois.defaultReadObject();

        // Initialize transient fields.
        this.proxyClassLoader = ClassUtils.getDefaultClassLoader();
    }

    private static class PrototypePlaceholderAdvisor implements Advisor, Serializable {

        private final String beanName;

        private final String message;

        public PrototypePlaceholderAdvisor(String beanName) {
            this.beanName = beanName;
            this.message = "Placeholder for prototype Advisor/Advice with bean name '" + beanName + "'";
        }

        public String getBeanName() {
            return this.beanName;
        }

        @Override
        public Advice getAdvice() {
            throw new UnsupportedOperationException("Cannot invoke methods: " + this.message);
        }

        @Override
        public String toString() {
            return this.message;
        }
    }

}
