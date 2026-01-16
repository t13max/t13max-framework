package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanCreationException;
import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.BeanFactoryUtils;
import com.t13max.ioc.beans.factory.FactoryBean;
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


    public BeanDefinitionValueResolver(AbstractAutowireCapableBeanFactory beanFactory, String beanName, BeanDefinition beanDefinition, TypeConverter typeConverter) {

        this.beanFactory = beanFactory;
        this.beanName = beanName;
        this.beanDefinition = beanDefinition;
        this.typeConverter = typeConverter;
    }

    public BeanDefinitionValueResolver(AbstractAutowireCapableBeanFactory beanFactory, String beanName, BeanDefinition beanDefinition) {

        this.beanFactory = beanFactory;
        this.beanName = beanName;
        this.beanDefinition = beanDefinition;
        BeanWrapper beanWrapper = new BeanWrapperImpl();
        beanFactory.initBeanWrapper(beanWrapper);
        this.typeConverter = beanWrapper;
    }

    //解析属性值, 对注入类型进行转换
    @Nullable
    public Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
        // 对引用类型的属性进行解析, RuntimeBeanReference是在对BeanDefinition进行解析时生成的数据对象
        if (value instanceof RuntimeBeanReference ref) {
            //解析引用类型的属性值
            return resolveReference(argName, ref);
        } else if (value instanceof RuntimeBeanNameReference ref) {
            String refName = ref.getBeanName();
            refName = String.valueOf(doEvaluate(refName));
            if (!this.beanFactory.containsBean(refName)) {
                throw new BeanDefinitionStoreException("Invalid bean name '" + refName + "' in bean reference for " + argName);
            }
            return refName;
        }
        //对BeanDefinitionHolder类型属性的解析, 主要是bean中的内部类
        else if (value instanceof BeanDefinitionHolder bdHolder) {
            return resolveInnerBean(bdHolder.getBeanName(), bdHolder.getBeanDefinition(), (name, mbd) -> resolveInnerBeanValue(argName, name, mbd));
        } else if (value instanceof BeanDefinition bd) {
            return resolveInnerBean(null, bd, (name, mbd) -> resolveInnerBeanValue(argName, name, mbd));
        } else if (value instanceof DependencyDescriptor dependencyDescriptor) {
            Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
            Object result = this.beanFactory.resolveDependency(dependencyDescriptor, this.beanName, autowiredBeanNames, this.typeConverter);
            for (String autowiredBeanName : autowiredBeanNames) {
                if (this.beanFactory.containsBean(autowiredBeanName)) {
                    this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
                }
            }
            return result;
        }
        //对集合数组类型的属性解析
        else if (value instanceof ManagedArray managedArray) {
            // 获取数组的类型
            Class<?> elementType = managedArray.resolvedElementType;
            if (elementType == null) {
                //获取数组元素类型
                String elementTypeName = managedArray.getElementTypeName();
                if (StringUtils.hasText(elementTypeName)) {
                    try {
                        //使用反射机制创建指定类型的对象
                        elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
                        managedArray.resolvedElementType = elementType;
                    } catch (Throwable ex) {
                        // Improve the message by showing the context.
                        throw new BeanCreationException(this.beanDefinition.getResourceDescription(), this.beanName, "Error resolving array type for " + argName, ex);
                    }
                } else {
                    //没有获取到数组的类型, 也没有获取到数组元素的类型, 则直接设置数组的类型为Object
                    elementType = Object.class;
                }
            }
            return resolveManagedArray(argName, (List<?>) value, elementType);
        } else if (value instanceof ManagedList<?> managedList) {
            // 解析list类型的属性值
            return resolveManagedList(argName, managedList);
        } else if (value instanceof ManagedSet<?> managedSet) {
            // 解析set类型的属性值
            return resolveManagedSet(argName, managedSet);
        } else if (value instanceof ManagedMap<?, ?> managedMap) {
            // 解析map类型的属性值
            return resolveManagedMap(argName, managedMap);
        } else if (value instanceof ManagedProperties original) {
            // 解析Properties类型的属性值
            Properties copy = new Properties();
            //拷贝, 用作解析后的返回值
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
        } else if (value instanceof TypedStringValue typedStringValue) {
            // 解析字符串类型的属性值
            Object valueObject = evaluate(typedStringValue);
            try {
                //获取属性的目标类型
                Class<?> resolvedTargetType = resolveTargetType(typedStringValue);
                if (resolvedTargetType != null) {
                    //对目标类型的属性进行解析, 递归调用
                    return this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType);
                } else {
                    //没有获取到属性的目标对象, 则按Object类型返回
                    return valueObject;
                }
            } catch (Throwable ex) {
                // Improve the message by showing the context.
                throw new BeanCreationException(this.beanDefinition.getResourceDescription(), this.beanName, "Error converting typed String value for " + argName, ex);
            }
        } else if (value instanceof NullBean) {
            return null;
        } else {
            return evaluate(value);
        }
    }

    public <T> T resolveInnerBean(@Nullable String innerBeanName, BeanDefinition innerBd, BiFunction<String, RootBeanDefinition, T> resolver) {
        String nameToUse = (innerBeanName != null ? innerBeanName : "(inner bean)" + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(innerBd));
        return resolver.apply(nameToUse, this.beanFactory.getMergedBeanDefinition(nameToUse, innerBd, this.beanDefinition));
    }

    protected Object evaluate(TypedStringValue value) {
        Object result = doEvaluate(value.getValue());
        if (!ObjectUtils.nullSafeEquals(result, value.getValue())) {
            value.setDynamic();
        }
        return result;
    }

    protected Object evaluate(@Nullable Object value) {
        if (value instanceof String str) {
            return doEvaluate(str);
        } else if (value instanceof String[] values) {
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
        } else {
            return value;
        }
    }

    private Object doEvaluate(@Nullable String value) {
        return this.beanFactory.evaluateBeanDefinitionString(value, this.beanDefinition);
    }

    protected Class<?> resolveTargetType(TypedStringValue value) throws ClassNotFoundException {
        if (value.hasTargetType()) {
            return value.getTargetType();
        }
        return value.resolveTargetType(this.beanFactory.getBeanClassLoader());
    }

    //解析引用类型的属性值
    private Object resolveReference(Object argName, RuntimeBeanReference ref) {
        try {
            Object bean;
            Class<?> beanType = ref.getBeanType();
            //如果引用的对象在父容器中, 则从父容器中获取指定的引用对象
            if (ref.isToParent()) {
                BeanFactory parent = this.beanFactory.getParentBeanFactory();
                if (parent == null) {
                    throw new BeanCreationException(this.beanDefinition.getResourceDescription(), this.beanName, "Cannot resolve reference to bean " + ref + " in parent factory: no parent factory available");
                }
                if (beanType != null) {
                    bean = parent.getBean(beanType);
                } else {
                    bean = parent.getBean(String.valueOf(doEvaluate(ref.getBeanName())));
                }
            } else {
                //从当前的容器中获取指定的引用bean对象, 如果指定的bean没有被实例化, 则会递归触发引用bean的初始化和依赖注入
                String resolvedName;
                if (beanType != null) {
                    NamedBeanHolder<?> namedBean = this.beanFactory.resolveNamedBean(beanType);
                    bean = namedBean.getBeanInstance();
                    resolvedName = namedBean.getBeanName();
                } else {
                    resolvedName = String.valueOf(doEvaluate(ref.getBeanName()));
                    //为refName对应的bean注入它所依赖的bean
                    bean = this.beanFactory.getBean(resolvedName);
                }
                this.beanFactory.registerDependentBean(resolvedName, this.beanName);
            }
            if (bean instanceof NullBean) {
                bean = null;
            }
            return bean;
        } catch (BeansException ex) {
            throw new BeanCreationException(this.beanDefinition.getResourceDescription(), this.beanName, "Cannot resolve reference to bean '" + ref.getBeanName() + "' while setting " + argName, ex);
        }
    }

    private Object resolveInnerBeanValue(Object argName, String innerBeanName, RootBeanDefinition mbd) {
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
                innerBean = this.beanFactory.getObjectFromFactoryBean(factoryBean, actualInnerBeanName, !synthetic);
            }
            if (innerBean instanceof NullBean) {
                innerBean = null;
            }
            return innerBean;
        } catch (BeansException ex) {
            throw new BeanCreationException(this.beanDefinition.getResourceDescription(), this.beanName, "Cannot create inner bean '" + innerBeanName + "' " + (mbd.getBeanClassName() != null ? "of type [" + mbd.getBeanClassName() + "] " : "") + "while setting " + argName, ex);
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

    //解析array类型的属性
    private Object resolveManagedArray(Object argName, List<?> ml, Class<?> elementType) {
        //创建一个指定类型的数组, 用于存放和返回解析后的数组
        Object resolved = Array.newInstance(elementType, ml.size());
        for (int i = 0; i < ml.size(); i++) {
            //递归解析array的每一个元素, 并将解析后的值设置到resolved数组中, 索引为i
            Array.set(resolved, i, resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
        }
        return resolved;
    }

    //解析list类型的属性
    private List<?> resolveManagedList(Object argName, List<?> ml) {
        List<Object> resolved = new ArrayList<>(ml.size());
        for (int i = 0; i < ml.size(); i++) {
            resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
        }
        return resolved;
    }

    //解析set类型的属性
    private Set<?> resolveManagedSet(Object argName, Set<?> ms) {
        Set<Object> resolved = new LinkedHashSet<>(ms.size());
        int i = 0;
        for (Object m : ms) {
            resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), m));
            i++;
        }
        return resolved;
    }

    //解析map类型的属性
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
            return this.argName + " with key " + BeanWrapper.PROPERTY_KEY_PREFIX + this.key + BeanWrapper.PROPERTY_KEY_SUFFIX;
        }
    }

}
