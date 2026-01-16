package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.*;
import com.t13max.ioc.beans.factory.*;
import com.t13max.ioc.beans.factory.config.*;
import com.t13max.ioc.core.*;
import com.t13max.ioc.utils.*;
import com.t13max.ioc.utils.function.ThrowingSupplier;

import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 22:42 2026/1/15
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory implements AutowireCapableBeanFactory {

    private InstantiationStrategy instantiationStrategy;

    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private boolean allowCircularReferences = true;

    private boolean allowRawInjectionDespiteWrapping = false;

    private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

    private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

    private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

    private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

    private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

    private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache = new ConcurrentHashMap<>();

    public AbstractAutowireCapableBeanFactory() {
        super();
        ignoreDependencyInterface(BeanNameAware.class);
        ignoreDependencyInterface(BeanFactoryAware.class);
        ignoreDependencyInterface(BeanClassLoaderAware.class);
        this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
    }

    public AbstractAutowireCapableBeanFactory(BeanFactory parentBeanFactory) {
        this();
        setParentBeanFactory(parentBeanFactory);
    }

    public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
        this.instantiationStrategy = instantiationStrategy;
    }

    public InstantiationStrategy getInstantiationStrategy() {
        return this.instantiationStrategy;
    }

    public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }

    public ParameterNameDiscoverer getParameterNameDiscoverer() {
        return this.parameterNameDiscoverer;
    }

    public void setAllowCircularReferences(boolean allowCircularReferences) {
        this.allowCircularReferences = allowCircularReferences;
    }

    public boolean isAllowCircularReferences() {
        return this.allowCircularReferences;
    }

    public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
        this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
    }

    public boolean isAllowRawInjectionDespiteWrapping() {
        return this.allowRawInjectionDespiteWrapping;
    }

    public void ignoreDependencyType(Class<?> type) {
        this.ignoredDependencyTypes.add(type);
    }

    public void ignoreDependencyInterface(Class<?> ifc) {
        this.ignoredDependencyInterfaces.add(ifc);
    }

    @Override
    public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
        super.copyConfigurationFrom(otherFactory);
        if (otherFactory instanceof AbstractAutowireCapableBeanFactory otherAutowireFactory) {
            this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
            this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
            this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
            this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
        }
    }

    //-------------------------------------------------------------------------
    // Typical methods for creating and populating external bean instances
    //-------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <T> T createBean(Class<T> beanClass) throws BeansException {
        // Use non-singleton bean definition, to avoid registering bean as dependent bean.
        RootBeanDefinition bd = new CreateFromClassBeanDefinition(beanClass);
        bd.setScope(SCOPE_PROTOTYPE);
        bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
        return (T) createBean(beanClass.getName(), bd, null);
    }

    @Override
    public void autowireBean(Object existingBean) {
        // Use non-singleton bean definition, to avoid registering bean as dependent bean.
        RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
        bd.setScope(SCOPE_PROTOTYPE);
        bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
        BeanWrapper bw = new BeanWrapperImpl(existingBean);
        initBeanWrapper(bw);
        populateBean(bd.getBeanClass().getName(), bd, bw);
    }

    @Override
    public Object configureBean(Object existingBean, String beanName) throws BeansException {
        markBeanAsCreated(beanName);
        BeanDefinition mbd = getMergedBeanDefinition(beanName);
        RootBeanDefinition bd = null;
        if (mbd instanceof RootBeanDefinition rbd) {
            bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
        }
        if (bd == null) {
            bd = new RootBeanDefinition(mbd);
        }
        if (!bd.isPrototype()) {
            bd.setScope(SCOPE_PROTOTYPE);
            bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
        }
        BeanWrapper bw = new BeanWrapperImpl(existingBean);
        initBeanWrapper(bw);
        populateBean(beanName, bd, bw);
        return initializeBean(beanName, existingBean, bd);
    }

    //-------------------------------------------------------------------------
    // Specialized methods for fine-grained control over the bean lifecycle
    //-------------------------------------------------------------------------

    @Deprecated(since = "6.1")
    @Override
    public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
        // Use non-singleton bean definition, to avoid registering bean as dependent bean.
        RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
        bd.setScope(SCOPE_PROTOTYPE);
        return createBean(beanClass.getName(), bd, null);
    }

    @Override
    public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
        // Use non-singleton bean definition, to avoid registering bean as dependent bean.
        RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
        bd.setScope(SCOPE_PROTOTYPE);
        if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
            return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
        } else {
            Object bean = getInstantiationStrategy().instantiate(bd, null, this);
            populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
            return bean;
        }
    }

    @Override
    public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
            throws BeansException {

        if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
            throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
        }
        // Use non-singleton bean definition, to avoid registering bean as dependent bean.
        RootBeanDefinition bd =
                new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
        bd.setScope(SCOPE_PROTOTYPE);
        BeanWrapper bw = new BeanWrapperImpl(existingBean);
        initBeanWrapper(bw);
        populateBean(bd.getBeanClass().getName(), bd, bw);
    }

    @Override
    public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
        markBeanAsCreated(beanName);
        BeanDefinition bd = getMergedBeanDefinition(beanName);
        BeanWrapper bw = new BeanWrapperImpl(existingBean);
        initBeanWrapper(bw);
        applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
    }

    @Override
    public Object initializeBean(Object existingBean, String beanName) {
        return initializeBean(beanName, existingBean, null);
    }

    @Deprecated(since = "6.1")
    @Override
    public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName) throws BeansException {

        Object result = existingBean;
        for (BeanPostProcessor processor : getBeanPostProcessors()) {
            Object current = processor.postProcessBeforeInitialization(result, beanName);
            if (current == null) {
                return result;
            }
            result = current;
        }
        return result;
    }

    @Deprecated(since = "6.1")
    @Override
    public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName) throws BeansException {

        Object result = existingBean;
        for (BeanPostProcessor processor : getBeanPostProcessors()) {
            Object current = processor.postProcessAfterInitialization(result, beanName);
            if (current == null) {
                return result;
            }
            result = current;
        }
        return result;
    }

    @Override
    public void destroyBean(Object existingBean) {
        new DisposableBeanAdapter(existingBean, getBeanPostProcessorCache().destructionAware).destroy();
    }

    //-------------------------------------------------------------------------
    // Delegate methods for resolving injection points
    //-------------------------------------------------------------------------

    @Override
    public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
        InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
        try {
            return getBean(name, descriptor.getDependencyType());
        } finally {
            ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
        }
    }

    @Override
    public Object resolveDependency(DependencyDescriptor descriptor, String requestingBeanName) throws BeansException {
        return resolveDependency(descriptor, requestingBeanName, null, null);
    }

    //---------------------------------------------------------------------
    // AbstractBeanFactory的模板方法实现
    //---------------------------------------------------------------------

    @Override
    protected Object createBean(String beanName, RootBeanDefinition mbd, Object[] args) throws BeanCreationException {

        if (logger.isTraceEnabled()) {
            logger.trace("Creating instance of bean '" + beanName + "'");
        }
        RootBeanDefinition mbdToUse = mbd;

        //判断能否实例化, 是否可以通过当前类加载器加载
        Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
        if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
            mbdToUse = new RootBeanDefinition(mbd);
            mbdToUse.setBeanClass(resolvedClass);
            try {
                // 校验和准备bean中的方法覆盖
                mbdToUse.prepareMethodOverrides();
            } catch (BeanDefinitionValidationException ex) {
                throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(), beanName, "Validation of method overrides failed", ex);
            }
        }

        try {
            // 如果配置了后置处理器,则这里返回一个代理对象
            Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
            if (bean != null) {
                return bean;
            }
        } catch (Throwable ex) {
            throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, "BeanPostProcessor before instantiation of bean failed", ex);
        }

        try {
            //创建bean实例对象的具体实现
            Object beanInstance = doCreateBean(beanName, mbdToUse, args);
            if (logger.isTraceEnabled()) {
                logger.trace("Finished creating instance of bean '" + beanName + "'");
            }
            return beanInstance;
        } catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
            // A previously detected exception with proper bean creation context already,
            // or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
            throw ex;
        } catch (Throwable ex) {
            throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
        }
    }

    //创建bean实例对象的具体实现
    protected Object doCreateBean(String beanName, RootBeanDefinition mbd, Object[] args) throws BeanCreationException {

        // 初始化Bean
        BeanWrapper instanceWrapper = null;
        //如果这个bean是单例的，则从缓存中获取这个beanName对应的BeanWrapper实例,并清除
        if (mbd.isSingleton()) {
            instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
        }
        if (instanceWrapper == null) {
            //*创建实例对象*
            instanceWrapper = createBeanInstance(beanName, mbd, args);
        }
        //获取实例化对象和其类型
        Object bean = instanceWrapper.getWrappedInstance();
        Class<?> beanType = instanceWrapper.getWrappedClass();
        if (beanType != NullBean.class) {
            mbd.resolvedTargetType = beanType;
        }

        //调用后置处理器
        synchronized (mbd.postProcessingLock) {
            if (!mbd.postProcessed) {
                try {
                    //调用后置处理器
                    applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
                } catch (Throwable ex) {
                    throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Post-processing of merged bean definition failed", ex);
                }
                mbd.markAsPostProcessed();
            }
        }

        // 向容器中缓存单例模式的bean对象,以防循环引用
        boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences && isSingletonCurrentlyInCreation(beanName));
        //暴露早期bean
        if (earlySingletonExposure) {
            if (logger.isTraceEnabled()) {
                logger.trace("Eagerly caching bean '" + beanName + "' to allow for resolving potential circular references");
            }
            //添加单例工厂
            addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
        }

        Object exposedObject = bean;
        try {
            // 把生成的bean对象的依赖关系设置好,完成整个依赖注入过程
            populateBean(beanName, mbd, instanceWrapper);
            //初始化bean对象
            exposedObject = initializeBean(beanName, exposedObject, mbd);
        } catch (Throwable ex) {
            if (ex instanceof BeanCreationException bce && beanName.equals(bce.getBeanName())) {
                throw bce;
            } else {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
            }
        }

        if (earlySingletonExposure) {
            //获取指定名称的已注册的单例bean对象
            Object earlySingletonReference = getSingleton(beanName, false);
            if (earlySingletonReference != null) {
                //如果根据名称获取的已注册的bean和正在实例化的bean是同一个
                if (exposedObject == bean) {
                    // 当前实例化的bean初始化完成
                    exposedObject = earlySingletonReference;
                } else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                    //如果当前bean依赖其他bean,并且当发生循环引用时不允许新创建实例对象
                    String[] dependentBeans = getDependentBeans(beanName);
                    Set<String> actualDependentBeans = CollectionUtils.newLinkedHashSet(dependentBeans.length);
                    // 获取当前bean所依赖的其他bean
                    for (String dependentBean : dependentBeans) {
                        //对依赖bean进行类型检查
                        if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                            actualDependentBeans.add(dependentBean);
                        }
                    }
                    if (!actualDependentBeans.isEmpty()) {
                        throw new BeanCurrentlyInCreationException(beanName,
                                "Bean with name '" + beanName + "' has been injected into other beans [" +
                                        StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                                        "] in its raw version as part of a circular reference, but has eventually been " +
                                        "wrapped. This means that said other beans do not use the final version of the " +
                                        "bean. This is often the result of over-eager type matching - consider using " +
                                        "'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
                    }
                }
            }
        }

        // 注册, 成完依赖注入的bean
        try {
            registerDisposableBeanIfNecessary(beanName, bean, mbd);
        } catch (BeanDefinitionValidationException ex) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
        }
        // 为应用返回所需要的实例对象
        return exposedObject;
    }

    @Override
    protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
        Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);
        // Apply SmartInstantiationAwareBeanPostProcessors to predict the
        // eventual type after a before-instantiation shortcut.
        if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
            boolean matchingOnlyFactoryBean = (typesToMatch.length == 1 && typesToMatch[0] == FactoryBean.class);
            for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
                Class<?> predicted = bp.predictBeanType(targetType, beanName);
                if (predicted != null &&
                        (!matchingOnlyFactoryBean || FactoryBean.class.isAssignableFrom(predicted))) {
                    return predicted;
                }
            }
        }
        return targetType;
    }

    protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
        Class<?> targetType = mbd.getTargetType();
        if (targetType == null) {
            if (mbd.getFactoryMethodName() != null) {
                targetType = getTypeForFactoryMethod(beanName, mbd, typesToMatch);
            } else {
                targetType = resolveBeanClass(mbd, beanName, typesToMatch);
                if (mbd.hasBeanClass()) {
                    targetType = getInstantiationStrategy().getActualBeanClass(mbd, beanName, this);
                }
            }
            if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
                mbd.resolvedTargetType = targetType;
            }
        }
        return targetType;
    }

    protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
        ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
        if (cachedReturnType != null) {
            return cachedReturnType.resolve();
        }

        Class<?> commonType = null;
        Method uniqueCandidate = mbd.factoryMethodToIntrospect;

        if (uniqueCandidate == null) {
            Class<?> factoryClass;
            boolean isStatic = true;

            String factoryBeanName = mbd.getFactoryBeanName();
            if (factoryBeanName != null) {
                if (factoryBeanName.equals(beanName)) {
                    throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
                            "factory-bean reference points back to the same bean definition");
                }
                // Check declared factory method return type on factory class.
                factoryClass = getType(factoryBeanName);
                isStatic = false;
            } else {
                // Check declared factory method return type on bean class.
                factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
            }

            if (factoryClass == null) {
                return null;
            }
            factoryClass = ClassUtils.getUserClass(factoryClass);

            // If all factory methods have the same return type, return that type.
            // Can't clearly figure out exact method due to type converting / autowiring!
            int minNrOfArgs =
                    (mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
            Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(factoryClass,
                    clazz -> ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS));

            for (Method candidate : candidates) {
                if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
                        candidate.getParameterCount() >= minNrOfArgs) {
                    // Declared type variables to inspect?
                    if (candidate.getTypeParameters().length > 0) {
                        try {
                            // Fully resolve parameter names and argument values.
                            ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
                            Class<?>[] paramTypes = candidate.getParameterTypes();
                            String[] paramNames = null;
                            if (cav.containsNamedArgument()) {
                                ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
                                if (pnd != null) {
                                    paramNames = pnd.getParameterNames(candidate);
                                }
                            }
                            Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = CollectionUtils.newHashSet(paramTypes.length);
                            Object[] args = new Object[paramTypes.length];
                            for (int i = 0; i < args.length; i++) {
                                ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
                                        i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
                                if (valueHolder == null) {
                                    valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
                                }
                                if (valueHolder != null) {
                                    args[i] = valueHolder.getValue();
                                    usedValueHolders.add(valueHolder);
                                }
                            }
                            Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
                                    candidate, args, getBeanClassLoader());
                            uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
                                    candidate : null);
                            commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
                            if (commonType == null) {
                                // Ambiguous return types found: return null to indicate "not determinable".
                                return null;
                            }
                        } catch (Throwable ex) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Failed to resolve generic return type for factory method: " + ex);
                            }
                        }
                    } else {
                        uniqueCandidate = (commonType == null ? candidate : null);
                        commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
                        if (commonType == null) {
                            // Ambiguous return types found: return null to indicate "not determinable".
                            return null;
                        }
                    }
                }
            }

            mbd.factoryMethodToIntrospect = uniqueCandidate;
            if (commonType == null) {
                return null;
            }
        }

        // Common return type found: all factory methods return same type. For a non-parameterized
        // unique candidate, cache the full type declaration context of the target factory method.
        try {
            cachedReturnType = (uniqueCandidate != null ?
                    ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
            mbd.factoryMethodReturnType = cachedReturnType;
            return cachedReturnType.resolve();
        } catch (LinkageError err) {
            // For example, a NoClassDefFoundError for a generic method return type
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to resolve type for factory method of bean '" + beanName + "': " +
                        (uniqueCandidate != null ? uniqueCandidate : commonType), err);
            }
            return null;
        }
    }

    @Override
    protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
        ResolvableType result;

        // Check if the bean definition itself has defined the type with an attribute
        try {
            result = getTypeForFactoryBeanFromAttributes(mbd);
            if (result != ResolvableType.NONE) {
                return result;
            }
        } catch (IllegalArgumentException ex) {
            throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
                    String.valueOf(ex.getMessage()));
        }

        // For instance supplied beans, try the target type and bean class immediately
        if (mbd.getInstanceSupplier() != null) {
            result = getFactoryBeanGeneric(mbd.targetType);
            if (result.resolve() != null) {
                return result;
            }
            result = getFactoryBeanGeneric(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : null);
            if (result.resolve() != null) {
                return result;
            }
        }

        // Consider factory methods
        String factoryBeanName = mbd.getFactoryBeanName();
        String factoryMethodName = mbd.getFactoryMethodName();

        // Scan the factory bean methods
        if (factoryBeanName != null) {
            if (factoryMethodName != null) {
                // Try to obtain the FactoryBean's object type from its factory method
                // declaration without instantiating the containing bean at all.
                BeanDefinition factoryBeanDefinition = getBeanDefinition(factoryBeanName);
                Class<?> factoryBeanClass;
                if (factoryBeanDefinition instanceof AbstractBeanDefinition abstractBeanDefinition &&
                        abstractBeanDefinition.hasBeanClass()) {
                    factoryBeanClass = abstractBeanDefinition.getBeanClass();
                } else {
                    RootBeanDefinition fbmbd = getMergedBeanDefinition(factoryBeanName, factoryBeanDefinition);
                    factoryBeanClass = determineTargetType(factoryBeanName, fbmbd);
                }
                if (factoryBeanClass != null) {
                    result = getTypeForFactoryBeanFromMethod(factoryBeanClass, factoryMethodName);
                    if (result.resolve() != null) {
                        return result;
                    }
                }
            }
            // If not resolvable above and the referenced factory bean doesn't exist yet,
            // exit here - we don't want to force the creation of another bean just to
            // obtain a FactoryBean's object type...
            if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
                return ResolvableType.NONE;
            }
        }

        // If we're allowed, we can create the factory bean and call getObjectType() early
        if (allowInit) {
            FactoryBean<?> factoryBean = (mbd.isSingleton() ?
                    getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
                    getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));
            if (factoryBean != null) {
                // Try to obtain the FactoryBean's object type from this early stage of the instance.
                Class<?> type = getTypeForFactoryBean(factoryBean);
                if (type != null) {
                    return ResolvableType.forClass(type);
                }
                // No type found for shortcut FactoryBean instance:
                // fall back to full creation of the FactoryBean instance.
                return super.getTypeForFactoryBean(beanName, mbd, true);
            }
        }

        if (factoryBeanName == null && mbd.hasBeanClass() && factoryMethodName != null) {
            // No early bean instantiation possible: determine FactoryBean's type from
            // static factory method signature or from class inheritance hierarchy...
            return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
        }

        // For regular beans, try the target type and bean class as fallback
        if (mbd.getInstanceSupplier() == null) {
            result = getFactoryBeanGeneric(mbd.targetType);
            if (result.resolve() != null) {
                return result;
            }
            result = getFactoryBeanGeneric(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : null);
            if (result.resolve() != null) {
                return result;
            }
        }

        // FactoryBean type not resolvable
        return ResolvableType.NONE;
    }

    private ResolvableType getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {
        // CGLIB subclass methods hide generic parameters; look at the original user class.
        Class<?> factoryBeanClass = ClassUtils.getUserClass(beanClass);
        FactoryBeanMethodTypeFinder finder = new FactoryBeanMethodTypeFinder(factoryMethodName);
        ReflectionUtils.doWithMethods(factoryBeanClass, finder, ReflectionUtils.USER_DECLARED_METHODS);
        return finder.getResult();
    }

    protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
        Object exposedObject = bean;
        if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
            for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
                exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
            }
        }
        return exposedObject;
    }


    //---------------------------------------------------------------------
    // Implementation methods
    //---------------------------------------------------------------------

    private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
        Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
        if (lockFlag == null) {
            this.singletonLock.lock();
        } else {
            boolean locked = (lockFlag && this.singletonLock.tryLock());
            if (!locked) {
                // Avoid shortcut FactoryBean instance but allow for subsequent type-based resolution.
                resolveBeanClass(mbd, beanName);
                return null;
            }
        }

        try {
            BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
            if (bw != null) {
                return (FactoryBean<?>) bw.getWrappedInstance();
            }
            Object beanInstance = getSingleton(beanName, false);
            if (beanInstance instanceof FactoryBean<?> factoryBean) {
                return factoryBean;
            }
            if (isSingletonCurrentlyInCreation(beanName) ||
                    (mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
                return null;
            }

            Object instance;
            try {
                // Mark this bean as currently in creation, even if just partially.
                beforeSingletonCreation(beanName);
                // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
                instance = resolveBeforeInstantiation(beanName, mbd);
                if (instance == null) {
                    bw = createBeanInstance(beanName, mbd, null);
                    instance = bw.getWrappedInstance();
                    this.factoryBeanInstanceCache.put(beanName, bw);
                }
            } catch (UnsatisfiedDependencyException ex) {
                // Don't swallow, probably misconfiguration...
                throw ex;
            } catch (BeanCreationException ex) {
                // Don't swallow a linkage error since it contains a full stacktrace on
                // first occurrence... and just a plain NoClassDefFoundError afterwards.
                if (ex.contains(LinkageError.class)) {
                    throw ex;
                }
                // Instantiation failure, maybe too early...
                if (logger.isDebugEnabled()) {
                    logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
                }
                onSuppressedException(ex);
                return null;
            } finally {
                // Finished partial creation of this bean.
                afterSingletonCreation(beanName);
            }

            return getFactoryBean(beanName, instance);
        } finally {
            this.singletonLock.unlock();
        }
    }

    private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
        if (isPrototypeCurrentlyInCreation(beanName)) {
            return null;
        }

        Object instance;
        try {
            // Mark this bean as currently in creation, even if just partially.
            beforePrototypeCreation(beanName);
            // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
            instance = resolveBeforeInstantiation(beanName, mbd);
            if (instance == null) {
                BeanWrapper bw = createBeanInstance(beanName, mbd, null);
                instance = bw.getWrappedInstance();
            }
        } catch (UnsatisfiedDependencyException ex) {
            // Don't swallow, probably misconfiguration...
            throw ex;
        } catch (BeanCreationException ex) {
            // Instantiation failure, maybe too early...
            if (logger.isDebugEnabled()) {
                logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
            }
            onSuppressedException(ex);
            return null;
        } finally {
            // Finished partial creation of this bean.
            afterPrototypeCreation(beanName);
        }

        return getFactoryBean(beanName, instance);
    }

    protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
        for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
            processor.postProcessMergedBeanDefinition(mbd, beanType, beanName);
        }
    }

    @SuppressWarnings("deprecation")
    protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
        Object bean = null;
        if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
            // Make sure bean class is actually resolved at this point.
            if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
                Class<?> targetType = determineTargetType(beanName, mbd);
                if (targetType != null) {
                    bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
                    if (bean != null) {
                        bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
                    }
                }
            }
            mbd.beforeInstantiationResolved = (bean != null);
        }
        return bean;
    }

    protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
        for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
            Object result = bp.postProcessBeforeInstantiation(beanClass, beanName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    //创建Bean实例
    protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, Object[] args) {

        // 获取beanClass, 检查是否可实例化
        Class<?> beanClass = resolveBeanClass(mbd, beanName);

        if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
        }

        if (args == null) {
            //返回一个用来创建bean实例的回调接口
            Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
            if (instanceSupplier != null) {
                return obtainFromSupplier(instanceSupplier, beanName, mbd);
            }
        }

        if (mbd.getFactoryMethodName() != null) {
            // 通过工厂方法创建
            return instantiateUsingFactoryMethod(beanName, mbd, args);
        }

        // 使用容器的自动装配方法进行实例化
        boolean resolved = false;
        boolean autowireNecessary = false;
        if (args == null) {
            synchronized (mbd.constructorArgumentLock) {
                if (mbd.resolvedConstructorOrFactoryMethod != null) {
                    resolved = true;
                    autowireNecessary = mbd.constructorArgumentsResolved;
                }
            }
        }
        if (resolved) {
            if (autowireNecessary) {
                // 配置了自动装配属性, 使用容器的自动装配实例化, 即: 根据参数类型匹配bean的构造方法
                return autowireConstructor(beanName, mbd, null, null);
            } else {
                // 使用默认的无参构造方法进行实例化
                return instantiateBean(beanName, mbd);
            }
        }

        // Candidate constructors for autowiring?
        Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
        if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
                mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
            return autowireConstructor(beanName, mbd, ctors, args);
        }

        // Preferred constructors for default construction?
        ctors = mbd.getPreferredConstructors();
        if (ctors != null) {
            return autowireConstructor(beanName, mbd, ctors, null);
        }

        // No special handling: simply use no-arg constructor.
        return instantiateBean(beanName, mbd);
    }

    private BeanWrapper obtainFromSupplier(Supplier<?> supplier, String beanName, RootBeanDefinition mbd) {

        // 获取当前的bean实例
        String outerBean = this.currentlyCreatedBean.get();
        // 设置当前处理的beanName
        this.currentlyCreatedBean.set(beanName);
        Object instance;

        try {
            // 从Supplier中获取
            instance = obtainInstanceFromSupplier(supplier, beanName, mbd);
        } catch (Throwable ex) {
            if (ex instanceof BeansException beansException) {
                throw beansException;
            }
            throw new BeanCreationException(beanName, "Instantiation of supplied bean failed", ex);
        } finally {
            if (outerBean != null) {
                // 如果currentlyCreatedBean取不到设置
                this.currentlyCreatedBean.set(outerBean);
            } else {
                //移除
                this.currentlyCreatedBean.remove();
            }
        }

        if (instance == null) {
            //取不到, 设置为NullBean
            instance = new NullBean();
        }
        //包装
        BeanWrapper bw = new BeanWrapperImpl(instance);
        //实例化后的操作
        initBeanWrapper(bw);
        return bw;
    }

    protected Object obtainInstanceFromSupplier(Supplier<?> supplier, String beanName, RootBeanDefinition mbd) throws Exception {

        if (supplier instanceof ThrowingSupplier<?> throwingSupplier) {
            return throwingSupplier.getWithException();
        }
        return supplier.get();
    }

    @Override
    protected Object getObjectForBeanInstance(Object beanInstance, Class<?> requiredType,
                                              String name, String beanName, RootBeanDefinition mbd) {

        String currentlyCreatedBean = this.currentlyCreatedBean.get();
        if (currentlyCreatedBean != null) {
            registerDependentBean(beanName, currentlyCreatedBean);
        }

        return super.getObjectForBeanInstance(beanInstance, requiredType, name, beanName, mbd);
    }

    protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(Class<?> beanClass, String beanName)
            throws BeansException {

        if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
            for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
                Constructor<?>[] ctors = bp.determineCandidateConstructors(beanClass, beanName);
                if (ctors != null) {
                    return ctors;
                }
            }
        }
        return null;
    }

    protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
        try {
            //使用初始化策略实例化bean对象
            Object beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
            BeanWrapper bw = new BeanWrapperImpl(beanInstance);
            initBeanWrapper(bw);
            return bw;
        } catch (Throwable ex) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
        }
    }

    protected BeanWrapper instantiateUsingFactoryMethod(
            String beanName, RootBeanDefinition mbd, Object[] explicitArgs) {

        return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
    }

    protected BeanWrapper autowireConstructor(
            String beanName, RootBeanDefinition mbd, Constructor<?>[] ctors, Object[] explicitArgs) {

        return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
    }

    protected void populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw) {

        //如果BeanWrapper对象为null, 而要注入的属性值不为空, 则抛出下述异常
        if (bw == null) {
            if (mbd.hasPropertyValues()) {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
            } else {
                // BeanWrapper对象为 null, 属性值也为空, 不需要设置属性值, 直接返回
                return;
            }
        }

        if (bw.getWrappedClass().isRecord()) {
            if (mbd.hasPropertyValues()) {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Cannot apply property values to a record");
            } else {
                // Skip property population phase for records since they are immutable.
                return;
            }
        }

        // Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
        // state of the bean before properties are set. This can be used, for example,
        // to support styles of field injection.
        if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
            for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
                if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                    return;
                }
            }
        }

        //获取RootBeanDefinition中设置的属性值PropertyValues, 这些属性值来自对.xml文件中bean元素的解析
        PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
        // 获取自动注入的值
        int resolvedAutowireMode = mbd.getResolvedAutowireMode();
        // 自动注入
        if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
            MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
            // Add property values based on autowire by name if applicable.
            if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
                // 按照名称注入
                autowireByName(beanName, mbd, bw, newPvs);
            }
            // Add property values based on autowire by type if applicable.
            if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
                //按照类型注入
                autowireByType(beanName, mbd, bw, newPvs);
            }
            pvs = newPvs;
        }
        if (hasInstantiationAwareBeanPostProcessors()) {
            if (pvs == null) {
                pvs = mbd.getPropertyValues();
            }
            for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
                PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
                if (pvsToUse == null) {
                    return;
                }
                pvs = pvsToUse;
            }
        }

        //bean实例对象没有依赖, 即没有继承基类
        boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);
        if (needsDepCheck) {
            PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
            //检查依赖
            checkDependencies(beanName, mbd, filteredPds, pvs);
        }

        if (pvs != null) {
            //**对属性进行依赖注入
            applyPropertyValues(beanName, mbd, bw, pvs);
        }
    }

    protected void autowireByName(String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

        String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
        for (String propertyName : propertyNames) {
            if (containsBean(propertyName)) {
                Object bean = getBean(propertyName);
                pvs.add(propertyName, bean);
                registerDependentBean(propertyName, beanName);
                if (logger.isTraceEnabled()) {
                    logger.trace("Added autowiring by name from bean name '" + beanName + "' via property '" + propertyName + "' to bean named '" + propertyName + "'");
                }
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName + "' by name: no matching bean found");
                }
            }
        }
    }

    protected void autowireByType(String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

        TypeConverter converter = getCustomTypeConverter();
        if (converter == null) {
            converter = bw;
        }

        String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
        Set<String> autowiredBeanNames = new LinkedHashSet<>(propertyNames.length * 2);
        for (String propertyName : propertyNames) {
            try {
                PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
                // Don't try autowiring by type for type Object: never makes sense,
                // even if it technically is an unsatisfied, non-simple property.
                if (Object.class != pd.getPropertyType()) {
                    MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
                    // Do not allow eager init for type matching in case of a prioritized post-processor.
                    boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
                    DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
                    Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
                    if (autowiredArgument != null) {
                        pvs.add(propertyName, autowiredArgument);
                    }
                    for (String autowiredBeanName : autowiredBeanNames) {
                        registerDependentBean(autowiredBeanName, beanName);
                        if (logger.isTraceEnabled()) {
                            logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
                                    propertyName + "' to bean named '" + autowiredBeanName + "'");
                        }
                    }
                    autowiredBeanNames.clear();
                }
            } catch (BeansException ex) {
                throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
            }
        }
    }

    protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
        Set<String> result = new TreeSet<>();
        PropertyValues pvs = mbd.getPropertyValues();
        PropertyDescriptor[] pds = bw.getPropertyDescriptors();
        for (PropertyDescriptor pd : pds) {
            if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
                    !BeanUtils.isSimpleProperty(pd.getPropertyType())) {
                result.add(pd.getName());
            }
        }
        return StringUtils.toStringArray(result);
    }

    protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
        PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
        if (filtered == null) {
            filtered = filterPropertyDescriptorsForDependencyCheck(bw);
            if (cache) {
                PropertyDescriptor[] existing =
                        this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
                if (existing != null) {
                    filtered = existing;
                }
            }
        }
        return filtered;
    }

    protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
        List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
        pds.removeIf(this::isExcludedFromDependencyCheck);
        return pds.toArray(new PropertyDescriptor[0]);
    }

    protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
        return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
                this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
                AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
    }

    protected void checkDependencies(
            String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, PropertyValues pvs)
            throws UnsatisfiedDependencyException {

        int dependencyCheck = mbd.getDependencyCheck();
        for (PropertyDescriptor pd : pds) {
            if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
                boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
                boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
                        (isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
                        (!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
                if (unsatisfied) {
                    throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
                            "Set this property value or disable dependency checking for this bean.");
                }
            }
        }
    }

    //对属性进行依赖注入
    protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {

        if (pvs.isEmpty()) {
            return;
        }

        MutablePropertyValues mpvs = null;
        List<PropertyValue> original;

        if (pvs instanceof MutablePropertyValues _mpvs) {
            mpvs = _mpvs;
            //如果属性值已经转换
            if (mpvs.isConverted()) {
                // 为实例化对象设置属性值
                try {
                    bw.setPropertyValues(mpvs);
                    return;
                } catch (BeansException ex) {
                    throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Error setting property values", ex);
                }
            }
            //获取属性值对象的原始类型值
            original = mpvs.getPropertyValueList();
        } else {
            original = Arrays.asList(pvs.getPropertyValues());
        }

        //自定义转换器
        TypeConverter converter = getCustomTypeConverter();
        if (converter == null) {
            converter = bw;
        }

        //  创建一个BeanDefinition属性值解析器, 将BeanDefinition中的属性值解析为bean实例对象的实际值
        BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

        //为属性的解析值创建一个副本, 最后将属性值注入到实例对象中
        List<PropertyValue> deepCopy = new ArrayList<>(original.size());
        boolean resolveNecessary = false;
        for (PropertyValue pv : original) {
            //如果属性值已经转换, 直接添加到deepCopy列表中
            if (pv.isConverted()) {
                deepCopy.add(pv);
            }
            //如果属性值需要转换
            else {
                //属性名
                String propertyName = pv.getName();
                //属性值
                Object originalValue = pv.getValue();
                if (originalValue == AutowiredPropertyMarker.INSTANCE) {
                    Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
                    if (writeMethod == null) {
                        throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
                    }
                    originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
                }
                //解析值
                Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
                Object convertedValue = resolvedValue;
                boolean convertible = isConvertibleProperty(propertyName, bw);
                if (convertible) {
                    //使用用户自定义的类型转换器转换属性值
                    convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
                }
                //存储转换后的属性值, 避免每次属性注入时的转换工作
                if (resolvedValue == originalValue) {
                    if (convertible) {
                        //设置属性转换之后的值
                        pv.setConvertedValue(convertedValue);
                    }
                    deepCopy.add(pv);
                }
                //如果属性是可转换的, 且属性原始值是字符串类型, 且属性的原始类型值不是动态生成的字符串, 且属性的原始值不是集合或者数组类型
                else if (convertible && originalValue instanceof TypedStringValue typedStringValue &&
                        !typedStringValue.isDynamic() &&
                        !(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
                    pv.setConvertedValue(convertedValue);
                    deepCopy.add(pv);
                } else {
                    resolveNecessary = true;
                    // 重新封装属性的值
                    deepCopy.add(new PropertyValue(pv, convertedValue));
                }
            }
        }
        if (mpvs != null && !resolveNecessary) {
            //标记属性值已经转换过
            mpvs.setConverted();
        }

        // 进行属性依赖注入
        try {
            //完成bean的属性值注入的入口, 走AbstractPropertyAccessor中的实现方法
            bw.setPropertyValues(new MutablePropertyValues(deepCopy));
        } catch (BeansException ex) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
        }
    }

    /**
     * 1. isWritableProperty: 属性可写
     * 2. isNestedOrIndexedProperty: 是否循环嵌套
     */
    private boolean isConvertibleProperty(String propertyName, BeanWrapper bw) {
        try {
            return !PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName) &&
                    BeanUtils.hasUniqueWriteMethod(bw.getPropertyDescriptor(propertyName));
        } catch (InvalidPropertyException ex) {
            return false;
        }
    }

    private Object convertForProperty(Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

        if (converter instanceof BeanWrapperImpl beanWrapper) {
            return beanWrapper.convertForProperty(value, propertyName);
        } else {
            PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
            MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
            return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
        }
    }


    @SuppressWarnings("deprecation")
    protected Object initializeBean(String beanName, Object bean, RootBeanDefinition mbd) {

        //Aware接口执行
        invokeAwareMethods(beanName, bean);

        Object wrappedBean = bean;
        if (mbd == null || !mbd.isSynthetic()) {
            // BeanPostProcessor前置方法执行
            wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
        }

        try {
            // 执行实例化函数
            invokeInitMethods(beanName, wrappedBean, mbd);
        } catch (Throwable ex) {
            throw new BeanCreationException((mbd != null ? mbd.getResourceDescription() : null), beanName, ex.getMessage(), ex);
        }
        if (mbd == null || !mbd.isSynthetic()) {
            // BeanPostProcessor后置方法执行
            wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
        }

        return wrappedBean;
    }

    //Aware接口执行
    private void invokeAwareMethods(String beanName, Object bean) {
        if (bean instanceof Aware) {
            if (bean instanceof BeanNameAware beanNameAware) {
                beanNameAware.setBeanName(beanName);
            }
            if (bean instanceof BeanClassLoaderAware beanClassLoaderAware) {
                ClassLoader bcl = getBeanClassLoader();
                if (bcl != null) {
                    beanClassLoaderAware.setBeanClassLoader(bcl);
                }
            }
            if (bean instanceof BeanFactoryAware beanFactoryAware) {
                beanFactoryAware.setBeanFactory(AbstractAutowireCapableBeanFactory.this);
            }
        }
    }

    //
    protected void invokeInitMethods(String beanName, Object bean, RootBeanDefinition mbd) throws Throwable {

        //是否是InitializingBean
        boolean isInitializingBean = (bean instanceof InitializingBean);
        // 是否存在方法 "afterPropertiesSet"
        if (isInitializingBean && (mbd == null || !mbd.hasAnyExternallyManagedInitMethod("afterPropertiesSet"))) {
            if (logger.isTraceEnabled()) {
                logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
            }
            // 执行 afterPropertiesSet
            ((InitializingBean) bean).afterPropertiesSet();
        }

        if (mbd != null && bean.getClass() != NullBean.class) {
            String[] initMethodNames = mbd.getInitMethodNames();
            if (initMethodNames != null) {
                for (String initMethodName : initMethodNames) {
                    if (StringUtils.hasLength(initMethodName) &&
                            !(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
                            !mbd.hasAnyExternallyManagedInitMethod(initMethodName)) {
                        // 自定义的 init method
                        invokeCustomInitMethod(beanName, bean, mbd, initMethodName);
                    }
                }
            }
        }
    }

    protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd, String initMethodName) throws Throwable {

        // 获取 initMethod 名称
        Class<?> beanClass = bean.getClass();
        MethodDescriptor descriptor = MethodDescriptor.create(beanName, beanClass, initMethodName);
        String methodName = descriptor.methodName();

        // 反射获取方法
        Method initMethod = (mbd.isNonPublicAccessAllowed() ?
                BeanUtils.findMethod(descriptor.declaringClass(), methodName) :
                ClassUtils.getMethodIfAvailable(beanClass, methodName));

        //方法不存在
        if (initMethod == null) {
            if (mbd.isEnforceInitMethod()) {
                throw new BeanDefinitionValidationException("Could not find an init method named '" + methodName + "' on bean with name '" + beanName + "'");
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("No default init method named '" + methodName + "' found on bean with name '" + beanName + "'");
                }
                // Ignore non-existent default lifecycle methods.
                return;
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Invoking init method '" + methodName + "' on bean with name '" + beanName + "'");
        }
        // 尝试获取接口方法
        Method methodToInvoke = ClassUtils.getPubliclyAccessibleMethodIfPossible(initMethod, beanClass);

        try {
            // 反射调用
            ReflectionUtils.makeAccessible(methodToInvoke);
            methodToInvoke.invoke(bean);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }


    @SuppressWarnings("deprecation")
    @Override
    protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
        return applyBeanPostProcessorsAfterInitialization(object, beanName);
    }

    @Override
    protected void removeSingleton(String beanName) {
        super.removeSingleton(beanName);
        this.factoryBeanInstanceCache.remove(beanName);
    }

    @Override
    protected void clearSingletonCache() {
        super.clearSingletonCache();
        this.factoryBeanInstanceCache.clear();
    }

    @SuppressWarnings("serial")
    private static class CreateFromClassBeanDefinition extends RootBeanDefinition {

        public CreateFromClassBeanDefinition(Class<?> beanClass) {
            super(beanClass);
        }

        public CreateFromClassBeanDefinition(CreateFromClassBeanDefinition original) {
            super(original);
        }

        @Override
        public Constructor<?>[] getPreferredConstructors() {
            Constructor<?>[] fromAttribute = super.getPreferredConstructors();
            if (fromAttribute != null) {
                return fromAttribute;
            }
            return ConstructorResolver.determinePreferredConstructors(getBeanClass());
        }

        @Override
        public RootBeanDefinition cloneBeanDefinition() {
            return new CreateFromClassBeanDefinition(this);
        }
    }


    @SuppressWarnings("serial")
    private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

        public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
            super(methodParameter, false, eager);
        }

        @Override
        public String getDependencyName() {
            return null;
        }
    }


    private static class FactoryBeanMethodTypeFinder implements MethodCallback {

        private final String factoryMethodName;

        private ResolvableType result = ResolvableType.NONE;

        FactoryBeanMethodTypeFinder(String factoryMethodName) {
            this.factoryMethodName = factoryMethodName;
        }

        @Override
        public void doWith(Method method) throws IllegalArgumentException {
            if (isFactoryBeanMethod(method)) {
                ResolvableType returnType = ResolvableType.forMethodReturnType(method);
                ResolvableType candidate = returnType.as(FactoryBean.class).getGeneric();
                if (this.result == ResolvableType.NONE) {
                    this.result = candidate;
                } else {
                    Class<?> resolvedResult = this.result.resolve();
                    Class<?> commonAncestor = ClassUtils.determineCommonAncestor(candidate.resolve(), resolvedResult);
                    if (!ObjectUtils.nullSafeEquals(resolvedResult, commonAncestor)) {
                        this.result = ResolvableType.forClass(commonAncestor);
                    }
                }
            }
        }

        private boolean isFactoryBeanMethod(Method method) {
            return (method.getName().equals(this.factoryMethodName) &&
                    FactoryBean.class.isAssignableFrom(method.getReturnType()));
        }

        ResolvableType getResult() {
            Class<?> resolved = this.result.resolve();
            boolean foundResult = resolved != null && resolved != Object.class;
            return (foundResult ? this.result : ResolvableType.NONE);
        }
    }

}
