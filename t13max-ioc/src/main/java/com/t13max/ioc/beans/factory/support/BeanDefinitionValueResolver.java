package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeanWrapperImpl;
import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.*;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinitionHolder;
import com.t13max.ioc.beans.factory.config.DependencyDescriptor;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.CollectionUtils;
import com.t13max.ioc.utils.ObjectUtils;
import com.t13max.ioc.utils.StringUtils;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiFunction;

/**
 * @author t13max
 * @since 13:51 2026/1/16
 */
public class BeanDefinitionValueResolver {

    private final AbstractAutowireCapableBeanFactory beanFactory;

    private final String beanName;

    private final BeanDefinition beanDefinition;

    private final TypeConverter typeConverter;
    
    public BeanDefinitionValueResolver(AbstractAutowireCapableBeanFactory beanFactory, String beanName,
                                       BeanDefinition beanDefinition, TypeConverter typeConverter) {

        this.beanFactory = beanFactory;
        this.beanName = beanName;
        this.beanDefinition = beanDefinition;
        this.typeConverter = typeConverter;
    }    
    public BeanDefinitionValueResolver(AbstractAutowireCapableBeanFactory beanFactory, String beanName,
                                       BeanDefinition beanDefinition) {

        this.beanFactory = beanFactory;
        this.beanName = beanName;
        this.beanDefinition = beanDefinition;
        BeanWrapper beanWrapper = new BeanWrapperImpl();
        beanFactory.initBeanWrapper(beanWrapper);
        this.typeConverter = beanWrapper;
    }
    
