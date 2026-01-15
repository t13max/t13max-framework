package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.core.ResolvableType;

/**
 * 对象定义接口
 *
 * @Author: t13max
 * @Since: 21:41 2026/1/14
 */
public interface BeanDefinition {

    String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

    String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;    
    int ROLE_APPLICATION = 0;

    int ROLE_SUPPORT = 1;    
    int ROLE_INFRASTRUCTURE = 2;

    // Modifiable attributes

    void setParentName(String parentName);    
    String getParentName();

    void setBeanClassName(String beanClassName);    
    String getBeanClassName();    
    void setScope(String scope);

    String getScope();    
    void setLazyInit(boolean lazyInit);

    boolean isLazyInit();    
    void setDependsOn(String ... dependsOn);

    String [] getDependsOn();    
    void setAutowireCandidate(boolean autowireCandidate);    
    boolean isAutowireCandidate();

    void setPrimary(boolean primary);    
    boolean isPrimary();

    void setFallback(boolean fallback);    
    boolean isFallback();    
    void setFactoryBeanName(String factoryBeanName);

    String getFactoryBeanName();    
    void setFactoryMethodName(String factoryMethodName);

    String getFactoryMethodName();    
    ConstructorArgumentValues getConstructorArgumentValues();

    default boolean hasConstructorArgumentValues() {
        return !getConstructorArgumentValues().isEmpty();
    }

    MutablePropertyValues getPropertyValues();

    default boolean hasPropertyValues() {
        return !getPropertyValues().isEmpty();
    }    
    void setInitMethodName(String initMethodName);    
    String getInitMethodName();    
    void setDestroyMethodName(String destroyMethodName);

    String getDestroyMethodName();

    void setRole(int role);

    int getRole();

    void setDescription(String description);    
    String getDescription();

    // Read-only attributes

    ResolvableType getResolvableType();    
    boolean isSingleton();

    boolean isPrototype();

    boolean isAbstract();    
    String getResourceDescription();

    BeanDefinition getOriginatingBeanDefinition();
    
}
