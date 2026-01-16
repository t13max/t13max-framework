package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;

import java.util.Set;

/**
 * @Author: t13max
 * @Since: 21:19 2026/1/15
 */
public interface AutowireCapableBeanFactory extends BeanFactory {
    int AUTOWIRE_NO = 0;
    int AUTOWIRE_BY_NAME = 1;
    int AUTOWIRE_BY_TYPE = 2;
    int AUTOWIRE_CONSTRUCTOR = 3;
    @Deprecated(since = "3.0")
    int AUTOWIRE_AUTODETECT = 4;
    String ORIGINAL_INSTANCE_SUFFIX = ".ORIGINAL";


    //-------------------------------------------------------------------------
    // Typical methods for creating and populating external bean instances
    //-------------------------------------------------------------------------
    <T> T createBean(Class<T> beanClass) throws BeansException;
    void autowireBean(Object existingBean) throws BeansException;
    Object configureBean(Object existingBean, String beanName) throws BeansException;


    //-------------------------------------------------------------------------
    // Specialized methods for fine-grained control over the bean lifecycle
    //-------------------------------------------------------------------------
    @Deprecated(since = "6.1")
    Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException;
    Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException;
    void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
            throws BeansException;
    void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException;
    Object initializeBean(Object existingBean, String beanName) throws BeansException;
    @Deprecated(since = "6.1")
    Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
            throws BeansException;
    @Deprecated(since = "6.1")
    Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
            throws BeansException;
    void destroyBean(Object existingBean);


    //-------------------------------------------------------------------------
    // Delegate methods for resolving injection points
    //-------------------------------------------------------------------------
    <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException;
    Object resolveBeanByName(String name, DependencyDescriptor descriptor) throws BeansException;
     Object resolveDependency(DependencyDescriptor descriptor,  String requestingBeanName) throws BeansException;
     Object resolveDependency(DependencyDescriptor descriptor,  String requestingBeanName,
                                        Set<String> autowiredBeanNames,  TypeConverter typeConverter) throws BeansException;
}
