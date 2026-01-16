package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.framework.adapter.AdvisorAdapterRegistry;
import com.t13max.ioc.beans.factory.FactoryBean;
import com.t13max.ioc.beans.factory.FactoryBeanNotInitializedException;
import com.t13max.ioc.utils.ClassUtils;

/**
 * @author t13max
 * @since 18:02 2026/1/16
 */
public class AbstractSingletonProxyFactoryBean extends ProxyConfig implements FactoryBean<Object>, BeanClassLoaderAware, InitializingBean {

    private Object target;

    private Class<?>[] proxyInterfaces;
    
    private Object[] preInterceptors;

    private Object[] postInterceptors;

    //全局注册中心
    private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

    private transient ClassLoader proxyClassLoader;

    private Object proxy;

    public void setTarget(Object target) {
        this.target = target;
    }

    /**
     * Specify the set of interfaces being proxied.
     * <p>If not specified (the default), the AOP infrastructure works
     * out which interfaces need proxying by analyzing the target,
     * proxying all the interfaces that the target object implements.
     */
    public void setProxyInterfaces(Class<?>[] proxyInterfaces) {
        this.proxyInterfaces = proxyInterfaces;
    }

    /**
     * Set additional interceptors (or advisors) to be applied before the
     * implicit transaction interceptor, e.g. a PerformanceMonitorInterceptor.
     * <p>You may specify any AOP Alliance MethodInterceptors or other
     * Spring AOP Advices, as well as Spring AOP Advisors.
     * @see org.springframework.aop.interceptor.PerformanceMonitorInterceptor
     */
    public void setPreInterceptors(Object[] preInterceptors) {
        this.preInterceptors = preInterceptors;
    }

    /**
     * Set additional interceptors (or advisors) to be applied after the
     * implicit transaction interceptor.
     * <p>You may specify any AOP Alliance MethodInterceptors or other
     * Spring AOP Advices, as well as Spring AOP Advisors.
     */
    public void setPostInterceptors(Object[] postInterceptors) {
        this.postInterceptors = postInterceptors;
    }

    /**
     * Specify the AdvisorAdapterRegistry to use.
     * Default is the global AdvisorAdapterRegistry.
     * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
     */
    public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
        this.advisorAdapterRegistry = advisorAdapterRegistry;
    }

    /**
     * Set the ClassLoader to generate the proxy class in.
     * <p>Default is the bean ClassLoader, i.e. the ClassLoader used by the
     * containing BeanFactory for loading all bean classes. This can be
     * overridden here for specific proxies.
     */
    public void setProxyClassLoader(ClassLoader classLoader) {
        this.proxyClassLoader = classLoader;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        if (this.proxyClassLoader == null) {
            this.proxyClassLoader = classLoader;
        }
    }

    //处理完成AOP配置,创建ProxyFactory对象,为其生成代理对象,配置通知器,设置代理接口方法
    @Override
    public void afterPropertiesSet() {
        //检验目标对象
        if (this.target == null) {
            throw new IllegalArgumentException("Property 'target' is required");
        }
        if (this.target instanceof String) {
            throw new IllegalArgumentException("'target' needs to be a bean reference, not a bean name as value");
        }
        if (this.proxyClassLoader == null) {
            this.proxyClassLoader = ClassUtils.getDefaultClassLoader();
        }

        //使用ProxyFactory完成AOP的基本功能,ProxyFactory提供proxy对象并将TransactionIntercepter设为target方法调用的拦截器
        ProxyFactory proxyFactory = new ProxyFactory();

        if (this.preInterceptors != null) {
            for (Object interceptor : this.preInterceptors) {
                proxyFactory.addAdvisor(this.advisorAdapterRegistry.wrap(interceptor));
            }
        }

        // 加入Advisor通知 DefaultPointcutAdvisor/TransactionAttributeSourceAdvisor
        proxyFactory.addAdvisor(this.advisorAdapterRegistry.wrap(createMainInterceptor()));

        if (this.postInterceptors != null) {
            for (Object interceptor : this.postInterceptors) {
                proxyFactory.addAdvisor(this.advisorAdapterRegistry.wrap(interceptor));
            }
        }

        proxyFactory.copyFrom(this);
        //这里创建AOP的目标源,与在其它地方使用ProxyFactory没什么差别
        TargetSource targetSource = createTargetSource(this.target);
        proxyFactory.setTargetSource(targetSource);

        if (this.proxyInterfaces != null) {
            proxyFactory.setInterfaces(this.proxyInterfaces);
        }
        else if (!isProxyTargetClass()) {
            // 需要根据AOP基础设施来确定使用哪个接口作为代理
            Class<?> targetClass = targetSource.getTargetClass();
            if (targetClass != null) {
                proxyFactory.setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
            }
        }

        postProcessProxyFactory(proxyFactory);

        //设置代理对象
        this.proxy = proxyFactory.getProxy(this.proxyClassLoader);
    }

    /**
     * Determine a TargetSource for the given target (or TargetSource).
     * @param target the target. If this is an implementation of TargetSource it is
     * used as our TargetSource; otherwise it is wrapped in a SingletonTargetSource.
     * @return a TargetSource for this object
     */
    protected TargetSource createTargetSource(Object target) {
        if (target instanceof TargetSource targetSource) {
            return targetSource;
        }
        else {
            return new SingletonTargetSource(target);
        }
    }

    /**
     * A hook for subclasses to post-process the {@link ProxyFactory}
     * before creating the proxy instance with it.
     * @param proxyFactory the AOP ProxyFactory about to be used
     * @since 4.2
     */
    protected void postProcessProxyFactory(ProxyFactory proxyFactory) {
    }


    @Override
    public Object getObject() {
        if (this.proxy == null) {
            throw new FactoryBeanNotInitializedException();
        }
        return this.proxy;
    }

    @Override
    
    public Class<?> getObjectType() {
        if (this.proxy != null) {
            return this.proxy.getClass();
        }
        if (this.proxyInterfaces != null && this.proxyInterfaces.length == 1) {
            return this.proxyInterfaces[0];
        }
        if (this.target instanceof TargetSource targetSource) {
            return targetSource.getTargetClass();
        }
        if (this.target != null) {
            return this.target.getClass();
        }
        return null;
    }

    @Override
    public final boolean isSingleton() {
        return true;
    }


    /**
     * Create the "main" interceptor for this proxy factory bean.
     * Typically an Advisor, but can also be any type of Advice.
     * <p>Pre-interceptors will be applied before, post-interceptors
     * will be applied after this interceptor.
     */
    protected abstract Object createMainInterceptor();
}
