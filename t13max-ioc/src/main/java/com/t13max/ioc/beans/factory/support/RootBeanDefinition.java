package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.MutablePropertyValues;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinitionHolder;
import com.t13max.ioc.beans.factory.config.ConstructorArgumentValues;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.utils.Assert;

import java.lang.reflect.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 22:47 2026/1/15
 */
public class RootBeanDefinition extends AbstractBeanDefinition{

    private BeanDefinitionHolder decoratedDefinition;

    private AnnotatedElement qualifiedElement;
    //是否需要重新合并定义
    volatile boolean stale;

    boolean allowCaching = true;

    boolean isFactoryMethodUnique;

    volatile ResolvableType targetType;    volatile Class<?> resolvedTargetType;    volatile Boolean isFactoryBean;    volatile ResolvableType factoryMethodReturnType;    volatile Method factoryMethodToIntrospect;    volatile String resolvedDestroyMethodName;    final Object constructorArgumentLock = new Object();

    Executable resolvedConstructorOrFactoryMethod;    boolean constructorArgumentsResolved = false;    Object [] resolvedConstructorArguments;    Object [] preparedConstructorArguments;    final Object postProcessingLock = new Object();    boolean postProcessed = false;    volatile Boolean beforeInstantiationResolved;

    private Set<Member> externallyManagedConfigMembers;

    private Set<String> externallyManagedInitMethods;

    private Set<String> externallyManagedDestroyMethods;
    public RootBeanDefinition() {
    }    public RootBeanDefinition(Class<?> beanClass) {
        setBeanClass(beanClass);
    }    @Deprecated(since = "6.0.11")
    public RootBeanDefinition(ResolvableType beanType) {
        setTargetType(beanType);
    }    public <T> RootBeanDefinition(Class<T> beanClass, Supplier<T> instanceSupplier) {
        setBeanClass(beanClass);
        setInstanceSupplier(instanceSupplier);
    }    public <T> RootBeanDefinition(Class<T> beanClass, String scope, Supplier<T> instanceSupplier) {
        setBeanClass(beanClass);
        setScope(scope);
        setInstanceSupplier(instanceSupplier);
    }    public RootBeanDefinition(Class<?> beanClass, int autowireMode, boolean dependencyCheck) {
        setBeanClass(beanClass);
        setAutowireMode(autowireMode);
        if (dependencyCheck && getResolvedAutowireMode() != AUTOWIRE_CONSTRUCTOR) {
            setDependencyCheck(DEPENDENCY_CHECK_OBJECTS);
        }
    }    public RootBeanDefinition(Class<?> beanClass, ConstructorArgumentValues cargs, MutablePropertyValues pvs) {
        super(cargs, pvs);
        setBeanClass(beanClass);
    }    public RootBeanDefinition(String beanClassName) {
        setBeanClassName(beanClassName);
    }    public RootBeanDefinition(String beanClassName, ConstructorArgumentValues cargs, MutablePropertyValues pvs) {
        super(cargs, pvs);
        setBeanClassName(beanClassName);
    }    public RootBeanDefinition(RootBeanDefinition original) {
        super(original);
        this.decoratedDefinition = original.decoratedDefinition;
        this.qualifiedElement = original.qualifiedElement;
        this.allowCaching = original.allowCaching;
        this.isFactoryMethodUnique = original.isFactoryMethodUnique;
        this.targetType = original.targetType;
        this.factoryMethodToIntrospect = original.factoryMethodToIntrospect;
    }    RootBeanDefinition(BeanDefinition original) {
        super(original);
    }


    @Override
    public String getParentName() {
        return null;
    }