    public  Object resolveValueIfNecessary(Object argName,  Object value) {
        // We must check each value to see whether it requires a runtime reference
        // to another bean to be resolved.
        if (value instanceof RuntimeBeanReference ref) {
            return resolveReference(argName, ref);
        }
        else if (value instanceof RuntimeBeanNameReference ref) {
            String refName = ref.getBeanName();
            refName = String.valueOf(doEvaluate(refName));
            if (!this.beanFactory.containsBean(refName)) {
                throw new BeanDefinitionStoreException(
                        "Invalid bean name '" + refName + "' in bean reference for " + argName);
            }
            return refName;
        }
        else if (value instanceof BeanDefinitionHolder bdHolder) {
            // Resolve BeanDefinitionHolder: contains BeanDefinition with name and aliases.
            return resolveInnerBean(bdHolder.getBeanName(), bdHolder.getBeanDefinition(),
                    (name, mbd) -> resolveInnerBeanValue(argName, name, mbd));
        }
        else if (value instanceof BeanDefinition bd) {
            return resolveInnerBean(null, bd,
                    (name, mbd) -> resolveInnerBeanValue(argName, name, mbd));
        }
        else if (value instanceof DependencyDescriptor dependencyDescriptor) {
            Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
            Object result = this.beanFactory.resolveDependency(
                    dependencyDescriptor, this.beanName, autowiredBeanNames, this.typeConverter);
            for (String autowiredBeanName : autowiredBeanNames) {
                if (this.beanFactory.containsBean(autowiredBeanName)) {
                    this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
                }
            }
            return result;
        }
        else if (value instanceof ManagedArray managedArray) {
            // May need to resolve contained runtime references.
            Class<?> elementType = managedArray.resolvedElementType;
            if (elementType == null) {
                String elementTypeName = managedArray.getElementTypeName();
                if (StringUtils.hasText(elementTypeName)) {
                    try {
                        elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
                        managedArray.resolvedElementType = elementType;
                    }
                    catch (Throwable ex) {
                        // Improve the message by showing the context.
                        throw new BeanCreationException(
                                this.beanDefinition.getResourceDescription(), this.beanName,
                                "Error resolving array type for " + argName, ex);
                    }
                }
                else {
                    elementType = Object.class;
                }
            }
            return resolveManagedArray(argName, (List<?>) value, elementType);
        }
        else if (value instanceof ManagedList<?> managedList) {
            // May need to resolve contained runtime references.
            return resolveManagedList(argName, managedList);
        }
        else if (value instanceof ManagedSet<?> managedSet) {
            // May need to resolve contained runtime references.
            return resolveManagedSet(argName, managedSet);
        }
        else if (value instanceof ManagedMap<?, ?> managedMap) {
            // May need to resolve contained runtime references.
            return resolveManagedMap(argName, managedMap);
        }
        else if (value instanceof ManagedProperties original) {
            // Properties original = managedProperties;
            Properties copy = new Properties();
            original.forEach((propKey, propValue) -> {
                if (propKey instanceof TypedStringValue typedStringValue) {
                    propKey = evaluate(typedStringValue);
                }
                if (propValue instanceof TypedStringValue typedStringValue) {
                    propValue = evaluate(typedStringValue);
                }
                if (propKey == null || propValue == null) {
                    throw new BeanCreationException(
                            this.beanDefinition.getResourceDescription(), this.beanName,
                            "Error converting Properties key/value pair for " + argName + ": resolved to null");
                }
                copy.put(propKey, propValue);
            });
            return copy;
        }
        else if (value instanceof TypedStringValue typedStringValue) {
            // Convert value to target type here.
            Object valueObject = evaluate(typedStringValue);
            try {
                Class<?> resolvedTargetType = resolveTargetType(typedStringValue);
                if (resolvedTargetType != null) {
                    return this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType);
                }
                else {
                    return valueObject;
                }
            }
            catch (Throwable ex) {
                // Improve the message by showing the context.
                throw new BeanCreationException(
                        this.beanDefinition.getResourceDescription(), this.beanName,
                        "Error converting typed String value for " + argName, ex);
            }
        }
        else if (value instanceof NullBean) {
            return null;
        }
        else {
            return evaluate(value);
        }
    }    
    public <T> T resolveInnerBean( String innerBeanName, BeanDefinition innerBd,
                                  BiFunction<String, RootBeanDefinition, T> resolver) {

        String nameToUse = (innerBeanName != null ? innerBeanName : "(inner bean)" +
                BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(innerBd));
        return resolver.apply(nameToUse,
                this.beanFactory.getMergedBeanDefinition(nameToUse, innerBd, this.beanDefinition));
    }    
    protected  Object evaluate(TypedStringValue value) {
        Object result = doEvaluate(value.getValue());
        if (!ObjectUtils.nullSafeEquals(result, value.getValue())) {
            value.setDynamic();
        }
        return result;
    }    
    protected  Object evaluate( Object value) {
        if (value instanceof String str) {
            return doEvaluate(str);
        }
        else if (value instanceof String[] values) {
            boolean actuallyResolved = false;
             Object[] resolvedValues = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                String originalValue = values[i];
                Object resolvedValue = doEvaluate(originalValue);
                if (resolvedValue != originalValue) {
                    actuallyResolved = true;
                }
                resolvedValues[i] = resolvedValue;
            }
            return (actuallyResolved ? resolvedValues : values);
        }
        else {
            return value;
        }
    }    
    private  Object doEvaluate( String value) {
        return this.beanFactory.evaluateBeanDefinitionString(value, this.beanDefinition);
    }    
    protected  Class<?> resolveTargetType(TypedStringValue value) throws ClassNotFoundException {
        if (value.hasTargetType()) {
            return value.getTargetType();
        }
        return value.resolveTargetType(this.beanFactory.getBeanClassLoader());
    }    
    private  Object resolveReference(Object argName, RuntimeBeanReference ref) {
        try {
            Object bean;
            Class<?> beanType = ref.getBeanType();
            if (ref.isToParent()) {
                BeanFactory parent = this.beanFactory.getParentBeanFactory();
                if (parent == null) {
                    throw new BeanCreationException(
                            this.beanDefinition.getResourceDescription(), this.beanName,
                            "Cannot resolve reference to bean " + ref +
                                    " in parent factory: no parent factory available");
                }
                if (beanType != null) {
                    bean = parent.getBean(beanType);
                }
                else {
                    bean = parent.getBean(String.valueOf(doEvaluate(ref.getBeanName())));
                }
            }
            else {
                String resolvedName;
                if (beanType != null) {
                    NamedBeanHolder<?> namedBean = this.beanFactory.resolveNamedBean(beanType);
                    bean = namedBean.getBeanInstance();
                    resolvedName = namedBean.getBeanName();
                }
                else {
                    resolvedName = String.valueOf(doEvaluate(ref.getBeanName()));
                    bean = this.beanFactory.getBean(resolvedName);
                }
                this.beanFactory.registerDependentBean(resolvedName, this.beanName);
            }
            if (bean instanceof NullBean) {
                bean = null;
            }
            return bean;
        }
        catch (BeansException ex) {
            throw new BeanCreationException(
                    this.beanDefinition.getResourceDescription(), this.beanName,
                    "Cannot resolve reference to bean '" + ref.getBeanName() + "' while setting " + argName, ex);
        }
    }    
    private  Object resolveInnerBeanValue(Object argName, String innerBeanName, RootBeanDefinition mbd) {
        try {
            // Check given bean name whether it is unique. If not already unique,
            // add counter - increasing the counter until the name is unique.
            String actualInnerBeanName = innerBeanName;
            if (mbd.isSingleton()) {
                actualInnerBeanName = adaptInnerBeanName(innerBeanName);
            }
            this.beanFactory.registerContainedBean(actualInnerBeanName, this.beanName);
            // Guarantee initialization of beans that the inner bean depends on.
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                for (String dependsOnBean : dependsOn) {
                    this.beanFactory.registerDependentBean(dependsOnBean, actualInnerBeanName);
                    this.beanFactory.getBean(dependsOnBean);
                }
            }
            // Actually create the inner bean instance now...
            Object innerBean = this.beanFactory.createBean(actualInnerBeanName, mbd, null);
            if (innerBean instanceof FactoryBean<?> factoryBean) {
                boolean synthetic = mbd.isSynthetic();
                innerBean = this.beanFactory.getObjectFromFactoryBean(
                        factoryBean, null, actualInnerBeanName, !synthetic);
            }
            if (innerBean instanceof NullBean) {
                innerBean = null;
            }
            return innerBean;
        }
        catch (BeansException ex) {
            throw new BeanCreationException(
                    this.beanDefinition.getResourceDescription(), this.beanName,
                    "Cannot create inner bean '" + innerBeanName + "' " +
                            (mbd.getBeanClassName() != null ? "of type [" + mbd.getBeanClassName() + "] " : "") +
                            "while setting " + argName, ex);
        }
    }    
    private String adaptInnerBeanName(String innerBeanName) {
        String actualInnerBeanName = innerBeanName;
        int counter = 0;
        String prefix = innerBeanName + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR;
        while (this.beanFactory.isBeanNameInUse(actualInnerBeanName)) {
            counter++;
            actualInnerBeanName = prefix + counter;
        }
        return actualInnerBeanName;
    }    
    private Object resolveManagedArray(Object argName, List<?> ml, Class<?> elementType) {
        Object resolved = Array.newInstance(elementType, ml.size());
        for (int i = 0; i < ml.size(); i++) {
            Array.set(resolved, i, resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
        }
        return resolved;
    }    
    private List<?> resolveManagedList(Object argName, List<?> ml) {
        List<Object> resolved = new ArrayList<>(ml.size());
        for (int i = 0; i < ml.size(); i++) {
            resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
        }
        return resolved;
    }    
    private Set<?> resolveManagedSet(Object argName, Set<?> ms) {
        Set<Object> resolved = CollectionUtils.newLinkedHashSet(ms.size());
        int i = 0;
        for (Object m : ms) {
            resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), m));
            i++;
        }
        return resolved;
    }    
    private Map<?, ?> resolveManagedMap(Object argName, Map<?, ?> mm) {
        Map<Object, Object> resolved = CollectionUtils.newLinkedHashMap(mm.size());
        mm.forEach((key, value) -> {
            Object resolvedKey = resolveValueIfNecessary(argName, key);
            Object resolvedValue = resolveValueIfNecessary(new KeyedArgName(argName, key), value);
            resolved.put(resolvedKey, resolvedValue);
        });
        return resolved;
    }
    
    private static class KeyedArgName {

        private final Object argName;

        private final Object key;

        public KeyedArgName(Object argName, Object key) {
            this.argName = argName;
            this.key = key;
        }

        @Override
        public String toString() {
            return this.argName + " with key " + BeanWrapper.PROPERTY_KEY_PREFIX +
                    this.key + BeanWrapper.PROPERTY_KEY_SUFFIX;
        }
    }

}
