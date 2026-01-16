package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.*;
import com.t13max.ioc.beans.factory.config.*;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.metrics.StartupStep;
import com.t13max.ioc.lang.Contract;
import com.t13max.ioc.utils.*;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @Author: t13max
 * @Since: 22:16 2026/1/14
 */
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {


    public static final String STRICT_LOCKING_PROPERTY_NAME = "spring.locking.strict";

    private static Class<?> jakartaInjectProviderClass;

    static {
        try {
            jakartaInjectProviderClass =
                    ClassUtils.forName("jakarta.inject.Provider", DefaultListableBeanFactory.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // JSR-330 API not available - Provider interface simply not supported then.
            jakartaInjectProviderClass = null;
        }
    }


    private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories = new ConcurrentHashMap<>(8);

    private final Boolean strictLocking = SpringProperties.checkFlag(STRICT_LOCKING_PROPERTY_NAME);

    private String serializationId;

    private Boolean allowBeanDefinitionOverriding;

    private boolean allowEagerClassLoading = true;

    private Executor bootstrapExecutor;

    private Comparator<Object> dependencyComparator;

    private AutowireCandidateResolver autowireCandidateResolver = SimpleAutowireCandidateResolver.INSTANCE;

    private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);
    //BeanDefinition集合 beanName -> BeanDefinition
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

    private final Map<String, BeanDefinitionHolder> mergedBeanDefinitionHolders = new ConcurrentHashMap<>(256);

    private final Set<String> primaryBeanNames = ConcurrentHashMap.newKeySet(16);

    private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);

    private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);
    //按注册顺序排列的beanDefinition名称列表
    private volatile List<String> beanDefinitionNames = new ArrayList<>(256);
    //
    private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

    private volatile String[] frozenBeanDefinitionNames;

    private volatile boolean configurationFrozen;

    private volatile String mainThreadPrefix;

    private final NamedThreadLocal<PreInstantiation> preInstantiationThread =
            new NamedThreadLocal<>("Pre-instantiation thread marker");


    public DefaultListableBeanFactory() {
        super();
    }

    public DefaultListableBeanFactory(BeanFactory parentBeanFactory) {
        super(parentBeanFactory);
    }


    public void setSerializationId(String serializationId) {
        if (serializationId != null) {
            serializableFactories.put(serializationId, new WeakReference<>(this));
        } else if (this.serializationId != null) {
            serializableFactories.remove(this.serializationId);
        }
        this.serializationId = serializationId;
    }

    public String getSerializationId() {
        return this.serializationId;
    }

    public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
        this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
    }

    public boolean isAllowBeanDefinitionOverriding() {
        return !Boolean.FALSE.equals(this.allowBeanDefinitionOverriding);
    }

    public void setAllowEagerClassLoading(boolean allowEagerClassLoading) {
        this.allowEagerClassLoading = allowEagerClassLoading;
    }

    public boolean isAllowEagerClassLoading() {
        return this.allowEagerClassLoading;
    }

    @Override
    public void setBootstrapExecutor(Executor bootstrapExecutor) {
        this.bootstrapExecutor = bootstrapExecutor;
    }

    @Override
    public Executor getBootstrapExecutor() {
        return this.bootstrapExecutor;
    }

    public void setDependencyComparator(Comparator<Object> dependencyComparator) {
        this.dependencyComparator = dependencyComparator;
    }

    public Comparator<Object> getDependencyComparator() {
        return this.dependencyComparator;
    }

    public void setAutowireCandidateResolver(AutowireCandidateResolver autowireCandidateResolver) {
        Assert.notNull(autowireCandidateResolver, "AutowireCandidateResolver must not be null");
        if (autowireCandidateResolver instanceof BeanFactoryAware beanFactoryAware) {
            beanFactoryAware.setBeanFactory(this);
        }
        this.autowireCandidateResolver = autowireCandidateResolver;
    }

    public AutowireCandidateResolver getAutowireCandidateResolver() {
        return this.autowireCandidateResolver;
    }


    @Override
    public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
        super.copyConfigurationFrom(otherFactory);
        if (otherFactory instanceof DefaultListableBeanFactory otherListableFactory) {
            this.allowBeanDefinitionOverriding = otherListableFactory.allowBeanDefinitionOverriding;
            this.allowEagerClassLoading = otherListableFactory.allowEagerClassLoading;
            this.bootstrapExecutor = otherListableFactory.bootstrapExecutor;
            this.dependencyComparator = otherListableFactory.dependencyComparator;
            // A clone of the AutowireCandidateResolver since it is potentially BeanFactoryAware
            setAutowireCandidateResolver(otherListableFactory.getAutowireCandidateResolver().cloneIfNecessary());
            // Make resolvable dependencies (for example, ResourceLoader) available here as well
            this.resolvableDependencies.putAll(otherListableFactory.resolvableDependencies);
        }
    }


    //---------------------------------------------------------------------
    // Implementation of remaining BeanFactory methods
    //---------------------------------------------------------------------

    @Override
    public <T> T getBean(Class<T> requiredType) throws BeansException {
        return getBean(requiredType, (Object[]) null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
        Assert.notNull(requiredType, "Required type must not be null");
        Object resolved = resolveBean(ResolvableType.forRawClass(requiredType), args, false);
        if (resolved == null) {
            throw new NoSuchBeanDefinitionException(requiredType);
        }
        return (T) resolved;
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        Assert.notNull(requiredType, "Required type must not be null");
        return getBeanProvider(ResolvableType.forRawClass(requiredType), true);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        return getBeanProvider(requiredType, true);
    }


    //---------------------------------------------------------------------
    // Implementation of ListableBeanFactory interface
    //---------------------------------------------------------------------

    @Override
    public boolean containsBeanDefinition(String beanName) {
        Assert.notNull(beanName, "Bean name must not be null");
        return this.beanDefinitionMap.containsKey(beanName);
    }

    @Override
    public int getBeanDefinitionCount() {
        return this.beanDefinitionMap.size();
    }

    @Override
    public String[] getBeanDefinitionNames() {
        String[] frozenNames = this.frozenBeanDefinitionNames;
        if (frozenNames != null) {
            return frozenNames.clone();
        } else {
            return StringUtils.toStringArray(this.beanDefinitionNames);
        }
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
        Assert.notNull(requiredType, "Required type must not be null");
        return getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
        return new BeanObjectProvider<>() {
            @Override
            public T getObject() throws BeansException {
                T resolved = resolveBean(requiredType, null, false);
                if (resolved == null) {
                    throw new NoSuchBeanDefinitionException(requiredType);
                }
                return resolved;
            }

            @Override
            public T getObject(Object... args) throws BeansException {
                T resolved = resolveBean(requiredType, args, false);
                if (resolved == null) {
                    throw new NoSuchBeanDefinitionException(requiredType);
                }
                return resolved;
            }

            @Override
            public T getIfAvailable() throws BeansException {
                try {
                    return resolveBean(requiredType, null, false);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope
                    return null;
                }
            }

            @Override
            public void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
                T dependency = getIfAvailable();
                if (dependency != null) {
                    try {
                        dependencyConsumer.accept(dependency);
                    } catch (ScopeNotActiveException ex) {
                        // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                    }
                }
            }

            @Override
            public T getIfUnique() throws BeansException {
                try {
                    return resolveBean(requiredType, null, true);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope
                    return null;
                }
            }

            @Override
            public void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
                T dependency = getIfUnique();
                if (dependency != null) {
                    try {
                        dependencyConsumer.accept(dependency);
                    } catch (ScopeNotActiveException ex) {
                        // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                    }
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> stream() {
                return Arrays.stream(beanNamesForStream(requiredType, true, allowEagerInit))
                        .map(name -> (T) getBean(name))
                        .filter(bean -> !(bean instanceof NullBean));
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> orderedStream() {
                String[] beanNames = beanNamesForStream(requiredType, true, allowEagerInit);
                if (beanNames.length == 0) {
                    return Stream.empty();
                }
                Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);
                for (String beanName : beanNames) {
                    Object beanInstance = getBean(beanName);
                    if (!(beanInstance instanceof NullBean)) {
                        matchingBeans.put(beanName, (T) beanInstance);
                    }
                }
                Stream<T> stream = matchingBeans.values().stream();
                return stream.sorted(adaptOrderComparator(matchingBeans));
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> stream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
                return Arrays.stream(beanNamesForStream(requiredType, includeNonSingletons, allowEagerInit))
                        .filter(name -> customFilter.test(getType(name)))
                        .map(name -> (T) getBean(name))
                        .filter(bean -> !(bean instanceof NullBean));
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> orderedStream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
                String[] beanNames = beanNamesForStream(requiredType, includeNonSingletons, allowEagerInit);
                if (beanNames.length == 0) {
                    return Stream.empty();
                }
                Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);
                for (String beanName : beanNames) {
                    if (customFilter.test(getType(beanName))) {
                        Object beanInstance = getBean(beanName);
                        if (!(beanInstance instanceof NullBean)) {
                            matchingBeans.put(beanName, (T) beanInstance);
                        }
                    }
                }
                return matchingBeans.values().stream().sorted(adaptOrderComparator(matchingBeans));
            }
        };
    }

    private <T> T resolveBean(ResolvableType requiredType, Object[] args, boolean nonUniqueAsNull) {
        NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args, nonUniqueAsNull);
        if (namedBean != null) {
            return namedBean.getBeanInstance();
        }
        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory dlbf) {
            return dlbf.resolveBean(requiredType, args, nonUniqueAsNull);
        } else if (parent != null) {
            ObjectProvider<T> parentProvider = parent.getBeanProvider(requiredType);
            if (args != null) {
                return parentProvider.getObject(args);
            } else {
                return (nonUniqueAsNull ? parentProvider.getIfUnique() : parentProvider.getIfAvailable());
            }
        }
        return null;
    }

    private String[] beanNamesForStream(ResolvableType requiredType, boolean includeNonSingletons, boolean allowEagerInit) {
        return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, includeNonSingletons, allowEagerInit);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type) {
        return getBeanNamesForType(type, true, true);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        Class<?> resolved = type.resolve();
        if (resolved != null && !type.hasGenerics()) {
            return getBeanNamesForType(resolved, includeNonSingletons, allowEagerInit);
        } else {
            return doGetBeanNamesForType(type, includeNonSingletons, allowEagerInit);
        }
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type) {
        return getBeanNamesForType(type, true, true);
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        if (!isConfigurationFrozen() || type == null || !allowEagerInit) {
            return doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit);
        }
        Map<Class<?>, String[]> cache =
                (includeNonSingletons ? this.allBeanNamesByType : this.singletonBeanNamesByType);
        String[] resolvedBeanNames = cache.get(type);
        if (resolvedBeanNames != null) {
            return resolvedBeanNames;
        }
        resolvedBeanNames = doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true);
        if (ClassUtils.isCacheSafe(type, getBeanClassLoader())) {
            cache.put(type, resolvedBeanNames);
        }
        return resolvedBeanNames;
    }

    private String[] doGetBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        List<String> result = new ArrayList<>();

        // Check all bean definitions.
        for (String beanName : this.beanDefinitionNames) {
            // Only consider bean as eligible if the bean name is not defined as alias for some other bean.
            if (!isAlias(beanName)) {
                try {
                    RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                    // Only check bean definition if it is complete.
                    if (!mbd.isAbstract() && (allowEagerInit ||
                            (mbd.hasBeanClass() || !mbd.isLazyInit() || isAllowEagerClassLoading()) &&
                                    !requiresEagerInitForType(mbd.getFactoryBeanName()))) {
                        boolean isFactoryBean = isFactoryBean(beanName, mbd);
                        BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
                        boolean matchFound = false;
                        boolean allowFactoryBeanInit = (allowEagerInit || containsSingleton(beanName));
                        boolean isNonLazyDecorated = (dbd != null && !mbd.isLazyInit());
                        if (!isFactoryBean) {
                            if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                            }
                        } else {
                            if (includeNonSingletons || isNonLazyDecorated) {
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                            } else if (allowFactoryBeanInit) {
                                // Type check before singleton check, avoiding FactoryBean instantiation
                                // for early FactoryBean.isSingleton() calls on non-matching beans.
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit) &&
                                        isSingleton(beanName, mbd, dbd);
                            }
                            if (!matchFound) {
                                // In case of FactoryBean, try to match FactoryBean instance itself next.
                                beanName = FACTORY_BEAN_PREFIX + beanName;
                                if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
                                    matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                                }
                            }
                        }
                        if (matchFound) {
                            result.add(beanName);
                        }
                    }
                } catch (CannotLoadBeanClassException | BeanDefinitionStoreException ex) {
                    if (allowEagerInit) {
                        throw ex;
                    }
                    // Probably a placeholder: let's ignore it for type matching purposes.
                    LogMessage message = (ex instanceof CannotLoadBeanClassException ?
                            LogMessage.format("Ignoring bean class loading failure for bean '%s'", beanName) :
                            LogMessage.format("Ignoring unresolvable metadata in bean definition '%s'", beanName));
                    logger.trace(message, ex);
                    // Register exception, in case the bean was accidentally unresolvable.
                    onSuppressedException(ex);
                } catch (NoSuchBeanDefinitionException ex) {
                    // Bean definition got removed while we were iterating -> ignore.
                }
            }
        }

        // Check manually registered singletons too.
        for (String beanName : this.manualSingletonNames) {
            try {
                // In case of FactoryBean, match object created by FactoryBean.
                if (isFactoryBean(beanName)) {
                    if ((includeNonSingletons || isSingleton(beanName)) && isTypeMatch(beanName, type)) {
                        result.add(beanName);
                        // Match found for this bean: do not match FactoryBean itself anymore.
                        continue;
                    }
                    // In case of FactoryBean, try to match FactoryBean itself next.
                    beanName = FACTORY_BEAN_PREFIX + beanName;
                }
                // Match raw bean instance (might be raw FactoryBean).
                if (isTypeMatch(beanName, type)) {
                    result.add(beanName);
                }
            } catch (NoSuchBeanDefinitionException ex) {
                // Shouldn't happen - probably a result of circular reference resolution...
                logger.trace(LogMessage.format(
                        "Failed to check manually registered singleton with name '%s'", beanName), ex);
            }
        }

        return StringUtils.toStringArray(result);
    }

    private boolean isSingleton(String beanName, RootBeanDefinition mbd, BeanDefinitionHolder dbd) {
        return (dbd != null ? mbd.isSingleton() : isSingleton(beanName));
    }

    private boolean requiresEagerInitForType(String factoryBeanName) {
        return (factoryBeanName != null && isFactoryBean(factoryBeanName) && !containsSingleton(factoryBeanName));
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        return getBeansOfType(type, true, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBeansOfType(
            Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {

        String[] beanNames = getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
        Map<String, T> result = CollectionUtils.newLinkedHashMap(beanNames.length);
        for (String beanName : beanNames) {
            try {
                Object beanInstance = (type != null ? getBean(beanName, type) : getBean(beanName));
                if (!(beanInstance instanceof NullBean)) {
                    result.put(beanName, (T) beanInstance);
                }
            } catch (BeanNotOfRequiredTypeException ex) {
                // Ignore - probably a NullBean
            } catch (BeanCreationException ex) {
                Throwable rootCause = ex.getMostSpecificCause();
                if (rootCause instanceof BeanCurrentlyInCreationException bce) {
                    String exBeanName = bce.getBeanName();
                    if (exBeanName != null && isCurrentlyInCreation(exBeanName)) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Ignoring match to currently created bean '" + exBeanName + "': " +
                                    ex.getMessage());
                        }
                        onSuppressedException(ex);
                        // Ignore: indicates a circular reference when autowiring constructors.
                        // We want to find matches other than the currently created bean itself.
                        continue;
                    }
                }
                throw ex;
            }
        }
        return result;
    }

    @Override
    public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        List<String> result = new ArrayList<>();
        for (String beanName : this.beanDefinitionNames) {
            BeanDefinition bd = this.beanDefinitionMap.get(beanName);
            if (bd != null && !bd.isAbstract() && findAnnotationOnBean(beanName, annotationType) != null) {
                result.add(beanName);
            }
        }
        for (String beanName : this.manualSingletonNames) {
            if (!result.contains(beanName) && findAnnotationOnBean(beanName, annotationType) != null) {
                result.add(beanName);
            }
        }
        return StringUtils.toStringArray(result);
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        String[] beanNames = getBeanNamesForAnnotation(annotationType);
        Map<String, Object> result = CollectionUtils.newLinkedHashMap(beanNames.length);
        for (String beanName : beanNames) {
            Object beanInstance = getBean(beanName);
            if (!(beanInstance instanceof NullBean)) {
                result.put(beanName, beanInstance);
            }
        }
        return result;
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
            throws NoSuchBeanDefinitionException {

        return findAnnotationOnBean(beanName, annotationType, true);
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {

        Class<?> beanType = getType(beanName, allowFactoryBeanInit);
        if (beanType != null) {
            MergedAnnotation<A> annotation =
                    MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
            if (annotation.isPresent()) {
                return annotation.synthesize();
            }
        }
        if (containsBeanDefinition(beanName)) {
            RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            // Check raw bean class, for example, in case of a proxy.
            if (bd.hasBeanClass() && bd.getFactoryMethodName() == null) {
                Class<?> beanClass = bd.getBeanClass();
                if (beanClass != beanType) {
                    MergedAnnotation<A> annotation =
                            MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
                    if (annotation.isPresent()) {
                        return annotation.synthesize();
                    }
                }
            }
            // Check annotations declared on factory method, if any.
            Method factoryMethod = bd.getResolvedFactoryMethod();
            if (factoryMethod != null) {
                MergedAnnotation<A> annotation =
                        MergedAnnotations.from(factoryMethod, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
                if (annotation.isPresent()) {
                    return annotation.synthesize();
                }
            }
        }
        return null;
    }

    @Override
    public <A extends Annotation> Set<A> findAllAnnotationsOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {

        Set<A> annotations = new LinkedHashSet<>();
        Class<?> beanType = getType(beanName, allowFactoryBeanInit);
        if (beanType != null) {
            MergedAnnotations.from(beanType, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                    .stream(annotationType)
                    .filter(MergedAnnotation::isPresent)
                    .forEach(mergedAnnotation -> annotations.add(mergedAnnotation.synthesize()));
        }
        if (containsBeanDefinition(beanName)) {
            RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            // Check raw bean class, for example, in case of a proxy.
            if (bd.hasBeanClass() && bd.getFactoryMethodName() == null) {
                Class<?> beanClass = bd.getBeanClass();
                if (beanClass != beanType) {
                    MergedAnnotations.from(beanClass, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                            .stream(annotationType)
                            .filter(MergedAnnotation::isPresent)
                            .forEach(mergedAnnotation -> annotations.add(mergedAnnotation.synthesize()));
                }
            }
            // Check annotations declared on factory method, if any.
            Method factoryMethod = bd.getResolvedFactoryMethod();
            if (factoryMethod != null) {
                MergedAnnotations.from(factoryMethod, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        .stream(annotationType)
                        .filter(MergedAnnotation::isPresent)
                        .forEach(mergedAnnotation -> annotations.add(mergedAnnotation.synthesize()));
            }
        }
        return annotations;
    }


    //---------------------------------------------------------------------
    // Implementation of ConfigurableListableBeanFactory interface
    //---------------------------------------------------------------------

    @Override
    public void registerResolvableDependency(Class<?> dependencyType, Object autowiredValue) {
        Assert.notNull(dependencyType, "Dependency type must not be null");
        if (autowiredValue != null) {
            if (!(autowiredValue instanceof ObjectFactory || dependencyType.isInstance(autowiredValue))) {
                throw new IllegalArgumentException("Value [" + autowiredValue +
                        "] does not implement specified dependency type [" + dependencyType.getName() + "]");
            }
            this.resolvableDependencies.put(dependencyType, autowiredValue);
        }
    }

    @Override
    public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
            throws NoSuchBeanDefinitionException {

        return isAutowireCandidate(beanName, descriptor, getAutowireCandidateResolver());
    }

    protected boolean isAutowireCandidate(
            String beanName, DependencyDescriptor descriptor, AutowireCandidateResolver resolver)
            throws NoSuchBeanDefinitionException {

        String bdName = transformedBeanName(beanName);
        if (containsBeanDefinition(bdName)) {
            return isAutowireCandidate(beanName, getMergedLocalBeanDefinition(bdName), descriptor, resolver);
        } else if (containsSingleton(beanName)) {
            return isAutowireCandidate(beanName, new RootBeanDefinition(getType(beanName)), descriptor, resolver);
        }

        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory dlbf) {
            // No bean definition found in this factory -> delegate to parent.
            return dlbf.isAutowireCandidate(beanName, descriptor, resolver);
        } else if (parent instanceof ConfigurableListableBeanFactory clbf) {
            // If no DefaultListableBeanFactory, can't pass the resolver along.
            return clbf.isAutowireCandidate(beanName, descriptor);
        } else {
            return true;
        }
    }

    protected boolean isAutowireCandidate(String beanName, RootBeanDefinition mbd,
                                          DependencyDescriptor descriptor, AutowireCandidateResolver resolver) {

        String bdName = transformedBeanName(beanName);
        resolveBeanClass(mbd, bdName);
        if (mbd.isFactoryMethodUnique && mbd.factoryMethodToIntrospect == null) {
            new ConstructorResolver(this).resolveFactoryMethodIfPossible(mbd);
        }
        BeanDefinitionHolder holder = (beanName.equals(bdName) ?
                this.mergedBeanDefinitionHolders.computeIfAbsent(beanName,
                        key -> new BeanDefinitionHolder(mbd, beanName, getAliases(bdName))) :
                new BeanDefinitionHolder(mbd, beanName, getAliases(bdName)));
        return resolver.isAutowireCandidate(holder, descriptor);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        BeanDefinition bd = this.beanDefinitionMap.get(beanName);
        if (bd == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No bean named {} found in {}", beanName, this);
            }
            throw new NoSuchBeanDefinitionException(beanName);
        }
        return bd;
    }

    @Override
    public Iterator<String> getBeanNamesIterator() {
        CompositeIterator<String> iterator = new CompositeIterator<>();
        iterator.add(this.beanDefinitionNames.iterator());
        iterator.add(this.manualSingletonNames.iterator());
        return iterator;
    }

    @Override
    protected void clearMergedBeanDefinition(String beanName) {
        super.clearMergedBeanDefinition(beanName);
        this.mergedBeanDefinitionHolders.remove(beanName);
    }

    @Override
    public void clearMetadataCache() {
        super.clearMetadataCache();
        this.mergedBeanDefinitionHolders.clear();
        clearByTypeCache();
    }

    @Override
    public void freezeConfiguration() {
        clearMetadataCache();
        this.configurationFrozen = true;
        this.frozenBeanDefinitionNames = StringUtils.toStringArray(this.beanDefinitionNames);
    }

    @Override
    public boolean isConfigurationFrozen() {
        return this.configurationFrozen;
    }

    @Override
    protected boolean isBeanEligibleForMetadataCaching(String beanName) {
        return (this.configurationFrozen || super.isBeanEligibleForMetadataCaching(beanName));
    }

    @Override
    protected Object obtainInstanceFromSupplier(Supplier<?> supplier, String beanName, RootBeanDefinition mbd)
            throws Exception {

        if (supplier instanceof InstanceSupplier<?> instanceSupplier) {
            return instanceSupplier.get(RegisteredBean.of(this, beanName, mbd));
        }
        return super.obtainInstanceFromSupplier(supplier, beanName, mbd);
    }

    @Override
    protected void cacheMergedBeanDefinition(RootBeanDefinition mbd, String beanName) {
        super.cacheMergedBeanDefinition(mbd, beanName);
        if (mbd.isPrimary()) {
            this.primaryBeanNames.add(beanName);
        }
    }

    @Override
    protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, Object[] args) {
        super.checkMergedBeanDefinition(mbd, beanName, args);

        if (mbd.isBackgroundInit()) {
            if (this.preInstantiationThread.get() == PreInstantiation.MAIN && getBootstrapExecutor() != null) {
                throw new BeanCurrentlyInCreationException(beanName, "Bean marked for background " +
                        "initialization but requested in mainline thread - declare ObjectProvider " +
                        "or lazy injection point in dependent mainline beans");
            }
        } else {
            // Bean intended to be initialized in main bootstrap thread.
            if (this.preInstantiationThread.get() == PreInstantiation.BACKGROUND) {
                throw new BeanCurrentlyInCreationException(beanName, "Bean marked for mainline initialization " +
                        "but requested in background thread - enforce early instantiation in mainline thread " +
                        "through depends-on '" + beanName + "' declaration for dependent background beans");
            }
        }
    }

    @Override
    protected Boolean isCurrentThreadAllowedToHoldSingletonLock() {
        String mainThreadPrefix = this.mainThreadPrefix;
        if (mainThreadPrefix != null) {
            // We only differentiate in the preInstantiateSingletons phase, using
            // the volatile mainThreadPrefix field as an indicator for that phase.

            PreInstantiation preInstantiation = this.preInstantiationThread.get();
            if (preInstantiation != null) {
                // A Spring-managed bootstrap thread:
                // MAIN is allowed to lock (true) or even forced to lock (null),
                // BACKGROUND is never allowed to lock (false).
                return switch (preInstantiation) {
                    case MAIN -> (Boolean.TRUE.equals(this.strictLocking) ? null : true);
                    case BACKGROUND -> false;
                };
            }

            // Not a Spring-managed bootstrap thread...
            if (Boolean.FALSE.equals(this.strictLocking)) {
                // Explicitly configured to use lenient locking wherever possible.
                return true;
            } else if (this.strictLocking == null) {
                // No explicit locking configuration -> infer appropriate locking.
                if (!getThreadNamePrefix().equals(mainThreadPrefix)) {
                    // An unmanaged thread (assumed to be application-internal) with lenient locking,
                    // and not part of the same thread pool that provided the main bootstrap thread
                    // (excluding scenarios where we are hit by multiple external bootstrap threads).
                    return true;
                }
            }
        }

        // Traditional behavior: forced to always hold a full lock.
        return null;
    }

    @Override
    public void preInstantiateSingletons() throws BeansException {
        if (logger.isTraceEnabled()) {
            logger.trace("Pre-instantiating singletons in " + this);
        }

        // Iterate over a copy to allow for init methods which in turn register new bean definitions.
        // While this may not be part of the regular factory bootstrap, it does otherwise work fine.
        List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

        // Trigger initialization of all non-lazy singleton beans...
        List<CompletableFuture<?>> futures = new ArrayList<>();

        this.preInstantiationThread.set(PreInstantiation.MAIN);
        this.mainThreadPrefix = getThreadNamePrefix();
        try {
            for (String beanName : beanNames) {
                RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                if (!mbd.isAbstract() && mbd.isSingleton()) {
                    CompletableFuture<?> future = preInstantiateSingleton(beanName, mbd);
                    if (future != null) {
                        futures.add(future);
                    }
                }
            }
        } finally {
            this.mainThreadPrefix = null;
            this.preInstantiationThread.remove();
        }

        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
            } catch (CompletionException ex) {
                ReflectionUtils.rethrowRuntimeException(ex.getCause());
            }
        }

        // Trigger post-initialization callback for all applicable beans...
        for (String beanName : beanNames) {
            Object singletonInstance = getSingleton(beanName, false);
            if (singletonInstance instanceof SmartInitializingSingleton smartSingleton) {
                StartupStep smartInitialize = getApplicationStartup().start("spring.beans.smart-initialize")
                        .tag("beanName", beanName);
                smartSingleton.afterSingletonsInstantiated();
                smartInitialize.end();
            }
        }
    }

    private CompletableFuture<?> preInstantiateSingleton(String beanName, RootBeanDefinition mbd) {
        if (mbd.isBackgroundInit()) {
            Executor executor = getBootstrapExecutor();
            if (executor != null) {
                String[] dependsOn = mbd.getDependsOn();
                if (dependsOn != null) {
                    for (String dep : dependsOn) {
                        getBean(dep);
                    }
                }
                CompletableFuture<?> future = CompletableFuture.runAsync(
                        () -> instantiateSingletonInBackgroundThread(beanName), executor);
                addSingletonFactory(beanName, () -> {
                    try {
                        future.join();
                    } catch (CompletionException ex) {
                        ReflectionUtils.rethrowRuntimeException(ex.getCause());
                    }
                    return future;  // not to be exposed, just to lead to ClassCastException in case of mismatch
                });
                return (!mbd.isLazyInit() ? future : null);
            } else if (logger.isInfoEnabled()) {
                logger.info("Bean '" + beanName + "' marked for background initialization " +
                        "without bootstrap executor configured - falling back to mainline initialization");
            }
        }

        if (!mbd.isLazyInit()) {
            try {
                instantiateSingleton(beanName);
            } catch (BeanCurrentlyInCreationException ex) {
                logger.info("Bean '" + beanName + "' marked for pre-instantiation (not lazy-init) " +
                        "but currently initialized by other thread - skipping it in mainline thread");
            }
        }
        return null;
    }

    private void instantiateSingletonInBackgroundThread(String beanName) {
        this.preInstantiationThread.set(PreInstantiation.BACKGROUND);
        try {
            instantiateSingleton(beanName);
        } catch (RuntimeException | Error ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to instantiate singleton bean '" + beanName + "' in background thread", ex);
            }
            throw ex;
        } finally {
            this.preInstantiationThread.remove();
        }
    }

    private void instantiateSingleton(String beanName) {
        if (isFactoryBean(beanName)) {
            Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
            if (bean instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isEagerInit()) {
                getBean(beanName);
            }
        } else {
            getBean(beanName);
        }
    }

    private static String getThreadNamePrefix() {
        String name = Thread.currentThread().getName();
        int numberSeparator = name.lastIndexOf('-');
        return (numberSeparator >= 0 ? name.substring(0, numberSeparator) : name);
    }


    //---------------------------------------------------------------------
    // BeanDefinitionRegistry实现
    //---------------------------------------------------------------------

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException {

        Assert.hasText(beanName, "Bean name must not be empty");
        Assert.notNull(beanDefinition, "BeanDefinition must not be null");

        //校验解析的BeanDefiniton对象
        if (beanDefinition instanceof AbstractBeanDefinition abd) {
            try {
                abd.validate();
            } catch (BeanDefinitionValidationException ex) {
                throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName, "Validation of bean definition failed", ex);
            }
        }

        BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
        //检查是否有同名(beanName)的BeanDefinition存在, 存在且不允许覆盖则抛出注册异常, allowBeanDefinitionOverriding默认为true
        if (existingDefinition != null) {
            if (!isBeanDefinitionOverridable(beanName)) {
                throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
            } else {
                logBeanDefinitionOverriding(beanName, beanDefinition, existingDefinition);
            }
            this.beanDefinitionMap.put(beanName, beanDefinition);
        } else {
            //如果允许覆盖同名的bean, 后注册的会覆盖先注册的
            if (isAlias(beanName)) {
                String aliasedName = canonicalName(beanName);
                if (!isBeanDefinitionOverridable(aliasedName)) {
                    if (containsBeanDefinition(aliasedName)) {  // alias for existing bean definition
                        throw new BeanDefinitionOverrideException(beanName, beanDefinition, getBeanDefinition(aliasedName));
                    } else {  // alias pointing to non-existing bean definition
                        throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName, "Cannot register bean definition for bean '" + beanName + "' since there is already an alias for bean '" + aliasedName + "' bound.");
                    }
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Removing alias '" + beanName + "' for bean '" + aliasedName + "' due to registration of bean definition for bean '" + beanName + "': [" + beanDefinition + "]");
                    }
                    removeAlias(beanName);
                }
            }
            if (hasBeanCreationStarted()) {
                // Cannot modify startup-time collection elements anymore (for stable iteration)
                synchronized (this.beanDefinitionMap) {
                    this.beanDefinitionMap.put(beanName, beanDefinition);
                    List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
                    updatedDefinitions.addAll(this.beanDefinitionNames);
                    updatedDefinitions.add(beanName);
                    this.beanDefinitionNames = updatedDefinitions;
                    removeManualSingletonName(beanName);
                }
            } else {
                // Still in startup registration phase
                this.beanDefinitionMap.put(beanName, beanDefinition);
                this.beanDefinitionNames.add(beanName);
                removeManualSingletonName(beanName);
            }
            this.frozenBeanDefinitionNames = null;
        }

        if (existingDefinition != null || containsSingleton(beanName)) {
            resetBeanDefinition(beanName);
        } else if (isConfigurationFrozen()) {
            clearByTypeCache();
        }

        // Cache a primary marker for the given bean.
        if (beanDefinition.isPrimary()) {
            this.primaryBeanNames.add(beanName);
        }
    }

    private void logBeanDefinitionOverriding(String beanName, BeanDefinition beanDefinition,
                                             BeanDefinition existingDefinition) {

        boolean explicitBeanOverride = (this.allowBeanDefinitionOverriding != null);
        if (existingDefinition.getRole() < beanDefinition.getRole()) {
            // for example, was ROLE_APPLICATION, now overriding with ROLE_SUPPORT or ROLE_INFRASTRUCTURE
            if (logger.isInfoEnabled()) {
                logger.info("Overriding user-defined bean definition for bean '" + beanName +
                        "' with a framework-generated bean definition: replacing [" +
                        existingDefinition + "] with [" + beanDefinition + "]");
            }
        } else if (!beanDefinition.equals(existingDefinition)) {
            if (explicitBeanOverride && logger.isInfoEnabled()) {
                logger.info("Overriding bean definition for bean '" + beanName +
                        "' with a different definition: replacing [" + existingDefinition +
                        "] with [" + beanDefinition + "]");
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Overriding bean definition for bean '" + beanName +
                        "' with a different definition: replacing [" + existingDefinition +
                        "] with [" + beanDefinition + "]");
            }
        } else {
            if (explicitBeanOverride && logger.isInfoEnabled()) {
                logger.info("Overriding bean definition for bean '" + beanName +
                        "' with an equivalent definition: replacing [" + existingDefinition +
                        "] with [" + beanDefinition + "]");
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Overriding bean definition for bean '" + beanName +
                        "' with an equivalent definition: replacing [" + existingDefinition +
                        "] with [" + beanDefinition + "]");
            }
        }
    }

    @Override
    public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        Assert.hasText(beanName, "'beanName' must not be empty");

        BeanDefinition bd = this.beanDefinitionMap.remove(beanName);
        if (bd == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No bean named '" + beanName + "' found in " + this);
            }
            throw new NoSuchBeanDefinitionException(beanName);
        }

        if (hasBeanCreationStarted()) {
            // Cannot modify startup-time collection elements anymore (for stable iteration)
            synchronized (this.beanDefinitionMap) {
                List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames);
                updatedDefinitions.remove(beanName);
                this.beanDefinitionNames = updatedDefinitions;
            }
        } else {
            // Still in startup registration phase
            this.beanDefinitionNames.remove(beanName);
        }
        this.frozenBeanDefinitionNames = null;

        resetBeanDefinition(beanName);
    }

    protected void resetBeanDefinition(String beanName) {
        // Remove the merged bean definition for the given bean, if already created.
        clearMergedBeanDefinition(beanName);

        // Remove corresponding bean from singleton cache, if any. Shouldn't usually
        // be necessary, rather just meant for overriding a context's default beans
        // (for example, the default StaticMessageSource in a StaticApplicationContext).
        destroySingleton(beanName);

        // Remove a cached primary marker for the given bean.
        this.primaryBeanNames.remove(beanName);

        // Notify all post-processors that the specified bean definition has been reset.
        for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
            processor.resetBeanDefinition(beanName);
        }

        // Reset all bean definitions that have the given bean as parent (recursively).
        for (String bdName : this.beanDefinitionNames) {
            if (!beanName.equals(bdName)) {
                BeanDefinition bd = this.beanDefinitionMap.get(bdName);
                // Ensure bd is non-null due to potential concurrent modification of beanDefinitionMap.
                if (bd != null && beanName.equals(bd.getParentName())) {
                    resetBeanDefinition(bdName);
                }
            }
        }
    }

    @Override
    public boolean isBeanDefinitionOverridable(String beanName) {
        return isAllowBeanDefinitionOverriding();
    }

    @Override
    protected boolean allowAliasOverriding() {
        return isAllowBeanDefinitionOverriding();
    }

    @Override
    protected void checkForAliasCircle(String name, String alias) {
        super.checkForAliasCircle(name, alias);
        if (!isBeanDefinitionOverridable(alias) && containsBeanDefinition(alias)) {
            throw new IllegalStateException("Cannot register alias '" + alias +
                    "' for name '" + name + "': Alias would override bean definition '" + alias + "'");
        }
    }

    @Override
    public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
        super.registerSingleton(beanName, singletonObject);
        updateManualSingletonNames(set -> set.add(beanName), set -> !this.beanDefinitionMap.containsKey(beanName));
        clearByTypeCache();
    }

    @Override
    public void destroySingletons() {
        super.destroySingletons();
        updateManualSingletonNames(Set::clear, set -> !set.isEmpty());
        clearByTypeCache();
    }

    @Override
    public void destroySingleton(String beanName) {
        super.destroySingleton(beanName);
        removeManualSingletonName(beanName);
        clearByTypeCache();
    }

    private void removeManualSingletonName(String beanName) {
        updateManualSingletonNames(set -> set.remove(beanName), set -> set.contains(beanName));
    }

    private void updateManualSingletonNames(Consumer<Set<String>> action, Predicate<Set<String>> condition) {
        if (hasBeanCreationStarted()) {
            // Cannot modify startup-time collection elements anymore (for stable iteration)
            synchronized (this.beanDefinitionMap) {
                if (condition.test(this.manualSingletonNames)) {
                    Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
                    action.accept(updatedSingletons);
                    this.manualSingletonNames = updatedSingletons;
                }
            }
        } else {
            // Still in startup registration phase
            if (condition.test(this.manualSingletonNames)) {
                action.accept(this.manualSingletonNames);
            }
        }
    }

    private void clearByTypeCache() {
        this.allBeanNamesByType.clear();
        this.singletonBeanNamesByType.clear();
    }


    //---------------------------------------------------------------------
    // Dependency resolution functionality
    //---------------------------------------------------------------------

    @Override
    public <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException {
        Assert.notNull(requiredType, "Required type must not be null");
        NamedBeanHolder<T> namedBean = resolveNamedBean(ResolvableType.forRawClass(requiredType), null, false);
        if (namedBean != null) {
            return namedBean;
        }
        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof AutowireCapableBeanFactory acbf) {
            return acbf.resolveNamedBean(requiredType);
        }
        throw new NoSuchBeanDefinitionException(requiredType);
    }

    @SuppressWarnings("unchecked")
    private <T> NamedBeanHolder<T> resolveNamedBean(
            ResolvableType requiredType, Object[] args, boolean nonUniqueAsNull) throws BeansException {

        Assert.notNull(requiredType, "Required type must not be null");
        String[] candidateNames = getBeanNamesForType(requiredType);

        if (candidateNames.length > 1) {
            List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
            for (String beanName : candidateNames) {
                if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
                    autowireCandidates.add(beanName);
                }
            }
            if (!autowireCandidates.isEmpty()) {
                candidateNames = StringUtils.toStringArray(autowireCandidates);
            }
        }

        if (candidateNames.length == 1) {
            return resolveNamedBean(candidateNames[0], requiredType, args);
        } else if (candidateNames.length > 1) {
            Map<String, Object> candidates = CollectionUtils.newLinkedHashMap(candidateNames.length);
            for (String beanName : candidateNames) {
                if (containsSingleton(beanName) && args == null) {
                    Object beanInstance = getBean(beanName);
                    candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
                } else {
                    candidates.put(beanName, getType(beanName));
                }
            }
            String candidateName = determinePrimaryCandidate(candidates, requiredType.toClass());
            if (candidateName == null) {
                candidateName = determineHighestPriorityCandidate(candidates, requiredType.toClass());
            }
            if (candidateName == null) {
                candidateName = determineDefaultCandidate(candidates);
            }
            if (candidateName != null) {
                Object beanInstance = candidates.get(candidateName);
                if (beanInstance == null) {
                    return null;
                }
                if (beanInstance instanceof Class) {
                    return resolveNamedBean(candidateName, requiredType, args);
                }
                return new NamedBeanHolder<>(candidateName, (T) beanInstance);
            }
            if (!nonUniqueAsNull) {
                throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
            }
        }

        return null;
    }

    private <T> NamedBeanHolder<T> resolveNamedBean(
            String beanName, ResolvableType requiredType, Object[] args) throws BeansException {

        Object bean = getBean(beanName, null, args);
        if (bean instanceof NullBean) {
            return null;
        }
        return new NamedBeanHolder<>(beanName, adaptBeanInstance(beanName, bean, requiredType.toClass()));
    }

    @Override
    public Object resolveDependency(DependencyDescriptor descriptor, String requestingBeanName,
                                    Set<String> autowiredBeanNames, TypeConverter typeConverter) throws BeansException {

        descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
        if (Optional.class == descriptor.getDependencyType()) {
            return createOptionalDependency(descriptor, requestingBeanName);
        } else if (ObjectFactory.class == descriptor.getDependencyType() ||
                ObjectProvider.class == descriptor.getDependencyType()) {
            return new DependencyObjectProvider(descriptor, requestingBeanName);
        } else if (jakartaInjectProviderClass == descriptor.getDependencyType()) {
            return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
        } else if (descriptor.supportsLazyResolution()) {
            Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
                    descriptor, requestingBeanName);
            if (result != null) {
                return result;
            }
        }
        return doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
    }

    @SuppressWarnings("NullAway") // Dataflow analysis limitation
    public Object doResolveDependency(DependencyDescriptor descriptor, String beanName,
                                      Set<String> autowiredBeanNames, TypeConverter typeConverter) throws BeansException {

        InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
        try {
            // Step 1: pre-resolved shortcut for single bean match, for example, from @Autowired
            Object shortcut = descriptor.resolveShortcut(this);
            if (shortcut != null) {
                return shortcut;
            }

            Class<?> type = descriptor.getDependencyType();

            // Step 2: pre-defined value or expression, for example, from @Value
            Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
            if (value != null) {
                if (value instanceof String strValue) {
                    String resolvedValue = resolveEmbeddedValue(strValue);
                    BeanDefinition bd = (beanName != null && containsBean(beanName) ? getMergedBeanDefinition(beanName) : null);
                    value = evaluateBeanDefinitionString(resolvedValue, bd);
                }
                TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
                try {
                    return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
                } catch (UnsupportedOperationException ex) {
                    // A custom TypeConverter which does not support TypeDescriptor resolution...
                    return (descriptor.getField() != null ?
                            converter.convertIfNecessary(value, type, descriptor.getField()) :
                            converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
                }
            }

            // Step 3: shortcut for declared dependency name or qualifier-suggested name matching target bean name
            if (descriptor.usesStandardBeanLookup()) {
                String dependencyName = descriptor.getDependencyName();
                if (dependencyName == null || !containsBean(dependencyName)) {
                    String suggestedName = getAutowireCandidateResolver().getSuggestedName(descriptor);
                    dependencyName = (suggestedName != null && containsBean(suggestedName) ? suggestedName : null);
                }
                if (dependencyName != null) {
                    dependencyName = canonicalName(dependencyName);  // dependency name can be alias of target name
                    if (isTypeMatch(dependencyName, type) && isAutowireCandidate(dependencyName, descriptor) &&
                            !isFallback(dependencyName) && !hasPrimaryConflict(dependencyName, type) &&
                            !isSelfReference(beanName, dependencyName)) {
                        if (autowiredBeanNames != null) {
                            autowiredBeanNames.add(dependencyName);
                        }
                        Object dependencyBean = getBean(dependencyName);
                        return resolveInstance(dependencyBean, descriptor, type, dependencyName);
                    }
                }
            }

            // Step 4a: multiple beans as stream / array / standard collection / plain map
            Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
            if (multipleBeans != null) {
                return multipleBeans;
            }
            // Step 4b: direct bean matches, possibly direct beans of type Collection / Map
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
            if (matchingBeans.isEmpty()) {
                // Step 4c (fallback): custom Collection / Map declarations for collecting multiple beans
                multipleBeans = resolveMultipleBeansFallback(descriptor, beanName, autowiredBeanNames, typeConverter);
                if (multipleBeans != null) {
                    return multipleBeans;
                }
                // Raise exception if nothing found for required injection point
                if (isRequired(descriptor)) {
                    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
                }
                return null;
            }

            String autowiredBeanName;
            Object instanceCandidate;

            // Step 5: determine single candidate
            if (matchingBeans.size() > 1) {
                autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
                if (autowiredBeanName == null) {
                    if (isRequired(descriptor) || !indicatesArrayCollectionOrMap(type)) {
                        // Raise exception if no clear match found for required injection point
                        return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
                    } else {
                        // In case of an optional Collection/Map, silently ignore a non-unique case:
                        // possibly it was meant to be an empty collection of multiple regular beans
                        // (before 4.3 in particular when we didn't even look for collection beans).
                        return null;
                    }
                }
                instanceCandidate = matchingBeans.get(autowiredBeanName);
            } else {
                // We have exactly one match.
                Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
                autowiredBeanName = entry.getKey();
                instanceCandidate = entry.getValue();
            }

            // Step 6: validate single result
            if (autowiredBeanNames != null) {
                autowiredBeanNames.add(autowiredBeanName);
            }
            if (instanceCandidate instanceof Class) {
                instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
            }
            return resolveInstance(instanceCandidate, descriptor, type, autowiredBeanName);
        } finally {
            ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
        }
    }

    private Object resolveInstance(Object candidate, DependencyDescriptor descriptor, Class<?> type, String name) {
        Object result = candidate;
        if (result instanceof NullBean) {
            // Raise exception if null encountered for required injection point
            if (isRequired(descriptor)) {
                raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
            }
            result = null;
        }
        if (!ClassUtils.isAssignableValue(type, result)) {
            throw new BeanNotOfRequiredTypeException(name, type, candidate.getClass());
        }
        return result;
    }

    private Object resolveMultipleBeans(DependencyDescriptor descriptor, String beanName,
                                        Set<String> autowiredBeanNames, TypeConverter typeConverter) {

        Class<?> type = descriptor.getDependencyType();

        if (descriptor instanceof StreamDependencyDescriptor streamDependencyDescriptor) {
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            Stream<Object> stream = matchingBeans.keySet().stream()
                    .map(name -> descriptor.resolveCandidate(name, type, this))
                    .filter(bean -> !(bean instanceof NullBean));
            if (streamDependencyDescriptor.isOrdered()) {
                stream = stream.sorted(adaptOrderComparator(matchingBeans));
            }
            return stream;
        } else if (type.isArray()) {
            Class<?> componentType = type.componentType();
            ResolvableType resolvableType = descriptor.getResolvableType();
            Class<?> resolvedArrayType = resolvableType.resolve(type);
            if (resolvedArrayType != type) {
                componentType = resolvableType.getComponentType().resolve();
            }
            if (componentType == null) {
                return null;
            }
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType,
                    new MultiElementDescriptor(descriptor));
            if (matchingBeans.isEmpty()) {
                return null;
            }
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
            Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
            if (result instanceof Object[] array && array.length > 1) {
                Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
                if (comparator != null) {
                    Arrays.sort(array, comparator);
                }
            }
            return result;
        } else if (Collection.class == type || Set.class == type || List.class == type) {
            return resolveMultipleBeanCollection(descriptor, beanName, autowiredBeanNames, typeConverter);
        } else if (Map.class == type) {
            return resolveMultipleBeanMap(descriptor, beanName, autowiredBeanNames, typeConverter);
        }
        return null;
    }


    private Object resolveMultipleBeansFallback(DependencyDescriptor descriptor, String beanName,
                                                Set<String> autowiredBeanNames, TypeConverter typeConverter) {

        Class<?> type = descriptor.getDependencyType();

        if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
            return resolveMultipleBeanCollection(descriptor, beanName, autowiredBeanNames, typeConverter);
        } else if (Map.class.isAssignableFrom(type) && type.isInterface()) {
            return resolveMultipleBeanMap(descriptor, beanName, autowiredBeanNames, typeConverter);
        }
        return null;
    }

    private Object resolveMultipleBeanCollection(DependencyDescriptor descriptor, String beanName,
                                                 Set<String> autowiredBeanNames, TypeConverter typeConverter) {

        Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric();
        if (elementType == null) {
            return null;
        }
        Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType,
                new MultiElementDescriptor(descriptor));
        if (matchingBeans.isEmpty()) {
            return null;
        }
        if (autowiredBeanNames != null) {
            autowiredBeanNames.addAll(matchingBeans.keySet());
        }
        TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
        Object result = converter.convertIfNecessary(matchingBeans.values(), descriptor.getDependencyType());
        if (result instanceof List<?> list && list.size() > 1) {
            Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
            if (comparator != null) {
                list.sort(comparator);
            }
        }
        return result;
    }

    private Object resolveMultipleBeanMap(DependencyDescriptor descriptor, String beanName,
                                          Set<String> autowiredBeanNames, TypeConverter typeConverter) {

        ResolvableType mapType = descriptor.getResolvableType().asMap();
        Class<?> keyType = mapType.resolveGeneric(0);
        if (String.class != keyType) {
            return null;
        }
        Class<?> valueType = mapType.resolveGeneric(1);
        if (valueType == null) {
            return null;
        }
        Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType,
                new MultiElementDescriptor(descriptor));
        if (matchingBeans.isEmpty()) {
            return null;
        }
        if (autowiredBeanNames != null) {
            autowiredBeanNames.addAll(matchingBeans.keySet());
        }
        TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
        return converter.convertIfNecessary(matchingBeans, descriptor.getDependencyType());
    }

    private boolean indicatesArrayCollectionOrMap(Class<?> type) {
        return (type.isArray() || (type.isInterface() &&
                (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))));
    }

    private boolean isRequired(DependencyDescriptor descriptor) {
        return getAutowireCandidateResolver().isRequired(descriptor);
    }

    private Comparator<Object> adaptDependencyComparator(Map<String, ?> matchingBeans) {
        Comparator<Object> comparator = getDependencyComparator();
        if (comparator instanceof OrderComparator orderComparator) {
            return orderComparator.withSourceProvider(
                    createFactoryAwareOrderSourceProvider(matchingBeans));
        } else {
            return comparator;
        }
    }

    private Comparator<Object> adaptOrderComparator(Map<String, ?> matchingBeans) {
        Comparator<Object> dependencyComparator = getDependencyComparator();
        OrderComparator comparator = (dependencyComparator instanceof OrderComparator orderComparator ?
                orderComparator : OrderComparator.INSTANCE);
        return comparator.withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
    }

    private OrderComparator.OrderSourceProvider createFactoryAwareOrderSourceProvider(Map<String, ?> beans) {
        IdentityHashMap<Object, String> instancesToBeanNames = new IdentityHashMap<>();
        beans.forEach((beanName, instance) -> instancesToBeanNames.put(instance, beanName));
        return new FactoryAwareOrderSourceProvider(instancesToBeanNames);
    }

    protected Map<String, Object> findAutowireCandidates(
            String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {

        String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                this, requiredType, true, descriptor.isEager());
        Map<String, Object> result = CollectionUtils.newLinkedHashMap(candidateNames.length);
        for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
            Class<?> autowiringType = classObjectEntry.getKey();
            if (autowiringType.isAssignableFrom(requiredType)) {
                Object autowiringValue = classObjectEntry.getValue();
                autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
                if (requiredType.isInstance(autowiringValue)) {
                    result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
                    break;
                }
            }
        }
        for (String candidate : candidateNames) {
            if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
                addCandidateEntry(result, candidate, descriptor, requiredType);
            }
        }
        if (result.isEmpty()) {
            boolean multiple = indicatesArrayCollectionOrMap(requiredType);
            // Consider fallback matches if the first pass failed to find anything...
            DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();
            for (String candidate : candidateNames) {
                if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) &&
                        (!multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
                    addCandidateEntry(result, candidate, descriptor, requiredType);
                }
            }
            if (result.isEmpty() && !multiple) {
                // Consider self references as a final pass...
                // but in the case of a dependency collection, not the very same bean itself.
                for (String candidate : candidateNames) {
                    if (isSelfReference(beanName, candidate) &&
                            (!(descriptor instanceof MultiElementDescriptor) || !beanName.equals(candidate)) &&
                            isAutowireCandidate(candidate, fallbackDescriptor)) {
                        addCandidateEntry(result, candidate, descriptor, requiredType);
                    }
                }
            }
        }
        return result;
    }

    private void addCandidateEntry(Map<String, Object> candidates, String candidateName,
                                   DependencyDescriptor descriptor, Class<?> requiredType) {

        if (descriptor instanceof MultiElementDescriptor) {
            Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
            if (!(beanInstance instanceof NullBean)) {
                candidates.put(candidateName, beanInstance);
            }
        } else if (containsSingleton(candidateName) ||
                (descriptor instanceof StreamDependencyDescriptor streamDescriptor && streamDescriptor.isOrdered())) {
            Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
            candidates.put(candidateName, beanInstance);
        } else {
            candidates.put(candidateName, getType(candidateName));
        }
    }

    protected String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
        Class<?> requiredType = descriptor.getDependencyType();
        // Step 1: check primary candidate
        String primaryCandidate = determinePrimaryCandidate(candidates, requiredType);
        if (primaryCandidate != null) {
            return primaryCandidate;
        }
        // Step 2a: match bean name against declared dependency name
        String dependencyName = descriptor.getDependencyName();
        if (dependencyName != null) {
            for (String beanName : candidates.keySet()) {
                if (matchesBeanName(beanName, dependencyName)) {
                    return beanName;
                }
            }
        }
        // Step 2b: match bean name against qualifier-suggested name
        String suggestedName = getAutowireCandidateResolver().getSuggestedName(descriptor);
        if (suggestedName != null) {
            for (String beanName : candidates.keySet()) {
                if (matchesBeanName(beanName, suggestedName)) {
                    return beanName;
                }
            }
        }
        // Step 3: check highest priority candidate
        String priorityCandidate = determineHighestPriorityCandidate(candidates, requiredType);
        if (priorityCandidate != null) {
            return priorityCandidate;
        }
        // Step 4: pick unique default-candidate
        String defaultCandidate = determineDefaultCandidate(candidates);
        if (defaultCandidate != null) {
            return defaultCandidate;
        }
        // Step 5: pick directly registered dependency
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateName = entry.getKey();
            Object beanInstance = entry.getValue();
            if (beanInstance != null && this.resolvableDependencies.containsValue(beanInstance)) {
                return candidateName;
            }
        }
        return null;
    }

    protected String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
        String primaryBeanName = null;
        // First pass: identify unique primary candidate
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateBeanName = entry.getKey();
            Object beanInstance = entry.getValue();
            if (isPrimary(candidateBeanName, beanInstance)) {
                if (primaryBeanName != null) {
                    boolean candidateLocal = containsBeanDefinition(candidateBeanName);
                    boolean primaryLocal = containsBeanDefinition(primaryBeanName);
                    if (candidateLocal == primaryLocal) {
                        throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
                                "more than one 'primary' bean found among candidates: " + candidates.keySet());
                    } else if (candidateLocal) {
                        primaryBeanName = candidateBeanName;
                    }
                } else {
                    primaryBeanName = candidateBeanName;
                }
            }
        }
        // Second pass: identify unique non-fallback candidate
        if (primaryBeanName == null) {
            for (String candidateBeanName : candidates.keySet()) {
                if (!isFallback(candidateBeanName)) {
                    if (primaryBeanName != null) {
                        return null;
                    }
                    primaryBeanName = candidateBeanName;
                }
            }
        }
        return primaryBeanName;
    }

    protected String determineHighestPriorityCandidate(Map<String, Object> candidates, Class<?> requiredType) {
        String highestPriorityBeanName = null;
        Integer highestPriority = null;
        boolean highestPriorityConflictDetected = false;
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateBeanName = entry.getKey();
            Object beanInstance = entry.getValue();
            if (beanInstance != null) {
                Integer candidatePriority = getPriority(beanInstance);
                if (candidatePriority != null) {
                    if (highestPriority != null) {
                        if (candidatePriority.equals(highestPriority)) {
                            highestPriorityConflictDetected = true;
                        } else if (candidatePriority < highestPriority) {
                            highestPriorityBeanName = candidateBeanName;
                            highestPriority = candidatePriority;
                            highestPriorityConflictDetected = false;
                        }
                    } else {
                        highestPriorityBeanName = candidateBeanName;
                        highestPriority = candidatePriority;
                    }
                }
            }
        }

        if (highestPriorityConflictDetected) {
            throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
                    "Multiple beans found with the same highest priority (" + highestPriority +
                            ") among candidates: " + candidates.keySet());

        }
        return highestPriorityBeanName;
    }

    protected boolean isPrimary(String beanName, Object beanInstance) {
        String transformedBeanName = transformedBeanName(beanName);
        if (containsBeanDefinition(transformedBeanName)) {
            return getMergedLocalBeanDefinition(transformedBeanName).isPrimary();
        }
        return (getParentBeanFactory() instanceof DefaultListableBeanFactory parent &&
                parent.isPrimary(transformedBeanName, beanInstance));
    }

    private boolean isFallback(String beanName) {
        String transformedBeanName = transformedBeanName(beanName);
        if (containsBeanDefinition(transformedBeanName)) {
            return getMergedLocalBeanDefinition(transformedBeanName).isFallback();
        }
        return (getParentBeanFactory() instanceof DefaultListableBeanFactory parent &&
                parent.isFallback(transformedBeanName));
    }

    protected Integer getPriority(Object beanInstance) {
        Comparator<Object> comparator = getDependencyComparator();
        if (comparator instanceof OrderComparator orderComparator) {
            return orderComparator.getPriority(beanInstance);
        }
        return null;
    }


    private String determineDefaultCandidate(Map<String, Object> candidates) {
        String defaultBeanName = null;
        for (String candidateBeanName : candidates.keySet()) {
            if (AutowireUtils.isDefaultCandidate(this, candidateBeanName)) {
                if (defaultBeanName != null) {
                    return null;
                }
                defaultBeanName = candidateBeanName;
            }
        }
        return defaultBeanName;
    }

    protected boolean matchesBeanName(String beanName, String candidateName) {
        return (candidateName != null &&
                (candidateName.equals(beanName) || ObjectUtils.containsElement(getAliases(beanName), candidateName)));
    }

    @Contract("null, _ -> false; _, null -> false;")
    private boolean isSelfReference(String beanName, String candidateName) {
        return (beanName != null && candidateName != null &&
                (beanName.equals(candidateName) || (containsBeanDefinition(candidateName) &&
                        beanName.equals(getMergedLocalBeanDefinition(candidateName).getFactoryBeanName()))));
    }

    private boolean hasPrimaryConflict(String beanName, Class<?> dependencyType) {
        for (String candidate : this.primaryBeanNames) {
            if (isTypeMatch(candidate, dependencyType) && !candidate.equals(beanName)) {
                return true;
            }
        }
        return (getParentBeanFactory() instanceof DefaultListableBeanFactory parent &&
                parent.hasPrimaryConflict(beanName, dependencyType));
    }

    private void raiseNoMatchingBeanFound(
            Class<?> type, ResolvableType resolvableType, DependencyDescriptor descriptor) throws BeansException {

        checkBeanNotOfRequiredType(type, descriptor);

        throw new NoSuchBeanDefinitionException(resolvableType,
                "expected at least 1 bean which qualifies as autowire candidate. " +
                        "Dependency annotations: " + ObjectUtils.nullSafeToString(descriptor.getAnnotations()));
    }

    private void checkBeanNotOfRequiredType(Class<?> type, DependencyDescriptor descriptor) {
        for (String beanName : this.beanDefinitionNames) {
            try {
                RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                Class<?> targetType = mbd.getTargetType();
                if (targetType != null && type.isAssignableFrom(targetType) &&
                        isAutowireCandidate(beanName, mbd, descriptor, getAutowireCandidateResolver())) {
                    // Probably a proxy interfering with target type match -> throw meaningful exception.
                    Object beanInstance = getSingleton(beanName, false);
                    Class<?> beanType = (beanInstance != null && beanInstance.getClass() != NullBean.class ?
                            beanInstance.getClass() : predictBeanType(beanName, mbd));
                    if (beanType != null && !type.isAssignableFrom(beanType)) {
                        throw new BeanNotOfRequiredTypeException(beanName, type, beanType);
                    }
                }
            } catch (NoSuchBeanDefinitionException ex) {
                // Bean definition got removed while we were iterating -> ignore.
            }
        }

        if (getParentBeanFactory() instanceof DefaultListableBeanFactory parent) {
            parent.checkBeanNotOfRequiredType(type, descriptor);
        }
    }

    private Optional<?> createOptionalDependency(
            DependencyDescriptor descriptor, String beanName, final Object... args) {

        DependencyDescriptor descriptorToUse = new NestedDependencyDescriptor(descriptor) {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
                return (!ObjectUtils.isEmpty(args) ? beanFactory.getBean(beanName, args) :
                        super.resolveCandidate(beanName, requiredType, beanFactory));
            }

            @Override
            public boolean usesStandardBeanLookup() {
                return ObjectUtils.isEmpty(args);
            }
        };
        Object result = doResolveDependency(descriptorToUse, beanName, null, null);
        return (result instanceof Optional<?> optional ? optional : Optional.ofNullable(result));
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(ObjectUtils.identityToString(this));
        sb.append(": defining beans [");
        sb.append(StringUtils.collectionToCommaDelimitedString(this.beanDefinitionNames));
        sb.append("]; ");
        BeanFactory parent = getParentBeanFactory();
        if (parent == null) {
            sb.append("root of factory hierarchy");
        } else {
            sb.append("parent: ").append(ObjectUtils.identityToString(parent));
        }
        return sb.toString();
    }


    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    @Serial
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        throw new NotSerializableException("DefaultListableBeanFactory itself is not deserializable - " +
                "just a SerializedBeanFactoryReference is");
    }

    @Serial
    protected Object writeReplace() throws ObjectStreamException {
        if (this.serializationId != null) {
            return new SerializedBeanFactoryReference(this.serializationId);
        } else {
            throw new NotSerializableException("DefaultListableBeanFactory has no serialization id");
        }
    }


    private static class SerializedBeanFactoryReference implements Serializable {

        private final String id;

        public SerializedBeanFactoryReference(String id) {
            this.id = id;
        }

        private Object readResolve() {
            Reference<?> ref = serializableFactories.get(this.id);
            if (ref != null) {
                Object result = ref.get();
                if (result != null) {
                    return result;
                }
            }
            // Lenient fallback: dummy factory in case of original factory not found...
            DefaultListableBeanFactory dummyFactory = new DefaultListableBeanFactory();
            dummyFactory.serializationId = this.id;
            return dummyFactory;
        }
    }


    private static class NestedDependencyDescriptor extends DependencyDescriptor {

        public NestedDependencyDescriptor(DependencyDescriptor original) {
            super(original);
            increaseNestingLevel();
        }

        @Override
        public boolean usesStandardBeanLookup() {
            return true;
        }
    }


    private static class MultiElementDescriptor extends NestedDependencyDescriptor {

        public MultiElementDescriptor(DependencyDescriptor original) {
            super(original);
        }
    }


    private static class StreamDependencyDescriptor extends DependencyDescriptor {

        private final boolean ordered;

        public StreamDependencyDescriptor(DependencyDescriptor original, boolean ordered) {
            super(original);
            this.ordered = ordered;
        }

        public boolean isOrdered() {
            return this.ordered;
        }
    }


    private interface BeanObjectProvider<T> extends ObjectProvider<T>, Serializable {
    }


    private class DependencyObjectProvider implements BeanObjectProvider<Object> {

        private final DependencyDescriptor descriptor;

        private final boolean optional;

        private final String beanName;

        public DependencyObjectProvider(DependencyDescriptor descriptor, String beanName) {
            this.descriptor = new NestedDependencyDescriptor(descriptor);
            this.optional = (this.descriptor.getDependencyType() == Optional.class);
            this.beanName = beanName;
        }

        @Override
        public Object getObject() throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName);
            } else {
                Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
                if (result == null) {
                    throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
                }
                return result;
            }
        }

        @Override
        public Object getObject(final Object... args) throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName, args);
            } else {
                DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                    @Override
                    public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
                        return beanFactory.getBean(beanName, args);
                    }
                };
                Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
                if (result == null) {
                    throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
                }
                return result;
            }
        }

        @Override
        public Object getIfAvailable() throws BeansException {
            try {
                if (this.optional) {
                    return createOptionalDependency(this.descriptor, this.beanName);
                } else {
                    DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                        @Override
                        public boolean isRequired() {
                            return false;
                        }

                        @Override
                        public boolean usesStandardBeanLookup() {
                            return true;
                        }
                    };
                    return doResolveDependency(descriptorToUse, this.beanName, null, null);
                }
            } catch (ScopeNotActiveException ex) {
                // Ignore resolved bean in non-active scope
                return null;
            }
        }

        @Override
        public void ifAvailable(Consumer<Object> dependencyConsumer) throws BeansException {
            Object dependency = getIfAvailable();
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                }
            }
        }

        @Override
        public Object getIfUnique() throws BeansException {
            DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                @Override
                public boolean isRequired() {
                    return false;
                }

                @Override
                public boolean usesStandardBeanLookup() {
                    return true;
                }

                @Override
                public Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) {
                    return null;
                }
            };
            try {
                if (this.optional) {
                    return createOptionalDependency(descriptorToUse, this.beanName);
                } else {
                    return doResolveDependency(descriptorToUse, this.beanName, null, null);
                }
            } catch (ScopeNotActiveException ex) {
                // Ignore resolved bean in non-active scope
                return null;
            }
        }

        @Override
        public void ifUnique(Consumer<Object> dependencyConsumer) throws BeansException {
            Object dependency = getIfUnique();
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                }
            }
        }

        protected Object getValue() throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName);
            } else {
                return doResolveDependency(this.descriptor, this.beanName, null, null);
            }
        }

        @Override
        public Stream<Object> stream() {
            return resolveStream(false);
        }

        @Override
        public Stream<Object> orderedStream() {
            return resolveStream(true);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Stream<Object> resolveStream(boolean ordered) {
            DependencyDescriptor descriptorToUse = new StreamDependencyDescriptor(this.descriptor, ordered);
            Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
            return (result instanceof Stream stream ? stream : Stream.of(result));
        }

        @Override
        public Stream<Object> stream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
            return Arrays.stream(beanNamesForStream(this.descriptor.getResolvableType(), includeNonSingletons, true))
                    .filter(name -> AutowireUtils.isAutowireCandidate(DefaultListableBeanFactory.this, name))
                    .filter(name -> customFilter.test(getType(name)))
                    .map(name -> getBean(name))
                    .filter(bean -> !(bean instanceof NullBean));
        }

        @Override
        public Stream<Object> orderedStream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
            String[] beanNames = beanNamesForStream(this.descriptor.getResolvableType(), includeNonSingletons, true);
            if (beanNames.length == 0) {
                return Stream.empty();
            }
            Map<String, Object> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);
            for (String beanName : beanNames) {
                if (AutowireUtils.isAutowireCandidate(DefaultListableBeanFactory.this, beanName) &&
                        customFilter.test(getType(beanName))) {
                    Object beanInstance = getBean(beanName);
                    if (!(beanInstance instanceof NullBean)) {
                        matchingBeans.put(beanName, beanInstance);
                    }
                }
            }
            return matchingBeans.values().stream().sorted(adaptOrderComparator(matchingBeans));
        }
    }


    private class Jsr330Factory implements Serializable {

        public Object createDependencyProvider(DependencyDescriptor descriptor, String beanName) {
            return new Jsr330Provider(descriptor, beanName);
        }

        private class Jsr330Provider extends DependencyObjectProvider implements Provider<Object> {

            public Jsr330Provider(DependencyDescriptor descriptor, String beanName) {
                super(descriptor, beanName);
            }

            @Override
            public Object get() throws BeansException {
                return getValue();
            }
        }
    }


    private class FactoryAwareOrderSourceProvider implements OrderComparator.OrderSourceProvider {

        private final Map<Object, String> instancesToBeanNames;

        public FactoryAwareOrderSourceProvider(Map<Object, String> instancesToBeanNames) {
            this.instancesToBeanNames = instancesToBeanNames;
        }

        @Override
        public Object getOrderSource(Object obj) {
            String beanName = this.instancesToBeanNames.get(obj);
            if (beanName == null) {
                return null;
            }
            try {
                RootBeanDefinition beanDefinition = (RootBeanDefinition) getMergedBeanDefinition(beanName);
                List<Object> sources = new ArrayList<>(3);
                Object orderAttribute = beanDefinition.getAttribute(AbstractBeanDefinition.ORDER_ATTRIBUTE);
                if (orderAttribute != null) {
                    if (orderAttribute instanceof Integer order) {
                        sources.add((Ordered) () -> order);
                    } else {
                        throw new IllegalStateException("Invalid value type for attribute '" +
                                AbstractBeanDefinition.ORDER_ATTRIBUTE + "': " + orderAttribute.getClass().getName());
                    }
                }
                Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
                if (factoryMethod != null) {
                    sources.add(factoryMethod);
                }
                Class<?> targetType = beanDefinition.getTargetType();
                if (targetType != null && targetType != obj.getClass()) {
                    sources.add(targetType);
                }
                return sources.toArray();
            } catch (NoSuchBeanDefinitionException ex) {
                return null;
            }
        }
    }


    private enum PreInstantiation {

        MAIN, BACKGROUND
    }

}
