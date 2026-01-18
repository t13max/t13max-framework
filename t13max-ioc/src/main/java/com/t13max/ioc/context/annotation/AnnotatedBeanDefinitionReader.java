package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinitionCustomizer;
import com.t13max.ioc.beans.factory.config.BeanDefinitionHolder;
import com.t13max.ioc.beans.factory.support.AutowireCandidateQualifier;
import com.t13max.ioc.beans.factory.support.BeanDefinitionReaderUtils;
import com.t13max.ioc.beans.factory.support.BeanDefinitionRegistry;
import com.t13max.ioc.beans.factory.support.BeanNameGenerator;
import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.core.env.EnvironmentCapable;
import com.t13max.ioc.core.env.StandardEnvironment;
import com.t13max.ioc.util.Assert;
import org.apache.logging.log4j.util.Lazy;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 20:45 2026/1/16
 */
public class AnnotatedBeanDefinitionReader {

    private final BeanDefinitionRegistry registry;

    private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

    private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

    private ConditionEvaluator conditionEvaluator;

    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
        this(registry, getOrCreateEnvironment(registry));
    }

    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        Assert.notNull(environment, "Environment must not be null");
        this.registry = registry;
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
        AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
    }

    public final BeanDefinitionRegistry getRegistry() {
        return this.registry;
    }

    public void setEnvironment(Environment environment) {
        this.conditionEvaluator = new ConditionEvaluator(this.registry, environment, null);
    }

    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator =
                (beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
    }

    public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
        this.scopeMetadataResolver =
                (scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
    }

    public void register(Class<?>... componentClasses) {
        for (Class<?> componentClass : componentClasses) {
            registerBean(componentClass);
        }
    }

    public void registerBean(Class<?> beanClass) {
        doRegisterBean(beanClass, null, null, null, null);
    }

    public void registerBean(Class<?> beanClass, String name) {
        doRegisterBean(beanClass, name, null, null, null);
    }

    @SuppressWarnings("unchecked")
    public void registerBean(Class<?> beanClass, Class<? extends Annotation>... qualifiers) {
        doRegisterBean(beanClass, null, qualifiers, null, null);
    }

    @SuppressWarnings("unchecked")
    public void registerBean(Class<?> beanClass, String name,
                             Class<? extends Annotation>... qualifiers) {

        doRegisterBean(beanClass, name, qualifiers, null, null);
    }

    public <T> void registerBean(Class<T> beanClass, Supplier<T> supplier) {
        doRegisterBean(beanClass, null, null, supplier, null);
    }

    public <T> void registerBean(Class<T> beanClass, String name, Supplier<T> supplier) {
        doRegisterBean(beanClass, name, null, supplier, null);
    }

    public <T> void registerBean(Class<T> beanClass, String name, Supplier<T> supplier,
                                 BeanDefinitionCustomizer... customizers) {

        doRegisterBean(beanClass, name, null, supplier, customizers);
    }

    private <T> void doRegisterBean(Class<T> beanClass, String name,
                                    Class<? extends Annotation>[] qualifiers, Supplier<T> supplier,
                                    BeanDefinitionCustomizer[] customizers) {

        AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);
        if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
            return;
        }

        abd.setAttribute(ConfigurationClassUtils.CANDIDATE_ATTRIBUTE, Boolean.TRUE);
        abd.setInstanceSupplier(supplier);
        ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
        abd.setScope(scopeMetadata.getScopeName());
        String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

        AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
        if (qualifiers != null) {
            for (Class<? extends Annotation> qualifier : qualifiers) {
                if (Primary.class == qualifier) {
                    abd.setPrimary(true);
                } else if (Fallback.class == qualifier) {
                    abd.setFallback(true);
                } else if (Lazy.class == qualifier) {
                    abd.setLazyInit(true);
                } else {
                    abd.addQualifier(new AutowireCandidateQualifier(qualifier));
                }
            }
        }
        if (customizers != null) {
            for (BeanDefinitionCustomizer customizer : customizers) {
                customizer.customize(abd);
            }
        }

        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
        definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
        BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
    }

    private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        if (registry instanceof EnvironmentCapable environmentCapable) {
            return environmentCapable.getEnvironment();
        }
        return new StandardEnvironment();
    }

}
