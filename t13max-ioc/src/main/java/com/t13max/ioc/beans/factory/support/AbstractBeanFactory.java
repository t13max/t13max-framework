package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.*;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinitionHolder;
import com.t13max.ioc.beans.factory.config.BeanPostProcessor;
import com.t13max.ioc.beans.factory.config.ConfigurableBeanFactory;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.metrics.ApplicationStartup;
import com.t13max.ioc.core.metrics.StartupStep;
import com.t13max.ioc.utils.*;

import java.beans.PropertyEditor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

    private BeanFactory parentBeanFactory;

    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    private ClassLoader tempClassLoader;

    private boolean cacheBeanMetadata = true;

    private BeanExpressionResolver beanExpressionResolver;

    private ConversionService conversionService;

    private final Set<PropertyEditorRegistrar> defaultEditorRegistrars = new LinkedHashSet<>(4);

    private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

    private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

    private TypeConverter typeConverter;

    private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

    private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();

    private BeanPostProcessorCache beanPostProcessorCache;

    private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

    private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;
    //合并后的BeanDefinition集合
    private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);
    //已创建的BeanName
    private final Set<String> alreadyCreated = ConcurrentHashMap.newKeySet(256);

    private final ThreadLocal<Object> prototypesCurrentlyInCreation = new NamedThreadLocal<>("Prototype beans currently in creation");

    public AbstractBeanFactory() {

    }

    public AbstractBeanFactory(BeanFactory parentBeanFactory) {
        this.parentBeanFactory = parentBeanFactory;
    }

    //---------------------------------------------------------------------
    // BeanFactory实现
    //---------------------------------------------------------------------

    @Override
    public Object getBean(String name) throws BeansException {
        return doGetBean(name, null, null, false);
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        return doGetBean(name, requiredType, null, false);
    }

    @Override
    public Object getBean(String name, Object... args) throws BeansException {
        return doGetBean(name, null, args, false);
    }

    public <T> T getBean(String name, Class<T> requiredType, Object... args) throws BeansException {
        return doGetBean(name, requiredType, args, false);
    }

    @SuppressWarnings("unchecked")
    protected <T> T doGetBean(String name, Class<T> requiredType, Object[] args, boolean typeCheckOnly) throws BeansException {

        //转换beanName
        String beanName = transformedBeanName(name);

        Object beanInstance;

        // 先从单例缓存获取
        Object sharedInstance = getSingleton(beanName);
        //单例是否存在, 参数是否为空
        if (sharedInstance != null && args == null) {
            if (logger.isTraceEnabled()) {
                if (isSingletonCurrentlyInCreation(beanName)) {
                    logger.trace("Returning eagerly cached instance of singleton bean '{}' that is not fully initialized yet - a consequence of a circular reference", beanName);
                } else {
                    logger.trace("Returning cached instance of singleton bean '{}'", beanName);
                }
            }
            //实例化bean
            beanInstance = getObjectForBeanInstance(sharedInstance, requiredType, name, beanName, null);
        } else {
            //已经创建了, 可能陷入了循环依赖
            if (isPrototypeCurrentlyInCreation(beanName)) {
                throw new BeanCurrentlyInCreationException(beanName);
            }

            // Check if bean definition exists in this factory.
            BeanFactory parentBeanFactory = getParentBeanFactory();
            if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
                // Not found -> check parent.
                String nameToLookup = originalBeanName(name);
                if (parentBeanFactory instanceof AbstractBeanFactory abf) {
                    return abf.doGetBean(nameToLookup, requiredType, args, typeCheckOnly);
                } else if (args != null) {
                    // Delegation to parent with explicit args.
                    return (T) parentBeanFactory.getBean(nameToLookup, args);
                } else if (requiredType != null) {
                    // No args -> delegate to standard getBean method.
                    return parentBeanFactory.getBean(nameToLookup, requiredType);
                } else {
                    return (T) parentBeanFactory.getBean(nameToLookup);
                }
            }

            if (!typeCheckOnly) {
                markBeanAsCreated(beanName);
            }

            StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate").tag("beanName", name);
            try {
                if (requiredType != null) {
                    beanCreation.tag("beanType", requiredType::toString);
                }
                RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                checkMergedBeanDefinition(mbd, beanName, args);

                //需要依赖的bean
                String[] dependsOn = mbd.getDependsOn();
                if (dependsOn != null) {
                    for (String dep : dependsOn) {
                        if (isDependent(beanName, dep)) {
                            throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                        }
                        //注册依赖bean
                        registerDependentBean(dep, beanName);
                        try {
                            getBean(dep);
                        } catch (NoSuchBeanDefinitionException ex) {
                            throw new BeanCreationException(mbd.getResourceDescription(), beanName, "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                        } catch (BeanCreationException ex) {
                            if (requiredType != null) {
                                // Wrap exception with current bean metadata but only if specifically
                                // requested (indicated by required type), not for depends-on cascades.
                                throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Failed to initialize dependency '" + ex.getBeanName() + "' of " + requiredType.getSimpleName() + " bean '" + beanName + "': " + ex.getMessage(), ex);
                            }
                            throw ex;
                        }
                    }
                }

                // 创建实例
                if (mbd.isSingleton()) {
                    sharedInstance = getSingleton(beanName, () -> {
                        try {
                            return createBean(beanName, mbd, args);
                        } catch (BeansException ex) {
                            // Explicitly remove instance from singleton cache: It might have been put there
                            // eagerly by the creation process, to allow for circular reference resolution.
                            // Also remove any beans that received a temporary reference to the bean.
                            destroySingleton(beanName);
                            throw ex;
                        }
                    });
                    beanInstance = getObjectForBeanInstance(sharedInstance, requiredType, name, beanName, mbd);
                } else if (mbd.isPrototype()) {
                    // It's a prototype -> create a new instance.
                    Object prototypeInstance = null;
                    try {
                        beforePrototypeCreation(beanName);
                        prototypeInstance = createBean(beanName, mbd, args);
                    } finally {
                        afterPrototypeCreation(beanName);
                    }
                    beanInstance = getObjectForBeanInstance(prototypeInstance, requiredType, name, beanName, mbd);
                } else {
                    String scopeName = mbd.getScope();
                    if (!StringUtils.hasLength(scopeName)) {
                        throw new IllegalStateException("No scope name defined for bean '" + beanName + "'");
                    }
                    Scope scope = this.scopes.get(scopeName);
                    if (scope == null) {
                        throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                    }
                    try {
                        Object scopedInstance = scope.get(beanName, () -> {
                            beforePrototypeCreation(beanName);
                            try {
                                return createBean(beanName, mbd, args);
                            } finally {
                                afterPrototypeCreation(beanName);
                            }
                        });
                        beanInstance = getObjectForBeanInstance(scopedInstance, requiredType, name, beanName, mbd);
                    } catch (IllegalStateException ex) {
                        throw new ScopeNotActiveException(beanName, scopeName, ex);
                    }
                }
            } catch (BeansException ex) {
                beanCreation.tag("exception", ex.getClass().toString());
                beanCreation.tag("message", String.valueOf(ex.getMessage()));
                cleanupAfterBeanCreationFailure(beanName);
                throw ex;
            } finally {
                beanCreation.end();
                if (!isCacheBeanMetadata()) {
                    clearMergedBeanDefinition(beanName);
                }
            }
        }

        return adaptBeanInstance(name, beanInstance, requiredType);
    }

    @SuppressWarnings("unchecked")
    <T> T adaptBeanInstance(String name, Object bean, Class<?> requiredType) {
        // Check if required type matches the type of the actual bean instance.
        if (requiredType != null && !requiredType.isInstance(bean)) {
            try {
                Object convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
                if (convertedBean == null) {
                    throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
                }
                return (T) convertedBean;
            } catch (TypeMismatchException ex) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Failed to convert bean '" + name + "' to required type '" +
                            ClassUtils.getQualifiedName(requiredType) + "'", ex);
                }
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
        }
        return (T) bean;
    }

    @Override
    public boolean containsBean(String name) {
        String beanName = transformedBeanName(name);
        if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
            return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
        }
        // Not found -> check parent.
        BeanFactory parentBeanFactory = getParentBeanFactory();
        return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
    }

    @Override
    public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);

        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null) {
            if (beanInstance instanceof FactoryBean<?> factoryBean) {
                return (BeanFactoryUtils.isFactoryDereference(name) || factoryBean.isSingleton());
            } else {
                return !BeanFactoryUtils.isFactoryDereference(name);
            }
        }

        // No singleton instance found -> check bean definition.
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.isSingleton(originalBeanName(name));
        }

        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

        // In case of FactoryBean, return singleton status of created object if not a dereference.
        if (mbd.isSingleton()) {
            if (isFactoryBean(beanName, mbd)) {
                if (BeanFactoryUtils.isFactoryDereference(name)) {
                    return true;
                }
                FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
                return factoryBean.isSingleton();
            } else {
                return !BeanFactoryUtils.isFactoryDereference(name);
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);

        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.isPrototype(originalBeanName(name));
        }

        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
        if (mbd.isPrototype()) {
            // In case of FactoryBean, return singleton status of created object if not a dereference.
            return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
        }

        // Singleton or scoped - not a prototype.
        // However, FactoryBean may still produce a prototype object...
        if (BeanFactoryUtils.isFactoryDereference(name)) {
            return false;
        }
        if (isFactoryBean(beanName, mbd)) {
            FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
            return ((fb instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isPrototype()) ||
                    !fb.isSingleton());
        } else {
            return false;
        }
    }

    @Override
    public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        return isTypeMatch(name, typeToMatch, true);
    }


    protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {

        String beanName = transformedBeanName(name);
        boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

        // Check manually registered singletons.
        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null && beanInstance.getClass() != NullBean.class) {

            // Determine target for FactoryBean match if necessary.
            if (beanInstance instanceof FactoryBean<?> factoryBean) {
                if (!isFactoryDereference) {
                    if (factoryBean instanceof SmartFactoryBean<?> smartFactoryBean &&
                            smartFactoryBean.supportsType(typeToMatch.toClass())) {
                        return true;
                    }
                    Class<?> type = getTypeForFactoryBean(factoryBean);
                    if (type == null) {
                        return false;
                    }
                    if (typeToMatch.isAssignableFrom(type)) {
                        return true;
                    } else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
                        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                        ResolvableType targetType = mbd.targetType;
                        if (targetType == null) {
                            targetType = mbd.factoryMethodReturnType;
                        }
                        if (targetType == null) {
                            return false;
                        }
                        Class<?> targetClass = targetType.resolve();
                        if (targetClass != null && FactoryBean.class.isAssignableFrom(targetClass)) {
                            Class<?> classToMatch = typeToMatch.resolve();
                            if (classToMatch != null && !FactoryBean.class.isAssignableFrom(classToMatch) &&
                                    !classToMatch.isAssignableFrom(targetType.toClass())) {
                                return typeToMatch.isAssignableFrom(targetType.getGeneric());
                            }
                        } else {
                            return typeToMatch.isAssignableFrom(targetType);
                        }
                    }
                    return false;
                }
            } else if (isFactoryDereference) {
                return false;
            }

            // Actual matching against bean instance...
            if (typeToMatch.isInstance(beanInstance)) {
                // Direct match for exposed instance?
                return true;
            } else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
                // Generics potentially only match on the target class, not on the proxy...
                RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                Class<?> targetType = mbd.getTargetType();
                if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
                    // Check raw class match as well, making sure it's exposed on the proxy.
                    Class<?> classToMatch = typeToMatch.resolve();
                    if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
                        return false;
                    }
                    if (typeToMatch.isAssignableFrom(targetType)) {
                        return true;
                    }
                }
                ResolvableType resolvableType = mbd.targetType;
                if (resolvableType == null) {
                    resolvableType = mbd.factoryMethodReturnType;
                }
                return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
            } else {
                return false;
            }
        } else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
            // null instance registered
            return false;
        }

        // No singleton instance found -> check bean definition.
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
        }

        // Retrieve corresponding bean definition.
        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
        BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

        // Set up the types that we want to match against
        Class<?> classToMatch = typeToMatch.resolve();
        if (classToMatch == null) {
            classToMatch = FactoryBean.class;
        }
        Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
                new Class<?>[]{classToMatch} : new Class<?>[]{FactoryBean.class, classToMatch});

        // Attempt to predict the bean type
        Class<?> predictedType = null;

        // We're looking for a regular reference, but we're a factory bean that has
        // a decorated bean definition. The target bean should be the same type
        // as FactoryBean would ultimately return.
        if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
            // We should only attempt if the user explicitly set lazy-init to true
            // and we know the merged bean definition is for a factory bean.
            if (!mbd.isLazyInit() || allowFactoryBeanInit) {
                RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
                Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
                if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
                    predictedType = targetType;
                }
            }
        }

        // If we couldn't use the target type, try regular prediction.
        if (predictedType == null) {
            predictedType = predictBeanType(beanName, mbd, typesToMatch);
            if (predictedType == null) {
                return false;
            }
        }

        // Attempt to get the actual ResolvableType for the bean.
        ResolvableType beanType = null;

        // If it's a FactoryBean, we want to look at what it creates, not the factory class.
        if (FactoryBean.class.isAssignableFrom(predictedType)) {
            if (beanInstance == null && !isFactoryDereference) {
                beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
                predictedType = beanType.resolve();
                if (predictedType == null) {
                    return false;
                }
            }
        } else if (isFactoryDereference) {
            // Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
            // type, but we nevertheless are being asked to dereference a FactoryBean...
            // Let's check the original bean class and proceed with it if it is a FactoryBean.
            predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
            if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
                return false;
            }
        }

        // We don't have an exact type but if bean definition target type or the factory
        // method return type matches the predicted type then we can use that.
        if (beanType == null) {
            ResolvableType definedType = mbd.targetType;
            if (definedType == null) {
                definedType = mbd.factoryMethodReturnType;
            }
            if (definedType != null && definedType.resolve() == predictedType) {
                beanType = definedType;
            }
        }

        // If we have a bean type use it so that generics are considered
        if (beanType != null) {
            return typeToMatch.isAssignableFrom(beanType);
        }

        // If we don't have a bean type, fallback to the predicted type
        return typeToMatch.isAssignableFrom(predictedType);
    }

    @Override
    public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
        return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
    }

    @Override
    public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        return getType(name, true);
    }

    @Override
    public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);

        // Check manually registered singletons.
        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
            if (beanInstance instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
                return getTypeForFactoryBean(factoryBean);
            } else {
                return beanInstance.getClass();
            }
        }

        // No singleton instance found -> check bean definition.
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.getType(originalBeanName(name));
        }

        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
        Class<?> beanClass = predictBeanType(beanName, mbd);

        if (beanClass != null) {
            // Check bean class whether we're dealing with a FactoryBean.
            if (FactoryBean.class.isAssignableFrom(beanClass)) {
                if (!BeanFactoryUtils.isFactoryDereference(name)) {
                    // If it's a FactoryBean, we want to look at what it creates, not at the factory class.
                    beanClass = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
                }
            } else if (BeanFactoryUtils.isFactoryDereference(name)) {
                return null;
            }
        }

        if (beanClass == null) {
            // Check decorated bean definition, if any: We assume it'll be easier
            // to determine the decorated bean's type than the proxy's type.
            BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
            if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
                RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
                Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
                if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
                    return targetClass;
                }
            }
        }

        return beanClass;
    }

    @Override
    public String[] getAliases(String name) {
        String beanName = transformedBeanName(name);
        List<String> aliases = new ArrayList<>();
        boolean hasFactoryPrefix = (!name.isEmpty() && name.charAt(0) == BeanFactory.FACTORY_BEAN_PREFIX_CHAR);
        String fullBeanName = beanName;
        if (hasFactoryPrefix) {
            fullBeanName = FACTORY_BEAN_PREFIX + beanName;
        }
        if (!fullBeanName.equals(name)) {
            aliases.add(fullBeanName);
        }
        String[] retrievedAliases = super.getAliases(beanName);
        String prefix = (hasFactoryPrefix ? FACTORY_BEAN_PREFIX : "");
        for (String retrievedAlias : retrievedAliases) {
            String alias = prefix + retrievedAlias;
            if (!alias.equals(name)) {
                aliases.add(alias);
            }
        }
        if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
            BeanFactory parentBeanFactory = getParentBeanFactory();
            if (parentBeanFactory != null) {
                aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
            }
        }
        return StringUtils.toStringArray(aliases);
    }


    //---------------------------------------------------------------------
    // Implementation of HierarchicalBeanFactory interface
    //---------------------------------------------------------------------

    @Override
    public BeanFactory getParentBeanFactory() {
        return this.parentBeanFactory;
    }

    @Override
    public boolean containsLocalBean(String name) {
        String beanName = transformedBeanName(name);
        return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
                (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
    }


    //---------------------------------------------------------------------
    // Implementation of ConfigurableBeanFactory interface
    //---------------------------------------------------------------------

    @Override
    public void setParentBeanFactory(BeanFactory parentBeanFactory) {
        if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
            throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
        }
        if (this == parentBeanFactory) {
            throw new IllegalStateException("Cannot set parent bean factory to self");
        }
        this.parentBeanFactory = parentBeanFactory;
    }

    @Override
    public void setBeanClassLoader(ClassLoader beanClassLoader) {
        this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
    }

    @Override
    public ClassLoader getBeanClassLoader() {
        return this.beanClassLoader;
    }

    @Override
    public void setTempClassLoader(ClassLoader tempClassLoader) {
        this.tempClassLoader = tempClassLoader;
    }

    @Override
    public ClassLoader getTempClassLoader() {
        return this.tempClassLoader;
    }

    @Override
    public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
        this.cacheBeanMetadata = cacheBeanMetadata;
    }

    @Override
    public boolean isCacheBeanMetadata() {
        return this.cacheBeanMetadata;
    }

    @Override
    public void setBeanExpressionResolver(BeanExpressionResolver resolver) {
        this.beanExpressionResolver = resolver;
    }

    @Override
    public BeanExpressionResolver getBeanExpressionResolver() {
        return this.beanExpressionResolver;
    }

    @Override
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return this.conversionService;
    }

    @Override
    public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
        Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
        if (registrar.overridesDefaultEditors()) {
            this.defaultEditorRegistrars.add(registrar);
        } else {
            this.propertyEditorRegistrars.add(registrar);
        }
    }


    public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
        return this.propertyEditorRegistrars;
    }

    @Override
    public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
        Assert.notNull(requiredType, "Required type must not be null");
        Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
        this.customEditors.put(requiredType, propertyEditorClass);
    }

    @Override
    public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
        registerCustomEditors(registry);
    }


    public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
        return this.customEditors;
    }

    @Override
    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }


    protected TypeConverter getCustomTypeConverter() {
        return this.typeConverter;
    }

    @Override
    public TypeConverter getTypeConverter() {
        TypeConverter customConverter = getCustomTypeConverter();
        if (customConverter != null) {
            return customConverter;
        } else {
            // Build default TypeConverter, registering custom editors.
            SimpleTypeConverter typeConverter = new SimpleTypeConverter();
            typeConverter.setConversionService(getConversionService());
            registerCustomEditors(typeConverter);
            return typeConverter;
        }
    }

    @Override
    public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
        Assert.notNull(valueResolver, "StringValueResolver must not be null");
        this.embeddedValueResolvers.add(valueResolver);
    }

    @Override
    public boolean hasEmbeddedValueResolver() {
        return !this.embeddedValueResolvers.isEmpty();
    }

    @Override
    public String resolveEmbeddedValue(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        for (StringValueResolver resolver : this.embeddedValueResolvers) {
            result = resolver.resolveStringValue(result);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    @Override
    public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
        Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
        synchronized (this.beanPostProcessors) {
            // Remove from old position, if any
            this.beanPostProcessors.remove(beanPostProcessor);
            // Add to end of list
            this.beanPostProcessors.add(beanPostProcessor);
        }
    }


    public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
        synchronized (this.beanPostProcessors) {
            // Remove from old position, if any
            this.beanPostProcessors.removeAll(beanPostProcessors);
            // Add to end of list
            this.beanPostProcessors.addAll(beanPostProcessors);
        }
    }

    @Override
    public int getBeanPostProcessorCount() {
        return this.beanPostProcessors.size();
    }


    public List<BeanPostProcessor> getBeanPostProcessors() {
        return this.beanPostProcessors;
    }


    BeanPostProcessorCache getBeanPostProcessorCache() {
        synchronized (this.beanPostProcessors) {
            BeanPostProcessorCache bppCache = this.beanPostProcessorCache;
            if (bppCache == null) {
                bppCache = new BeanPostProcessorCache();
                for (BeanPostProcessor bpp : this.beanPostProcessors) {
                    if (bpp instanceof InstantiationAwareBeanPostProcessor instantiationAwareBpp) {
                        bppCache.instantiationAware.add(instantiationAwareBpp);
                        if (bpp instanceof SmartInstantiationAwareBeanPostProcessor smartInstantiationAwareBpp) {
                            bppCache.smartInstantiationAware.add(smartInstantiationAwareBpp);
                        }
                    }
                    if (bpp instanceof DestructionAwareBeanPostProcessor destructionAwareBpp) {
                        bppCache.destructionAware.add(destructionAwareBpp);
                    }
                    if (bpp instanceof MergedBeanDefinitionPostProcessor mergedBeanDefBpp) {
                        bppCache.mergedDefinition.add(mergedBeanDefBpp);
                    }
                }
                this.beanPostProcessorCache = bppCache;
            }
            return bppCache;
        }
    }

    private void resetBeanPostProcessorCache() {
        synchronized (this.beanPostProcessors) {
            this.beanPostProcessorCache = null;
        }
    }


    protected boolean hasInstantiationAwareBeanPostProcessors() {
        return !getBeanPostProcessorCache().instantiationAware.isEmpty();
    }


    protected boolean hasDestructionAwareBeanPostProcessors() {
        return !getBeanPostProcessorCache().destructionAware.isEmpty();
    }

    @Override
    public void registerScope(String scopeName, Scope scope) {
        Assert.notNull(scopeName, "Scope identifier must not be null");
        Assert.notNull(scope, "Scope must not be null");
        if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
            throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
        }
        Scope previous = this.scopes.put(scopeName, scope);
        if (previous != null && previous != scope) {
            if (logger.isDebugEnabled()) {
                logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
            }
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
            }
        }
    }

    @Override
    public String[] getRegisteredScopeNames() {
        return StringUtils.toStringArray(this.scopes.keySet());
    }

    @Override
    public Scope getRegisteredScope(String scopeName) {
        Assert.notNull(scopeName, "Scope identifier must not be null");
        return this.scopes.get(scopeName);
    }

    @Override
    public void setApplicationStartup(ApplicationStartup applicationStartup) {
        Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
        this.applicationStartup = applicationStartup;
    }

    @Override
    public ApplicationStartup getApplicationStartup() {
        return this.applicationStartup;
    }

    @Override
    public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
        Assert.notNull(otherFactory, "BeanFactory must not be null");
        setBeanClassLoader(otherFactory.getBeanClassLoader());
        setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
        setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
        setConversionService(otherFactory.getConversionService());
        if (otherFactory instanceof AbstractBeanFactory otherAbstractFactory) {
            this.defaultEditorRegistrars.addAll(otherAbstractFactory.defaultEditorRegistrars);
            this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
            this.customEditors.putAll(otherAbstractFactory.customEditors);
            this.typeConverter = otherAbstractFactory.typeConverter;
            this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
            this.scopes.putAll(otherAbstractFactory.scopes);
        } else {
            setTypeConverter(otherFactory.getTypeConverter());
            String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
            for (String scopeName : otherScopeNames) {
                this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
            }
        }
    }


    @Override
    public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
        String beanName = transformedBeanName(name);
        // Efficiently check whether bean definition exists in this factory.
        if (getParentBeanFactory() instanceof ConfigurableBeanFactory parent && !containsBeanDefinition(beanName)) {
            return parent.getMergedBeanDefinition(beanName);
        }
        // Resolve merged bean definition locally.
        return getMergedLocalBeanDefinition(beanName);
    }

    @Override
    public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
        String beanName = transformedBeanName(name);
        Object beanInstance = getSingleton(beanName, false);
        if (beanInstance != null) {
            return (beanInstance instanceof FactoryBean);
        }
        // No singleton instance found -> check bean definition.
        if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory cbf) {
            // No bean definition found in this factory -> delegate to parent.
            return cbf.isFactoryBean(name);
        }
        return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
    }

    @Override
    public boolean isActuallyInCreation(String beanName) {
        return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
    }


    protected boolean isPrototypeCurrentlyInCreation(String beanName) {
        Object curVal = this.prototypesCurrentlyInCreation.get();
        return (curVal != null && (curVal.equals(beanName) || (curVal instanceof Set<?> set && set.contains(beanName))));
    }


    @SuppressWarnings("unchecked")
    protected void beforePrototypeCreation(String beanName) {
        Object curVal = this.prototypesCurrentlyInCreation.get();
        if (curVal == null) {
            this.prototypesCurrentlyInCreation.set(beanName);
        } else if (curVal instanceof String strValue) {
            Set<String> beanNameSet = CollectionUtils.newHashSet(2);
            beanNameSet.add(strValue);
            beanNameSet.add(beanName);
            this.prototypesCurrentlyInCreation.set(beanNameSet);
        } else {
            Set<String> beanNameSet = (Set<String>) curVal;
            beanNameSet.add(beanName);
        }
    }


    @SuppressWarnings("unchecked")
    protected void afterPrototypeCreation(String beanName) {
        Object curVal = this.prototypesCurrentlyInCreation.get();
        if (curVal instanceof String) {
            this.prototypesCurrentlyInCreation.remove();
        } else if (curVal instanceof Set<?> beanNameSet) {
            beanNameSet.remove(beanName);
            if (beanNameSet.isEmpty()) {
                this.prototypesCurrentlyInCreation.remove();
            }
        }
    }

    @Override
    public void destroyBean(String beanName, Object beanInstance) {
        destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
    }


    protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
        new DisposableBeanAdapter(
                bean, beanName, mbd, getBeanPostProcessorCache().destructionAware).destroy();
    }

    @Override
    public void destroyScopedBean(String beanName) {
        RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
        if (mbd.isSingleton() || mbd.isPrototype()) {
            throw new IllegalArgumentException(
                    "Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
        }
        String scopeName = mbd.getScope();
        Scope scope = this.scopes.get(scopeName);
        if (scope == null) {
            throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
        }
        Object bean = scope.remove(beanName);
        if (bean != null) {
            destroyBean(beanName, bean, mbd);
        }
    }


    //---------------------------------------------------------------------
    // Implementation methods
    //---------------------------------------------------------------------


    protected String transformedBeanName(String name) {
        // 转换beanName
        // 1. 通过 BeanFactoryUtils.transformedBeanName求beanName
        // 2. 如果是有别名的(方法参数是别名)会从别名列表中获取对应的beanName
        return canonicalName(BeanFactoryUtils.transformedBeanName(name));
    }

    protected String originalBeanName(String name) {
        String beanName = transformedBeanName(name);
        if (!name.isEmpty() && name.charAt(0) == BeanFactory.FACTORY_BEAN_PREFIX_CHAR) {
            beanName = FACTORY_BEAN_PREFIX + beanName;
        }
        return beanName;
    }


    protected void initBeanWrapper(BeanWrapper bw) {
        bw.setConversionService(getConversionService());
        registerCustomEditors(bw);
    }


    protected void registerCustomEditors(PropertyEditorRegistry registry) {
        if (registry instanceof PropertyEditorRegistrySupport registrySupport) {
            registrySupport.useConfigValueEditors();
            if (!this.defaultEditorRegistrars.isEmpty()) {
                // Optimization: lazy overriding of default editors only when needed
                registrySupport.setDefaultEditorRegistrar(new BeanFactoryDefaultEditorRegistrar());
            }
        } else if (!this.defaultEditorRegistrars.isEmpty()) {
            // Fallback: proactive overriding of default editors
            applyEditorRegistrars(registry, this.defaultEditorRegistrars);
        }

        if (!this.propertyEditorRegistrars.isEmpty()) {
            applyEditorRegistrars(registry, this.propertyEditorRegistrars);
        }
        if (!this.customEditors.isEmpty()) {
            this.customEditors.forEach((requiredType, editorClass) ->
                    registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
        }
    }

    private void applyEditorRegistrars(PropertyEditorRegistry registry, Set<PropertyEditorRegistrar> registrars) {
        for (PropertyEditorRegistrar registrar : registrars) {
            try {
                registrar.registerCustomEditors(registry);
            } catch (BeanCreationException ex) {
                Throwable rootCause = ex.getMostSpecificCause();
                if (rootCause instanceof BeanCurrentlyInCreationException bce) {
                    String bceBeanName = bce.getBeanName();
                    if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
                                    "] failed because it tried to obtain currently created bean '" +
                                    ex.getBeanName() + "': " + ex.getMessage());
                        }
                        onSuppressedException(ex);
                        return;
                    }
                }
                throw ex;
            }
        }
    }

    //获取合并RootBeanDefinition
    protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
        //先从缓存获取
        RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
        if (mbd != null && !mbd.stale) {
            return mbd;
        }
        return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
    }

    protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd) throws BeanDefinitionStoreException {
        return getMergedBeanDefinition(beanName, bd, null);
    }

    protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd, BeanDefinition containingBd) throws BeanDefinitionStoreException {

        synchronized (this.mergedBeanDefinitions) {
            RootBeanDefinition mbd = null;
            RootBeanDefinition previous = null;

            if (containingBd == null) {
                //从缓存获取
                mbd = this.mergedBeanDefinitions.get(beanName);
            }

            if (mbd == null || mbd.stale) {
                previous = mbd;
                // 是否存在父名称
                if (bd.getParentName() == null) {
                    // 类型是否等于RootBeanDefinition
                    if (bd instanceof RootBeanDefinition rootBeanDef) {
                        mbd = rootBeanDef.cloneBeanDefinition();
                    } else {
                        mbd = new RootBeanDefinition(bd);
                    }
                } else {
                    // 父BeanDefinition
                    BeanDefinition pbd;
                    try {
                        String parentBeanName = transformedBeanName(bd.getParentName());
                        //当前beanName是否等于父的beanName
                        if (!beanName.equals(parentBeanName)) {
                            pbd = getMergedBeanDefinition(parentBeanName);
                        } else {
                            //ConfigurableBeanFactory的获取方式
                            if (getParentBeanFactory() instanceof ConfigurableBeanFactory parent) {
                                pbd = parent.getMergedBeanDefinition(parentBeanName);
                            } else {
                                throw new NoSuchBeanDefinitionException(parentBeanName, "Parent name '" + parentBeanName + "' is equal to bean name '" + beanName + "': cannot be resolved without a ConfigurableBeanFactory parent");
                            }
                        }
                    } catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName, "Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
                    }
                    // 将父BeanDefinition对象拷贝
                    mbd = new RootBeanDefinition(pbd);
                    //覆盖
                    mbd.overrideFrom(bd);
                }

                // 设置Scope
                if (!StringUtils.hasLength(mbd.getScope())) {
                    mbd.setScope(SCOPE_SINGLETON);
                }

                // 修正作用域
                if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
                    mbd.setScope(containingBd.getScope());
                }

                if (containingBd == null && (isCacheBeanMetadata() || isBeanEligibleForMetadataCaching(beanName))) {
                    // 放入缓存
                    cacheMergedBeanDefinition(mbd, beanName);
                }
            }
            if (previous != null) {
                copyRelevantMergedBeanDefinitionCaches(previous, mbd);
            }
            return mbd;
        }
    }

    private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
        if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
                ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
                ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
            ResolvableType targetType = mbd.targetType;
            ResolvableType previousTargetType = previous.targetType;
            if (targetType == null || targetType.equals(previousTargetType)) {
                mbd.targetType = previousTargetType;
                mbd.isFactoryBean = previous.isFactoryBean;
                mbd.resolvedTargetType = previous.resolvedTargetType;
                mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
                mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
            }
            if (previous.hasMethodOverrides()) {
                mbd.setMethodOverrides(new MethodOverrides(previous.getMethodOverrides()));
            }
        }
    }


    protected void cacheMergedBeanDefinition(RootBeanDefinition mbd, String beanName) {
        this.mergedBeanDefinitions.put(beanName, mbd);
    }

    //校验合并BeanDefinition
    protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, Object[] args) {
        if (mbd.isAbstract()) {
            throw new BeanIsAbstractException(beanName);
        }
    }

    //
    protected void clearMergedBeanDefinition(String beanName) {
        RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
        if (bd != null) {
            bd.stale = true;
        }
    }


    public void clearMetadataCache() {
        this.mergedBeanDefinitions.forEach((beanName, bd) -> {
            if (!isBeanEligibleForMetadataCaching(beanName)) {
                bd.stale = true;
            }
        });
    }


    protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
            throws CannotLoadBeanClassException {

        try {
            if (mbd.hasBeanClass()) {
                return mbd.getBeanClass();
            }
            Class<?> beanClass = doResolveBeanClass(mbd, typesToMatch);
            if (mbd.hasBeanClass()) {
                mbd.prepareMethodOverrides();
            }
            return beanClass;
        } catch (ClassNotFoundException ex) {
            throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
        } catch (LinkageError err) {
            throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
        } catch (BeanDefinitionValidationException ex) {
            throw new BeanDefinitionStoreException(mbd.getResourceDescription(),
                    beanName, "Validation of method overrides failed", ex);
        }
    }

    private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
            throws ClassNotFoundException {

        ClassLoader beanClassLoader = getBeanClassLoader();
        ClassLoader dynamicLoader = beanClassLoader;
        boolean freshResolve = false;

        if (!ObjectUtils.isEmpty(typesToMatch)) {
            // When just doing type checks (i.e. not creating an actual instance yet),
            // use the specified temporary class loader (for example, in a weaving scenario).
            ClassLoader tempClassLoader = getTempClassLoader();
            if (tempClassLoader != null) {
                dynamicLoader = tempClassLoader;
                freshResolve = true;
                if (tempClassLoader instanceof DecoratingClassLoader dcl) {
                    for (Class<?> typeToMatch : typesToMatch) {
                        dcl.excludeClass(typeToMatch.getName());
                    }
                }
            }
        }

        String className = mbd.getBeanClassName();
        if (className != null) {
            Object evaluated = evaluateBeanDefinitionString(className, mbd);
            if (!className.equals(evaluated)) {
                // A dynamically resolved expression, supported as of 4.2...
                if (evaluated instanceof Class<?> clazz) {
                    return clazz;
                } else if (evaluated instanceof String name) {
                    className = name;
                    freshResolve = true;
                } else {
                    throw new IllegalStateException("Invalid class name expression result: " + evaluated);
                }
            }
            if (freshResolve) {
                // When resolving against a temporary class loader, exit early in order
                // to avoid storing the resolved Class in the bean definition.
                if (dynamicLoader != null) {
                    try {
                        return dynamicLoader.loadClass(className);
                    } catch (ClassNotFoundException ex) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
                        }
                    }
                }
                return ClassUtils.forName(className, dynamicLoader);
            }
        }

        // Resolve regularly, caching the result in the BeanDefinition...
        return mbd.resolveBeanClass(beanClassLoader);
    }


    protected Object evaluateBeanDefinitionString(String value, BeanDefinition beanDefinition) {
        if (this.beanExpressionResolver == null) {
            return value;
        }

        Scope scope = null;
        if (beanDefinition != null) {
            String scopeName = beanDefinition.getScope();
            if (scopeName != null) {
                scope = getRegisteredScope(scopeName);
            }
        }
        return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
    }


    protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
        Class<?> targetType = mbd.getTargetType();
        if (targetType != null) {
            return targetType;
        }
        if (mbd.getFactoryMethodName() != null) {
            return null;
        }
        return resolveBeanClass(mbd, beanName, typesToMatch);
    }


    protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
        Boolean result = mbd.isFactoryBean;
        if (result == null) {
            Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
            result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
            mbd.isFactoryBean = result;
        }
        return result;
    }


    protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
        try {
            ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
            if (result != ResolvableType.NONE) {
                return result;
            }
        } catch (IllegalArgumentException ex) {
            throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
                    String.valueOf(ex.getMessage()));
        }

        if (allowInit && mbd.isSingleton()) {
            try {
                FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
                Class<?> objectType = getTypeForFactoryBean(factoryBean);
                return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
            } catch (BeanCreationException ex) {
                if (ex.contains(BeanCurrentlyInCreationException.class)) {
                    logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
                } else if (mbd.isLazyInit()) {
                    logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
                } else {
                    logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
                }
                onSuppressedException(ex);
            }
        }

        // FactoryBean type not resolvable
        return ResolvableType.NONE;
    }

    //标记为已创建
    protected void markBeanAsCreated(String beanName) {
        //判断是否包含
        if (!this.alreadyCreated.contains(beanName)) {
            synchronized (this.mergedBeanDefinitions) {
                if (!isBeanEligibleForMetadataCaching(beanName)) {
                    //stale设置为true
                    clearMergedBeanDefinition(beanName);
                }
                //添加到已创建集合
                this.alreadyCreated.add(beanName);
            }
        }
    }


    protected void cleanupAfterBeanCreationFailure(String beanName) {
        synchronized (this.mergedBeanDefinitions) {
            this.alreadyCreated.remove(beanName);
        }
    }


    protected boolean isBeanEligibleForMetadataCaching(String beanName) {
        return this.alreadyCreated.contains(beanName);
    }


    protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
        if (!this.alreadyCreated.contains(beanName)) {
            removeSingleton(beanName);
            return true;
        } else {
            return false;
        }
    }


    protected boolean hasBeanCreationStarted() {
        return !this.alreadyCreated.isEmpty();
    }

    //获取bean实例
    protected Object getObjectForBeanInstance(Object beanInstance, Class<?> requiredType, String name, String beanName, RootBeanDefinition mbd) {

        // 判断beanName是不是bean工厂
        if (BeanFactoryUtils.isFactoryDereference(name)) {
            if (beanInstance instanceof NullBean) {
                return beanInstance;
            }
            if (!(beanInstance instanceof FactoryBean)) {
                throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
            }
            if (mbd != null) {
                mbd.isFactoryBean = true;
            }
            //返回实例
            return beanInstance;
        }

        //判断是否是factoryBean
        if (!(beanInstance instanceof FactoryBean<?> factoryBean)) {
            //不是直接返回
            return beanInstance;
        }

        Object object = null;
        if (mbd != null) {
            mbd.isFactoryBean = true;
        } else {
            // 缓存中获取
            object = getCachedObjectForFactoryBean(beanName);
        }
        if (object == null) {
            // 如果还是null从FactoryBean中创建
            if (mbd == null && containsBeanDefinition(beanName)) {
                mbd = getMergedLocalBeanDefinition(beanName);
            }
            boolean synthetic = (mbd != null && mbd.isSynthetic());
            // 从FactoryBean中获取bean实例
            object = getObjectFromFactoryBean(factoryBean, requiredType, beanName, !synthetic);
        }
        return object;
    }


    public boolean isBeanNameInUse(String beanName) {
        return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
    }


    protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
        return (bean.getClass() != NullBean.class && (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) ||
                (hasDestructionAwareBeanPostProcessors() && DisposableBeanAdapter.hasApplicableProcessors(
                        bean, getBeanPostProcessorCache().destructionAware))));
    }


    protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
        if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
            if (mbd.isSingleton()) {
                // Register a DisposableBean implementation that performs all destruction
                // work for the given bean: DestructionAwareBeanPostProcessors,
                // DisposableBean interface, custom destroy method.
                registerDisposableBean(beanName, new DisposableBeanAdapter(
                        bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
            } else {
                // A bean with a custom scope...
                Scope scope = this.scopes.get(mbd.getScope());
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
                }
                scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(
                        bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
            }
        }
    }


    //---------------------------------------------------------------------
    // Abstract methods to be implemented by subclasses
    //---------------------------------------------------------------------


    protected abstract boolean containsBeanDefinition(String beanName);


    protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;


    protected abstract Object createBean(String beanName, RootBeanDefinition mbd, Object[] args)
            throws BeanCreationException;


    @SuppressWarnings("serial")
    private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {

        @Override
        public BeanPostProcessor set(int index, BeanPostProcessor element) {
            BeanPostProcessor result = super.set(index, element);
            resetBeanPostProcessorCache();
            return result;
        }

        @Override
        public boolean add(BeanPostProcessor o) {
            boolean success = super.add(o);
            resetBeanPostProcessorCache();
            return success;
        }

        @Override
        public void add(int index, BeanPostProcessor element) {
            super.add(index, element);
            resetBeanPostProcessorCache();
        }

        @Override
        public BeanPostProcessor remove(int index) {
            BeanPostProcessor result = super.remove(index);
            resetBeanPostProcessorCache();
            return result;
        }

        @Override
        public boolean remove(Object o) {
            boolean success = super.remove(o);
            if (success) {
                resetBeanPostProcessorCache();
            }
            return success;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean success = super.removeAll(c);
            if (success) {
                resetBeanPostProcessorCache();
            }
            return success;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean success = super.retainAll(c);
            if (success) {
                resetBeanPostProcessorCache();
            }
            return success;
        }

        @Override
        public boolean addAll(Collection<? extends BeanPostProcessor> c) {
            boolean success = super.addAll(c);
            if (success) {
                resetBeanPostProcessorCache();
            }
            return success;
        }

        @Override
        public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
            boolean success = super.addAll(index, c);
            if (success) {
                resetBeanPostProcessorCache();
            }
            return success;
        }

        @Override
        public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
            boolean success = super.removeIf(filter);
            if (success) {
                resetBeanPostProcessorCache();
            }
            return success;
        }

        @Override
        public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
            super.replaceAll(operator);
            resetBeanPostProcessorCache();
        }
    }


    static class BeanPostProcessorCache {

        final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();

        final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();

        final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();

        final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
    }


    class BeanFactoryDefaultEditorRegistrar implements PropertyEditorRegistrar {

        @Override
        public void registerCustomEditors(PropertyEditorRegistry registry) {
            applyEditorRegistrars(registry, defaultEditorRegistrars);
        }
    }

}
