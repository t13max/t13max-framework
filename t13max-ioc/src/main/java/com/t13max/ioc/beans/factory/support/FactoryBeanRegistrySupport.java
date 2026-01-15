package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanCreationException;
import com.t13max.ioc.beans.factory.FactoryBean;
import com.t13max.ioc.beans.factory.SmartFactoryBean;
import com.t13max.ioc.core.AttributeAccessor;
import com.t13max.ioc.core.ResolvableType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {


    private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


    protected Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
        try {
            return factoryBean.getObjectType();
        } catch (Throwable ex) {
            // Thrown from the FactoryBean's getObjectType implementation.
            logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
                    "that it should return null if the type of its object cannot be determined yet", ex);
            return null;
        }
    }


    ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
        Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        if (attribute == null) {
            return ResolvableType.NONE;
        }
        if (attribute instanceof ResolvableType resolvableType) {
            return resolvableType;
        }
        if (attribute instanceof Class<?> clazz) {
            return ResolvableType.forClass(clazz);
        }
        throw new IllegalArgumentException("Invalid value type for attribute '" +
                FactoryBean.OBJECT_TYPE_ATTRIBUTE + "': " + attribute.getClass().getName());
    }


    ResolvableType getFactoryBeanGeneric(ResolvableType type) {
        return (type != null ? type.as(FactoryBean.class).getGeneric() : ResolvableType.NONE);
    }


    protected Object getCachedObjectForFactoryBean(String beanName) {
        return this.factoryBeanObjectCache.get(beanName);
    }

    // 从FactoryBean中获取Bean
    protected Object getObjectFromFactoryBean(FactoryBean<?> factory, Class<?> requiredType, String beanName, boolean shouldPostProcess) {

        // 是否单例 是否已经包含
        if (factory.isSingleton() && containsSingleton(beanName)) {
            Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
            boolean locked;
            if (lockFlag == null) {
                this.singletonLock.lock();
                locked = true;
            } else {
                locked = (lockFlag && this.singletonLock.tryLock());
            }
            try {
                // A SmartFactoryBean may return multiple object types -> do not cache.
                boolean smart = (factory instanceof SmartFactoryBean<?>);
                Object object = (!smart ? this.factoryBeanObjectCache.get(beanName) : null);
                if (object == null) {
                    object = doGetObjectFromFactoryBean(factory, requiredType, beanName);
                    // Only post-process and store if not put there already during getObject() call above
                    // (for example, because of circular reference processing triggered by custom getBean calls)
                    Object alreadyThere = (!smart ? this.factoryBeanObjectCache.get(beanName) : null);
                    if (alreadyThere != null) {
                        object = alreadyThere;
                    } else {
                        if (shouldPostProcess) {
                            if (locked) {
                                if (isSingletonCurrentlyInCreation(beanName)) {
                                    // Temporarily return non-post-processed object, not storing it yet
                                    return object;
                                }
                                beforeSingletonCreation(beanName);
                            }
                            try {
                                object = postProcessObjectFromFactoryBean(object, beanName);
                            } catch (Throwable ex) {
                                throw new BeanCreationException(beanName,
                                        "Post-processing of FactoryBean's singleton object failed", ex);
                            } finally {
                                if (locked) {
                                    afterSingletonCreation(beanName);
                                }
                            }
                        }
                        if (!smart && containsSingleton(beanName)) {
                            this.factoryBeanObjectCache.put(beanName, object);
                        }
                    }
                }
                return object;
            } finally {
                if (locked) {
                    this.singletonLock.unlock();
                }
            }
        } else {
            Object object = doGetObjectFromFactoryBean(factory, requiredType, beanName);
            if (shouldPostProcess) {
                try {
                    object = postProcessObjectFromFactoryBean(object, beanName);
                } catch (Throwable ex) {
                    throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
                }
            }
            return object;
        }
    }


    private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, Class<?> requiredType, String beanName)
            throws BeanCreationException {

        Object object;
        try {
            object = (requiredType != null && factory instanceof SmartFactoryBean<?> smartFactoryBean ?
                    smartFactoryBean.getObject(requiredType) : factory.getObject());
        } catch (FactoryBeanNotInitializedException ex) {
            throw new BeanCurrentlyInCreationException(beanName, ex.toString());
        } catch (Throwable ex) {
            throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
        }

        // Do not accept a null value for a FactoryBean that's not fully
        // initialized yet: Many FactoryBeans just return null then.
        if (object == null) {
            if (isSingletonCurrentlyInCreation(beanName)) {
                throw new BeanCurrentlyInCreationException(
                        beanName, "FactoryBean which is currently in creation returned null from getObject");
            }
            object = new NullBean();
        }
        return object;
    }


    protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
        return object;
    }


    protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
        if (!(beanInstance instanceof FactoryBean<?> factoryBean)) {
            throw new BeanCreationException(beanName,
                    "Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
        }
        return factoryBean;
    }


    @Override
    protected void removeSingleton(String beanName) {
        super.removeSingleton(beanName);
        this.factoryBeanObjectCache.remove(beanName);
    }


    @Override
    protected void clearSingletonCache() {
        super.clearSingletonCache();
        this.factoryBeanObjectCache.clear();
    }

}
