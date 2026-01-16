package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.PropertyEditorRegistrar;
import com.t13max.ioc.beans.PropertyEditorRegistry;
import com.t13max.ioc.beans.factory.BeanDefinitionStoreException;
import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.HierarchicalBeanFactory;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;
import com.t13max.ioc.core.metrics.ApplicationStartup;
import com.t13max.ioc.utils.StringValueResolver;

import java.beans.PropertyEditor;
import java.util.concurrent.Executor;

/**
 * @Author: t13max
 * @Since: 22:17 2026/1/15
 */
public interface ConfigurableBeanFactory extends HierarchicalBeanFactory, SingletonBeanRegistry {

    String SCOPE_SINGLETON = "singleton";

    String SCOPE_PROTOTYPE = "prototype";

    void setParentBeanFactory(BeanFactory parentBeanFactory) throws IllegalStateException;

    void setBeanClassLoader(ClassLoader beanClassLoader);

    ClassLoader getBeanClassLoader();

    void setTempClassLoader(ClassLoader tempClassLoader);

    ClassLoader getTempClassLoader();

    void setCacheBeanMetadata(boolean cacheBeanMetadata);

    boolean isCacheBeanMetadata();

    void setBeanExpressionResolver(BeanExpressionResolver resolver);

    BeanExpressionResolver getBeanExpressionResolver();

    void setBootstrapExecutor(Executor executor);

    Executor getBootstrapExecutor();

    void setConversionService(ConversionService conversionService);

    ConversionService getConversionService();

    void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar);

    void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass);

    void copyRegisteredEditorsTo(PropertyEditorRegistry registry);

    void setTypeConverter(TypeConverter typeConverter);

    TypeConverter getTypeConverter();

    void addEmbeddedValueResolver(StringValueResolver valueResolver);

    boolean hasEmbeddedValueResolver();

    String resolveEmbeddedValue(String value);

    void addBeanPostProcessor(BeanPostProcessor beanPostProcessor);

    int getBeanPostProcessorCount();

    void registerScope(String scopeName, Scope scope);

    String[] getRegisteredScopeNames();

    Scope getRegisteredScope(String scopeName);

    void setApplicationStartup(ApplicationStartup applicationStartup);

    ApplicationStartup getApplicationStartup();

    void copyConfigurationFrom(ConfigurableBeanFactory otherFactory);

    void registerAlias(String beanName, String alias) throws BeanDefinitionStoreException;

    void resolveAliases(StringValueResolver valueResolver);

    BeanDefinition getMergedBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

    boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException;

    void setCurrentlyInCreation(String beanName, boolean inCreation);

    boolean isCurrentlyInCreation(String beanName);

    void registerDependentBean(String beanName, String dependentBeanName);

    String[] getDependentBeans(String beanName);

    String[] getDependenciesForBean(String beanName);

    void destroyBean(String beanName, Object beanInstance);

    void destroyScopedBean(String beanName);

    void destroySingletons();
}
