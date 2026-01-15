package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanCreationException;
import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.FactoryBean;
import com.t13max.ioc.beans.factory.config.*;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.utils.*;

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
public class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory implements AutowireCapableBeanFactory {

    
    private InstantiationStrategy instantiationStrategy;
    
    private  ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    
    private boolean allowCircularReferences = true;
    
    private boolean allowRawInjectionDespiteWrapping = false;
    
    private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();
    
    private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();
    
    private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");
    
    private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
            new ConcurrentHashMap<>();

    
    public AbstractAutowireCapableBeanFactory() {
        super();
        ignoreDependencyInterface(BeanNameAware.class);
        ignoreDependencyInterface(BeanFactoryAware.class);
        ignoreDependencyInterface(BeanClassLoaderAware.class);
        this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
    }
    
    public AbstractAutowireCapableBeanFactory( BeanFactory parentBeanFactory) {
        this();
        setParentBeanFactory(parentBeanFactory);
    }

    
    public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
        this.instantiationStrategy = instantiationStrategy;
    }
    
    public InstantiationStrategy getInstantiationStrategy() {
        return this.instantiationStrategy;
    }
    
    public void setParameterNameDiscoverer( ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }
    
    public  ParameterNameDiscoverer getParameterNameDiscoverer() {
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
        }
        else {
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
    public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
            throws BeansException {

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
    public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
            throws BeansException {

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
        }
        finally {
            ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
        }
    }

    @Override
    public  Object resolveDependency(DependencyDescriptor descriptor,  String requestingBeanName) throws BeansException {
        return resolveDependency(descriptor, requestingBeanName, null, null);
    }


    //---------------------------------------------------------------------
    // Implementation of relevant AbstractBeanFactory template methods
    //---------------------------------------------------------------------
    
    @Override
    protected Object createBean(String beanName, RootBeanDefinition mbd,  Object  [] args)
            throws BeanCreationException {

        if (logger.isTraceEnabled()) {
            logger.trace("Creating instance of bean '" + beanName + "'");
        }
        RootBeanDefinition mbdToUse = mbd;

        // Make sure bean class is actually resolved at this point, and
        // clone the bean definition in case of a dynamically resolved Class
        // which cannot be stored in the shared merged bean definition.
        Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
        if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
            mbdToUse = new RootBeanDefinition(mbd);
            mbdToUse.setBeanClass(resolvedClass);
            try {
                mbdToUse.prepareMethodOverrides();
            }
            catch (BeanDefinitionValidationException ex) {
                throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
                        beanName, "Validation of method overrides failed", ex);
            }
        }

        try {
            // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
            Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
            if (bean != null) {
                return bean;
            }
        }
        catch (Throwable ex) {
            throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
                    "BeanPostProcessor before instantiation of bean failed", ex);
        }

        try {
            Object beanInstance = doCreateBean(beanName, mbdToUse, args);
            if (logger.isTraceEnabled()) {
                logger.trace("Finished creating instance of bean '" + beanName + "'");
            }
            return beanInstance;
        }
        catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
            // A previously detected exception with proper bean creation context already,
            // or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
            throw ex;
        }
        catch (Throwable ex) {
            throw new BeanCreationException(
                    mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
        }
    }
    
    protected Object doCreateBean(String beanName, RootBeanDefinition mbd,  Object  [] args)
            throws BeanCreationException {

        // Instantiate the bean.
        BeanWrapper instanceWrapper = null;
        if (mbd.isSingleton()) {
            instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
        }
        if (instanceWrapper == null) {
            instanceWrapper = createBeanInstance(beanName, mbd, args);
        }
        Object bean = instanceWrapper.getWrappedInstance();
        Class<?> beanType = instanceWrapper.getWrappedClass();
        if (beanType != NullBean.class) {
            mbd.resolvedTargetType = beanType;
        }

        // Allow post-processors to modify the merged bean definition.
        synchronized (mbd.postProcessingLock) {
            if (!mbd.postProcessed) {
                try {
                    applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
                }
                catch (Throwable ex) {
                    throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                            "Post-processing of merged bean definition failed", ex);
                }
                mbd.markAsPostProcessed();
            }
        }

        // Eagerly cache singletons to be able to resolve circular references
        // even when triggered by lifecycle interfaces like BeanFactoryAware.
        boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
                isSingletonCurrentlyInCreation(beanName));
        if (earlySingletonExposure) {
            if (logger.isTraceEnabled()) {
                logger.trace("Eagerly caching bean '" + beanName +
                        "' to allow for resolving potential circular references");
            }
            addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
        }

        // Initialize the bean instance.
        Object exposedObject = bean;
        try {
            populateBean(beanName, mbd, instanceWrapper);
            exposedObject = initializeBean(beanName, exposedObject, mbd);
        }
        catch (Throwable ex) {
            if (ex instanceof BeanCreationException bce && beanName.equals(bce.getBeanName())) {
                throw bce;
            }
            else {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
            }
        }

        if (earlySingletonExposure) {
            Object earlySingletonReference = getSingleton(beanName, false);
            if (earlySingletonReference != null) {
                if (exposedObject == bean) {
                    exposedObject = earlySingletonReference;
                }
                else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                    String[] dependentBeans = getDependentBeans(beanName);
                    Set<String> actualDependentBeans = CollectionUtils.newLinkedHashSet(dependentBeans.length);
                    for (String dependentBean : dependentBeans) {
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

        // Register bean as disposable.
        try {
            registerDisposableBeanIfNecessary(beanName, bean, mbd);
        }
        catch (BeanDefinitionValidationException ex) {
            throw new BeanCreationException(
                    mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
        }

        return exposedObject;
    }

    @Override
    protected  Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
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
    
    protected  Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
        Class<?> targetType = mbd.getTargetType();
        if (targetType == null) {
            if (mbd.getFactoryMethodName() != null) {
                targetType = getTypeForFactoryMethod(beanName, mbd, typesToMatch);
            }
            else {
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
    
    protected  Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
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
            }
            else {
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
                        }
                        catch (Throwable ex) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Failed to resolve generic return type for factory method: " + ex);
                            }
                        }
                    }
                    else {
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
        }
        catch (LinkageError err) {
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
        }
        catch (IllegalArgumentException ex) {
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
                }
                else {
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
    
    private  FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
        Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
        if (lockFlag == null) {
            this.singletonLock.lock();
        }
        else {
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
            }
            catch (UnsatisfiedDependencyException ex) {
                // Don't swallow, probably misconfiguration...
                throw ex;
            }
            catch (BeanCreationException ex) {
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
            }
            finally {
                // Finished partial creation of this bean.
                afterSingletonCreation(beanName);
            }

            return getFactoryBean(beanName, instance);
        }
        finally {
            this.singletonLock.unlock();
        }
    }
    
    private  FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
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
        }
        catch (UnsatisfiedDependencyException ex) {
            // Don't swallow, probably misconfiguration...
            throw ex;
        }
        catch (BeanCreationException ex) {
            // Instantiation failure, maybe too early...
            if (logger.isDebugEnabled()) {
                logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
            }
            onSuppressedException(ex);
            return null;
        }
        finally {
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
    protected  Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
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
    
    protected  Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
        for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
            Object result = bp.postProcessBeforeInstantiation(beanClass, beanName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
    
    protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd,  Object  [] args) {
        // Make sure bean class is actually resolved at this point.
        Class<?> beanClass = resolveBeanClass(mbd, beanName);

        if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                    "Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
        }

        if (args == null) {
            Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
            if (instanceSupplier != null) {
                return obtainFromSupplier(instanceSupplier, beanName, mbd);
            }
        }

        if (mbd.getFactoryMethodName() != null) {
            return instantiateUsingFactoryMethod(beanName, mbd, args);
        }

        // Shortcut when re-creating the same bean...
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
                return autowireConstructor(beanName, mbd, null, null);
            }
            else {
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
        String outerBean = this.currentlyCreatedBean.get();
        this.currentlyCreatedBean.set(beanName);
        Object instance;

        try {
            instance = obtainInstanceFromSupplier(supplier, beanName, mbd);
        }
        catch (Throwable ex) {
            if (ex instanceof BeansException beansException) {
                throw beansException;
            }
            throw new BeanCreationException(beanName, "Instantiation of supplied bean failed", ex);
        }
        finally {
            if (outerBean != null) {
                this.currentlyCreatedBean.set(outerBean);
            }
            else {
                this.currentlyCreatedBean.remove();
            }
        }

        if (instance == null) {
            instance = new NullBean();
        }
        BeanWrapper bw = new BeanWrapperImpl(instance);
        initBeanWrapper(bw);
        return bw;
    }
    
    protected  Object obtainInstanceFromSupplier(Supplier<?> supplier, String beanName, RootBeanDefinition mbd)
            throws Exception {

        if (supplier instanceof ThrowingSupplier<?> throwingSupplier) {
            return throwingSupplier.getWithException();
        }
        return supplier.get();
    }
    
    @Override
    protected Object getObjectForBeanInstance(Object beanInstance,  Class<?> requiredType,
                                              String name, String beanName,  RootBeanDefinition mbd) {

        String currentlyCreatedBean = this.currentlyCreatedBean.get();
        if (currentlyCreatedBean != null) {
            registerDependentBean(beanName, currentlyCreatedBean);
        }

        return super.getObjectForBeanInstance(beanInstance, requiredType, name, beanName, mbd);
    }
    
    protected Constructor<?>  [] determineConstructorsFromBeanPostProcessors( Class<?> beanClass, String beanName)
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
            Object beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
            BeanWrapper bw = new BeanWrapperImpl(beanInstance);
            initBeanWrapper(bw);
            return bw;
        }
        catch (Throwable ex) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
        }
    }
    
    protected BeanWrapper instantiateUsingFactoryMethod(
            String beanName, RootBeanDefinition mbd,  Object  [] explicitArgs) {

        return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
    }
    
    protected BeanWrapper autowireConstructor(
            String beanName, RootBeanDefinition mbd, Constructor<?>  [] ctors,  Object  [] explicitArgs) {

        return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
    }
    
    protected void populateBean(String beanName, RootBeanDefinition mbd,  BeanWrapper bw) {
        if (bw == null) {
            if (mbd.hasPropertyValues()) {
                throw new BeanCreationException(
                        mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
            }
            else {
                // Skip property population phase for null instance.
                return;
            }
        }

        if (bw.getWrappedClass().isRecord()) {
            if (mbd.hasPropertyValues()) {
                throw new BeanCreationException(
                        mbd.getResourceDescription(), beanName, "Cannot apply property values to a record");
            }
            else {
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

        PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

        int resolvedAutowireMode = mbd.getResolvedAutowireMode();
        if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
            MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
            // Add property values based on autowire by name if applicable.
            if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
                autowireByName(beanName, mbd, bw, newPvs);
            }
            // Add property values based on autowire by type if applicable.
            if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
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

        boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);
        if (needsDepCheck) {
            PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
            checkDependencies(beanName, mbd, filteredPds, pvs);
        }

        if (pvs != null) {
            applyPropertyValues(beanName, mbd, bw, pvs);
        }
    }
    
    protected void autowireByName(
            String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

        String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
        for (String propertyName : propertyNames) {
            if (containsBean(propertyName)) {
                Object bean = getBean(propertyName);
                pvs.add(propertyName, bean);
                registerDependentBean(propertyName, beanName);
                if (logger.isTraceEnabled()) {
                    logger.trace("Added autowiring by name from bean name '" + beanName +
                            "' via property '" + propertyName + "' to bean named '" + propertyName + "'");
                }
            }
            else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
                            "' by name: no matching bean found");
                }
            }
        }
    }
    
    protected void autowireByType(
            String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

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
            }
            catch (BeansException ex) {
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
            String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds,  PropertyValues pvs)
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
    
    protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
        if (pvs.isEmpty()) {
            return;
        }

        MutablePropertyValues mpvs = null;
        List<PropertyValue> original;

        if (pvs instanceof MutablePropertyValues _mpvs) {
            mpvs = _mpvs;
            if (mpvs.isConverted()) {
                // Shortcut: use the pre-converted values as-is.
                try {
                    bw.setPropertyValues(mpvs);
                    return;
                }
                catch (BeansException ex) {
                    throw new BeanCreationException(
                            mbd.getResourceDescription(), beanName, "Error setting property values", ex);
                }
            }
            original = mpvs.getPropertyValueList();
        }
        else {
            original = Arrays.asList(pvs.getPropertyValues());
        }

        TypeConverter converter = getCustomTypeConverter();
        if (converter == null) {
            converter = bw;
        }
        BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

        // Create a deep copy, resolving any references for values.
        List<PropertyValue> deepCopy = new ArrayList<>(original.size());
        boolean resolveNecessary = false;
        for (PropertyValue pv : original) {
            if (pv.isConverted()) {
                deepCopy.add(pv);
            }
            else {
                String propertyName = pv.getName();
                Object originalValue = pv.getValue();
                if (originalValue == AutowiredPropertyMarker.INSTANCE) {
                    Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
                    if (writeMethod == null) {
                        throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
                    }
                    originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
                }
                Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
                Object convertedValue = resolvedValue;
                boolean convertible = isConvertibleProperty(propertyName, bw);
                if (convertible) {
                    convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
                }
                // Possibly store converted value in merged bean definition,
                // in order to avoid re-conversion for every created bean instance.
                if (resolvedValue == originalValue) {
                    if (convertible) {
                        pv.setConvertedValue(convertedValue);
                    }
                    deepCopy.add(pv);
                }
                else if (convertible && originalValue instanceof TypedStringValue typedStringValue &&
                        !typedStringValue.isDynamic() &&
                        !(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
                    pv.setConvertedValue(convertedValue);
                    deepCopy.add(pv);
                }
                else {
                    resolveNecessary = true;
                    deepCopy.add(new PropertyValue(pv, convertedValue));
                }
            }
        }
        if (mpvs != null && !resolveNecessary) {
            mpvs.setConverted();
        }

        // Set our (possibly massaged) deep copy.
        try {
            bw.setPropertyValues(new MutablePropertyValues(deepCopy));
        }
        catch (BeansException ex) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
        }
    }
    
    private boolean isConvertibleProperty(String propertyName, BeanWrapper bw) {
        try {
            return !PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName) &&
                    BeanUtils.hasUniqueWriteMethod(bw.getPropertyDescriptor(propertyName));
        }
        catch (InvalidPropertyException ex) {
            return false;
        }
    }
    
    private  Object convertForProperty(
             Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

        if (converter instanceof BeanWrapperImpl beanWrapper) {
            return beanWrapper.convertForProperty(value, propertyName);
        }
        else {
            PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
            MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
            return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
        }
    }

    
    @SuppressWarnings("deprecation")
    protected Object initializeBean(String beanName, Object bean,  RootBeanDefinition mbd) {
        invokeAwareMethods(beanName, bean);

        Object wrappedBean = bean;
        if (mbd == null || !mbd.isSynthetic()) {
            wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
        }

        try {
            invokeInitMethods(beanName, wrappedBean, mbd);
        }
        catch (Throwable ex) {
            throw new BeanCreationException(
                    (mbd != null ? mbd.getResourceDescription() : null), beanName, ex.getMessage(), ex);
        }
        if (mbd == null || !mbd.isSynthetic()) {
            wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
        }

        return wrappedBean;
    }

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
    
    protected void invokeInitMethods(String beanName, Object bean,  RootBeanDefinition mbd)
            throws Throwable {

        boolean isInitializingBean = (bean instanceof InitializingBean);
        if (isInitializingBean && (mbd == null || !mbd.hasAnyExternallyManagedInitMethod("afterPropertiesSet"))) {
            if (logger.isTraceEnabled()) {
                logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
            }
            ((InitializingBean) bean).afterPropertiesSet();
        }

        if (mbd != null && bean.getClass() != NullBean.class) {
            String[] initMethodNames = mbd.getInitMethodNames();
            if (initMethodNames != null) {
                for (String initMethodName : initMethodNames) {
                    if (StringUtils.hasLength(initMethodName) &&
                            !(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
                            !mbd.hasAnyExternallyManagedInitMethod(initMethodName)) {
                        invokeCustomInitMethod(beanName, bean, mbd, initMethodName);
                    }
                }
            }
        }
    }
    
    protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd, String initMethodName)
            throws Throwable {

        Class<?> beanClass = bean.getClass();
        MethodDescriptor descriptor = MethodDescriptor.create(beanName, beanClass, initMethodName);
        String methodName = descriptor.methodName();

        Method initMethod = (mbd.isNonPublicAccessAllowed() ?
                BeanUtils.findMethod(descriptor.declaringClass(), methodName) :
                ClassUtils.getMethodIfAvailable(beanClass, methodName));

        if (initMethod == null) {
            if (mbd.isEnforceInitMethod()) {
                throw new BeanDefinitionValidationException("Could not find an init method named '" +
                        methodName + "' on bean with name '" + beanName + "'");
            }
            else {
                if (logger.isTraceEnabled()) {
                    logger.trace("No default init method named '" + methodName +
                            "' found on bean with name '" + beanName + "'");
                }
                // Ignore non-existent default lifecycle methods.
                return;
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Invoking init method '" + methodName + "' on bean with name '" + beanName + "'");
        }
        Method methodToInvoke = ClassUtils.getPubliclyAccessibleMethodIfPossible(initMethod, beanClass);

        try {
            ReflectionUtils.makeAccessible(methodToInvoke);
            methodToInvoke.invoke(bean);
        }
        catch (InvocationTargetException ex) {
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
    
    Log getLogger() {
        return logger;
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
        public Constructor<?>  [] getPreferredConstructors() {
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
        public  String getDependencyName() {
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
                }
                else {
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
