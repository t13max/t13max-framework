package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.TypeConverter;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinitionHolder;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.beans.factory.config.DependencyDescriptor;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.style.ToStringCreator;
import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;
import com.t13max.ioc.expression.ConstructorResolver;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.StringUtils;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 0:06 2026/1/17
 */
public class RegisteredBean {


    private final ConfigurableListableBeanFactory beanFactory;

    private final Supplier<String> beanName;

    private final boolean generatedBeanName;

    private final Supplier<RootBeanDefinition> mergedBeanDefinition;

    private final  RegisteredBean parent;


    private RegisteredBean(ConfigurableListableBeanFactory beanFactory, Supplier<String> beanName,
                           boolean generatedBeanName, Supplier<RootBeanDefinition> mergedBeanDefinition,
                            RegisteredBean parent) {

        this.beanFactory = beanFactory;
        this.beanName = beanName;
        this.generatedBeanName = generatedBeanName;
        this.mergedBeanDefinition = mergedBeanDefinition;
        this.parent = parent;
    }


    
    public static RegisteredBean of(ConfigurableListableBeanFactory beanFactory, String beanName) {
        Assert.notNull(beanFactory, "'beanFactory' must not be null");
        Assert.hasLength(beanName, "'beanName' must not be empty");
        return new RegisteredBean(beanFactory, () -> beanName, false,
                () -> (RootBeanDefinition) beanFactory.getMergedBeanDefinition(beanName),
                null);
    }

    
    static RegisteredBean of(ConfigurableListableBeanFactory beanFactory, String beanName, RootBeanDefinition mbd) {
        return new RegisteredBean(beanFactory, () -> beanName, false, () -> mbd, null);
    }

    
    public static RegisteredBean ofInnerBean(RegisteredBean parent, BeanDefinitionHolder innerBean) {
        Assert.notNull(innerBean, "'innerBean' must not be null");
        return ofInnerBean(parent, innerBean.getBeanName(), innerBean.getBeanDefinition());
    }

    
    public static RegisteredBean ofInnerBean(RegisteredBean parent, BeanDefinition innerBeanDefinition) {
        return ofInnerBean(parent, null, innerBeanDefinition);
    }

    
    public static RegisteredBean ofInnerBean(RegisteredBean parent,
                                              String innerBeanName, BeanDefinition innerBeanDefinition) {

        Assert.notNull(parent, "'parent' must not be null");
        Assert.notNull(innerBeanDefinition, "'innerBeanDefinition' must not be null");
        InnerBeanResolver resolver = new InnerBeanResolver(parent, innerBeanName, innerBeanDefinition);
        Supplier<String> beanName = (StringUtils.hasLength(innerBeanName) ?
                () -> innerBeanName : resolver::resolveBeanName);
        return new RegisteredBean(parent.getBeanFactory(), beanName,
                innerBeanName == null, resolver::resolveMergedBeanDefinition, parent);
    }


    
    public String getBeanName() {
        return this.beanName.get();
    }

    
    public boolean isGeneratedBeanName() {
        return this.generatedBeanName;
    }

    
    public ConfigurableListableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    
    public Class<?> getBeanClass() {
        return ClassUtils.getUserClass(getBeanType().toClass());
    }

    
    public ResolvableType getBeanType() {
        return getMergedBeanDefinition().getResolvableType();
    }

    
    public RootBeanDefinition getMergedBeanDefinition() {
        return this.mergedBeanDefinition.get();
    }

    
    public boolean isInnerBean() {
        return this.parent != null;
    }

    
    public  RegisteredBean getParent() {
        return this.parent;
    }


    @Override
    public String toString() {
        return new ToStringCreator(this).append("beanName", getBeanName())
                .append("mergedBeanDefinition", getMergedBeanDefinition()).toString();
    }


    
    public record InstantiationDescriptor(Executable executable, Class<?> targetClass) {

        public InstantiationDescriptor(Executable executable) {
            this(executable, executable.getDeclaringClass());
        }
    }


    
    private static class InnerBeanResolver {

        private final RegisteredBean parent;

        private final  String innerBeanName;

        private final BeanDefinition innerBeanDefinition;

        private volatile  String resolvedBeanName;

        InnerBeanResolver(RegisteredBean parent,  String innerBeanName, BeanDefinition innerBeanDefinition) {
            Assert.isInstanceOf(AbstractAutowireCapableBeanFactory.class, parent.getBeanFactory());
            this.parent = parent;
            this.innerBeanName = innerBeanName;
            this.innerBeanDefinition = innerBeanDefinition;
        }

        String resolveBeanName() {
            String resolvedBeanName = this.resolvedBeanName;
            if (resolvedBeanName != null) {
                return resolvedBeanName;
            }
            resolvedBeanName = resolveInnerBean((beanName, mergedBeanDefinition) -> beanName);
            this.resolvedBeanName = resolvedBeanName;
            return resolvedBeanName;
        }

        RootBeanDefinition resolveMergedBeanDefinition() {
            return resolveInnerBean((beanName, mergedBeanDefinition) -> mergedBeanDefinition);
        }

        private <T> T resolveInnerBean(BiFunction<String, RootBeanDefinition, T> resolver) {
            // Always use a fresh BeanDefinitionValueResolver in case the parent merged bean definition has changed.
            BeanDefinitionValueResolver beanDefinitionValueResolver = new BeanDefinitionValueResolver(
                    (AbstractAutowireCapableBeanFactory) this.parent.getBeanFactory(),
                    this.parent.getBeanName(), this.parent.getMergedBeanDefinition());
            return beanDefinitionValueResolver.resolveInnerBean(this.innerBeanName, this.innerBeanDefinition, resolver);
        }
    }

}