    @Override
    public void setParentName(String parentName) {
        if (parentName != null) {
            throw new IllegalArgumentException("Root bean cannot be changed into a child bean with parent reference");
        }
    }    public void setDecoratedDefinition(BeanDefinitionHolder decoratedDefinition) {
        this.decoratedDefinition = decoratedDefinition;
    }    public BeanDefinitionHolder getDecoratedDefinition() {
        return this.decoratedDefinition;
    }    public void setQualifiedElement(AnnotatedElement qualifiedElement) {
        this.qualifiedElement = qualifiedElement;
    }    public AnnotatedElement getQualifiedElement() {
        return this.qualifiedElement;
    }    public void setTargetType(ResolvableType targetType) {
        this.targetType = targetType;
    }    public void setTargetType(Class<?> targetType) {
        this.targetType = (targetType != null ? ResolvableType.forClass(targetType) : null);
    }    public Class<?> getTargetType() {
        if (this.resolvedTargetType != null) {
            return this.resolvedTargetType;
        }
        ResolvableType targetType = this.targetType;
        return (targetType != null ? targetType.resolve() : null);
    }    @Override
    public ResolvableType getResolvableType() {
        ResolvableType targetType = this.targetType;
        if (targetType != null) {
            return targetType;
        }
        ResolvableType returnType = this.factoryMethodReturnType;
        if (returnType != null) {
            return returnType;
        }
        Method factoryMethod = getResolvedFactoryMethod();
        if (factoryMethod != null) {
            return ResolvableType.forMethodReturnType(factoryMethod);
        }
        return super.getResolvableType();
    }    public Constructor<?> [] getPreferredConstructors() {
        Object attribute = getAttribute(PREFERRED_CONSTRUCTORS_ATTRIBUTE);
        if (attribute == null) {
            return null;
        }
        if (attribute instanceof Constructor<?> constructor) {
            return new Constructor<?>[] {constructor};
        }
        if (attribute instanceof Constructor<?>[] constructors) {
            return constructors;
        }
        throw new IllegalArgumentException("Invalid value type for attribute '" +
                PREFERRED_CONSTRUCTORS_ATTRIBUTE + "': " + attribute.getClass().getName());
    }    public void setUniqueFactoryMethodName(String name) {
        Assert.hasText(name, "Factory method name must not be empty");
        setFactoryMethodName(name);
        this.isFactoryMethodUnique = true;
    }    public void setNonUniqueFactoryMethodName(String name) {
        Assert.hasText(name, "Factory method name must not be empty");
        setFactoryMethodName(name);
        this.isFactoryMethodUnique = false;
    }    public boolean isFactoryMethod(Method candidate) {
        return candidate.getName().equals(getFactoryMethodName());
    }    public void setResolvedFactoryMethod(Method method) {
        this.factoryMethodToIntrospect = method;
        if (method != null) {
            setUniqueFactoryMethodName(method.getName());
        }
    }    public Method getResolvedFactoryMethod() {
        Method factoryMethod = this.factoryMethodToIntrospect;
        if (factoryMethod == null && getInstanceSupplier() instanceof InstanceSupplier<?> instanceSupplier) {
            factoryMethod = instanceSupplier.getFactoryMethod();
        }
        return factoryMethod;
    }    public void markAsPostProcessed() {
        synchronized (this.postProcessingLock) {
            this.postProcessed = true;
        }
    }    public void registerExternallyManagedConfigMember(Member configMember) {
        synchronized (this.postProcessingLock) {
            if (this.externallyManagedConfigMembers == null) {
                this.externallyManagedConfigMembers = new LinkedHashSet<>(1);
            }
            this.externallyManagedConfigMembers.add(configMember);
        }
    }    public boolean isExternallyManagedConfigMember(Member configMember) {
        synchronized (this.postProcessingLock) {
            return (this.externallyManagedConfigMembers != null &&
                    this.externallyManagedConfigMembers.contains(configMember));
        }
    }    public Set<Member> getExternallyManagedConfigMembers() {
        synchronized (this.postProcessingLock) {
            return (this.externallyManagedConfigMembers != null ?
                    Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedConfigMembers)) :
                    Collections.emptySet());
        }
    }    public void registerExternallyManagedInitMethod(String initMethod) {
        synchronized (this.postProcessingLock) {
            if (this.externallyManagedInitMethods == null) {
                this.externallyManagedInitMethods = new LinkedHashSet<>(1);
            }
            this.externallyManagedInitMethods.add(initMethod);
        }
    }    public boolean isExternallyManagedInitMethod(String initMethod) {
        synchronized (this.postProcessingLock) {
            return (this.externallyManagedInitMethods != null &&
                    this.externallyManagedInitMethods.contains(initMethod));
        }
    }    boolean hasAnyExternallyManagedInitMethod(String initMethod) {
        synchronized (this.postProcessingLock) {
            if (isExternallyManagedInitMethod(initMethod)) {
                return true;
            }
            return hasAnyExternallyManagedMethod(this.externallyManagedInitMethods, initMethod);
        }
    }    public Set<String> getExternallyManagedInitMethods() {
        synchronized (this.postProcessingLock) {
            return (this.externallyManagedInitMethods != null ?
                    Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedInitMethods)) :
                    Collections.emptySet());
        }
    }    public void resolveDestroyMethodIfNecessary() {
        setDestroyMethodNames(DisposableBeanAdapter.inferDestroyMethodsIfNecessary(getResolvableType().toClass(), this));
    }    public void registerExternallyManagedDestroyMethod(String destroyMethod) {
        synchronized (this.postProcessingLock) {
            if (this.externallyManagedDestroyMethods == null) {
                this.externallyManagedDestroyMethods = new LinkedHashSet<>(1);
            }
            this.externallyManagedDestroyMethods.add(destroyMethod);
        }
    }    public boolean isExternallyManagedDestroyMethod(String destroyMethod) {
        synchronized (this.postProcessingLock) {
            return (this.externallyManagedDestroyMethods != null &&
                    this.externallyManagedDestroyMethods.contains(destroyMethod));
        }
    }    boolean hasAnyExternallyManagedDestroyMethod(String destroyMethod) {
        synchronized (this.postProcessingLock) {
            if (isExternallyManagedDestroyMethod(destroyMethod)) {
                return true;
            }
            return hasAnyExternallyManagedMethod(this.externallyManagedDestroyMethods, destroyMethod);
        }
    }

    private static boolean hasAnyExternallyManagedMethod(Set<String> candidates, String methodName) {
        if (candidates != null) {
            for (String candidate : candidates) {
                int indexOfDot = candidate.lastIndexOf('.');
                if (indexOfDot > 0) {
                    String candidateMethodName = candidate.substring(indexOfDot + 1);
                    if (candidateMethodName.equals(methodName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }    public Set<String> getExternallyManagedDestroyMethods() {
        synchronized (this.postProcessingLock) {
            return (this.externallyManagedDestroyMethods != null ?
                    Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedDestroyMethods)) :
                    Collections.emptySet());
        }
    }


    @Override
    public RootBeanDefinition cloneBeanDefinition() {
        return new RootBeanDefinition(this);
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof RootBeanDefinition && super.equals(other)));
    }

    @Override
    public String toString() {
        return "Root bean: " + super.toString();
    }
}
