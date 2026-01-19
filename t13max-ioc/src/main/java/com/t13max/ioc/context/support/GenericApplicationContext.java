package com.t13max.ioc.context.support;

import com.t13max.ioc.beans.BeanUtils;
import com.t13max.ioc.beans.factory.BeanDefinitionStoreException;
import com.t13max.ioc.beans.factory.BeanRegistrar;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;
import com.t13max.ioc.beans.factory.config.AutowireCapableBeanFactory;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinitionCustomizer;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.beans.factory.support.BeanDefinitionRegistry;
import com.t13max.ioc.beans.factory.support.BeanRegistryAdapter;
import com.t13max.ioc.beans.factory.support.DefaultListableBeanFactory;
import com.t13max.ioc.beans.factory.support.RootBeanDefinition;
import com.t13max.ioc.context.ApplicationContext;
import com.t13max.ioc.core.io.ProtocolResolver;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.io.support.ResourcePatternResolver;
import com.t13max.ioc.core.metrics.ApplicationStartup;
import com.t13max.ioc.util.Assert;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class GenericApplicationContext extends AbstractApplicationContext implements BeanDefinitionRegistry {

    private final DefaultListableBeanFactory beanFactory;

    private ResourceLoader resourceLoader;

    private boolean customClassLoader = false;

    private final AtomicBoolean refreshed = new AtomicBoolean();

    public GenericApplicationContext() {
        this.beanFactory = new DefaultListableBeanFactory();
    }

    public GenericApplicationContext(DefaultListableBeanFactory beanFactory) {
        Assert.notNull(beanFactory, "BeanFactory must not be null");
        this.beanFactory = beanFactory;
    }

    public GenericApplicationContext(ApplicationContext parent) {
        this();
        setParent(parent);
    }

    public GenericApplicationContext(DefaultListableBeanFactory beanFactory, ApplicationContext parent) {
        this(beanFactory);
        setParent(parent);
    }

    @Override
    public void setParent(ApplicationContext parent) {
        super.setParent(parent);
        this.beanFactory.setParentBeanFactory(getInternalParentBeanFactory());
    }

    @Override
    public void setApplicationStartup(ApplicationStartup applicationStartup) {
        super.setApplicationStartup(applicationStartup);
        this.beanFactory.setApplicationStartup(applicationStartup);
    }

    public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
        this.beanFactory.setAllowBeanDefinitionOverriding(allowBeanDefinitionOverriding);
    }

    public void setAllowCircularReferences(boolean allowCircularReferences) {
        this.beanFactory.setAllowCircularReferences(allowCircularReferences);
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    //---------------------------------------------------------------------
    // ResourceLoader / ResourcePatternResolver override if necessary
    //---------------------------------------------------------------------

    @Override
    public Resource getResource(String location) {
        if (this.resourceLoader != null) {
            for (ProtocolResolver protocolResolver : getProtocolResolvers()) {
                Resource resource = protocolResolver.resolve(location, this);
                if (resource != null) {
                    return resource;
                }
            }
            return this.resourceLoader.getResource(location);
        }
        return super.getResource(location);
    }

    @Override
    public Resource[] getResources(String locationPattern) throws IOException {
        if (this.resourceLoader instanceof ResourcePatternResolver resourcePatternResolver) {
            return resourcePatternResolver.getResources(locationPattern);
        }
        return super.getResources(locationPattern);
    }

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        super.setClassLoader(classLoader);
        this.customClassLoader = true;
    }

    @Override
    public ClassLoader getClassLoader() {
        if (this.resourceLoader != null && !this.customClassLoader) {
            return this.resourceLoader.getClassLoader();
        }
        return super.getClassLoader();
    }

    //---------------------------------------------------------------------
    // AbstractApplicationContext的模板方法实现
    //---------------------------------------------------------------------

    @Override
    protected final void refreshBeanFactory() throws IllegalStateException {
        if (!this.refreshed.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "GenericApplicationContext does not support multiple refresh attempts: just call 'refresh' once");
        }
        this.beanFactory.setSerializationId(getId());
    }

    @Override
    protected void cancelRefresh(Throwable ex) {
        this.beanFactory.setSerializationId(null);
        super.cancelRefresh(ex);
    }

    @Override
    protected final void closeBeanFactory() {
        this.beanFactory.setSerializationId(null);
    }

    @Override
    public final ConfigurableListableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    public final DefaultListableBeanFactory getDefaultListableBeanFactory() {
        return this.beanFactory;
    }

    @Override
    public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        assertBeanFactoryActive();
        return this.beanFactory;
    }

    //---------------------------------------------------------------------
    // BeanDefinitionRegistry实现
    //---------------------------------------------------------------------

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException {
        this.beanFactory.registerBeanDefinition(beanName, beanDefinition);
    }

    @Override
    public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        this.beanFactory.removeBeanDefinition(beanName);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        return this.beanFactory.getBeanDefinition(beanName);
    }

    @Override
    public boolean isBeanDefinitionOverridable(String beanName) {
        return this.beanFactory.isBeanDefinitionOverridable(beanName);
    }

    @Override
    public boolean isBeanNameInUse(String beanName) {
        return this.beanFactory.isBeanNameInUse(beanName);
    }

    @Override
    public void registerAlias(String beanName, String alias) {
        this.beanFactory.registerAlias(beanName, alias);
    }

    @Override
    public void removeAlias(String alias) {
        this.beanFactory.removeAlias(alias);
    }

    @Override
    public boolean isAlias(String beanName) {
        return this.beanFactory.isAlias(beanName);
    }

    //---------------------------------------------------------------------
    // AOT处理
    //---------------------------------------------------------------------

    /*public void refreshForAotProcessing(RuntimeHints runtimeHints) {
        if (logger.isDebugEnabled()) {
            logger.debug("Preparing bean factory for AOT processing");
        }
        prepareRefresh();
        obtainFreshBeanFactory();
        prepareBeanFactory(this.beanFactory);
        postProcessBeanFactory(this.beanFactory);
        invokeBeanFactoryPostProcessors(this.beanFactory);
        this.beanFactory.freezeConfiguration();
        PostProcessorRegistrationDelegate.invokeMergedBeanDefinitionPostProcessors(this.beanFactory);
        preDetermineBeanTypes(runtimeHints);
    }

    private void preDetermineBeanTypes(RuntimeHints runtimeHints) {
        List<String> singletons = new ArrayList<>();
        List<String> lazyBeans = new ArrayList<>();

        // First round: pre-registered singleton instances, if any.
        for (String beanName : this.beanFactory.getSingletonNames()) {
            Class<?> beanType = this.beanFactory.getType(beanName);
            if (beanType != null) {
                ClassHintUtils.registerProxyIfNecessary(beanType, runtimeHints);
            }
            singletons.add(beanName);
        }

        List<SmartInstantiationAwareBeanPostProcessor> bpps =
                PostProcessorRegistrationDelegate.loadBeanPostProcessors(
                        this.beanFactory, SmartInstantiationAwareBeanPostProcessor.class);

        // Second round: non-lazy singleton beans in definition order,
        // matching preInstantiateSingletons.
        for (String beanName : this.beanFactory.getBeanDefinitionNames()) {
            if (!singletons.contains(beanName)) {
                BeanDefinition bd = getBeanDefinition(beanName);
                if (bd.isSingleton() && !bd.isLazyInit()) {
                    preDetermineBeanType(beanName, bpps, runtimeHints);
                } else {
                    lazyBeans.add(beanName);
                }
            }
        }

        // Third round: lazy singleton beans and scoped beans.
        for (String beanName : lazyBeans) {
            preDetermineBeanType(beanName, bpps, runtimeHints);
        }
    }

    private void preDetermineBeanType(String beanName, List<SmartInstantiationAwareBeanPostProcessor> bpps, RuntimeHints runtimeHints) {

        Class<?> beanType = this.beanFactory.getType(beanName);
        if (beanType != null) {
            ClassHintUtils.registerProxyIfNecessary(beanType, runtimeHints);
            for (SmartInstantiationAwareBeanPostProcessor bpp : bpps) {
                Class<?> newBeanType = bpp.determineBeanType(beanType, beanName);
                if (newBeanType != beanType) {
                    ClassHintUtils.registerProxyIfNecessary(newBeanType, runtimeHints);
                    beanType = newBeanType;
                }
            }
        }
    }*/

    //---------------------------------------------------------------------
    // 注册bean的方法
    //---------------------------------------------------------------------

    public <T> void registerBean(Class<T> beanClass, Object... constructorArgs) {
        registerBean(null, beanClass, constructorArgs);
    }

    public <T> void registerBean(String beanName, Class<T> beanClass, Object... constructorArgs) {
        registerBean(beanName, beanClass, (Supplier<T>) null,
                bd -> {
                    for (Object arg : constructorArgs) {
                        bd.getConstructorArgumentValues().addGenericArgumentValue(arg);
                    }
                });
    }

    public final <T> void registerBean(Class<T> beanClass, BeanDefinitionCustomizer... customizers) {
        registerBean(null, beanClass, null, customizers);
    }

    public final <T> void registerBean(
            String beanName, Class<T> beanClass, BeanDefinitionCustomizer... customizers) {

        registerBean(beanName, beanClass, null, customizers);
    }

    public final <T> void registerBean(
            Class<T> beanClass, Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

        registerBean(null, beanClass, supplier, customizers);
    }

    public <T> void registerBean(String beanName, Class<T> beanClass,
                                 Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

        ClassDerivedBeanDefinition beanDefinition = new ClassDerivedBeanDefinition(beanClass);
        if (supplier != null) {
            beanDefinition.setInstanceSupplier(supplier);
        }
        for (BeanDefinitionCustomizer customizer : customizers) {
            customizer.customize(beanDefinition);
        }

        String nameToUse = (beanName != null ? beanName : beanClass.getName());
        registerBeanDefinition(nameToUse, beanDefinition);
    }

    public void register(BeanRegistrar... registrars) {
        for (BeanRegistrar registrar : registrars) {
            new BeanRegistryAdapter(this.beanFactory, getEnvironment(), registrar.getClass()).register(registrar);
        }
    }

    @SuppressWarnings("serial")
    private static class ClassDerivedBeanDefinition extends RootBeanDefinition {

        public ClassDerivedBeanDefinition(Class<?> beanClass) {
            super(beanClass);
        }

        public ClassDerivedBeanDefinition(ClassDerivedBeanDefinition original) {
            super(original);
        }

        @Override
        public Constructor<?>[] getPreferredConstructors() {
            Constructor<?>[] fromAttribute = super.getPreferredConstructors();
            if (fromAttribute != null) {
                return fromAttribute;
            }
            Class<?> clazz = getBeanClass();
            Constructor<?> primaryCtor = BeanUtils.findPrimaryConstructor(clazz);
            if (primaryCtor != null) {
                return new Constructor<?>[]{primaryCtor};
            }
            Constructor<?>[] publicCtors = clazz.getConstructors();
            if (publicCtors.length > 0) {
                return publicCtors;
            }
            return null;
        }

        @Override
        public RootBeanDefinition cloneBeanDefinition() {
            return new ClassDerivedBeanDefinition(this);
        }
    }

}
