package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.BeanUtils;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.beans.factory.support.BeanDefinitionRegistry;
import com.t13max.ioc.context.ConfigurableApplicationContext;
import com.t13max.ioc.core.annotation.AnnotationAwareOrderComparator;
import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.core.env.EnvironmentCapable;
import com.t13max.ioc.core.env.StandardEnvironment;
import com.t13max.ioc.core.io.DefaultResourceLoader;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;
import com.t13max.ioc.core.type.AnnotatedTypeMetadata;
import com.t13max.ioc.core.type.AnnotationMetadata;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Author: t13max
 * @Since: 8:02 2026/1/17
 */
public class ConditionEvaluator {
    
    private final ConditionContextImpl context;

    public ConditionEvaluator( BeanDefinitionRegistry registry,  Environment environment,  ResourceLoader resourceLoader) {

        this.context = new ConditionContextImpl(registry, environment, resourceLoader);
    }

    
    public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
        return shouldSkip(metadata, null);
    }

    public boolean shouldSkip(AnnotatedTypeMetadata metadata, ConfigurationCondition.ConfigurationPhase phase) {
        if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
            return false;
        }

        if (phase == null) {
            if (metadata instanceof AnnotationMetadata annotationMetadata &&
                    ConfigurationClassUtils.isConfigurationCandidate(annotationMetadata)) {
                return shouldSkip(metadata, ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION);
            }
            return shouldSkip(metadata, ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
        }

        List<Condition> conditions = collectConditions(metadata);
        for (Condition condition : conditions) {
            ConfigurationCondition.ConfigurationPhase requiredPhase = null;
            if (condition instanceof ConfigurationCondition configurationCondition) {
                requiredPhase = configurationCondition.getConfigurationPhase();
            }
            if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return the {@linkplain Condition conditions} that should be applied when
     * considering the given annotated type.
     *
     * @param metadata the metadata of the annotated type
     * @return the ordered list of conditions for that type
     */
    List<Condition> collectConditions(AnnotatedTypeMetadata metadata) {
        if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
            return Collections.emptyList();
        }

        List<Condition> conditions = new ArrayList<>();
        for (String[] conditionClasses : getConditionClasses(metadata)) {
            for (String conditionClass : conditionClasses) {
                Condition condition = getCondition(conditionClass, this.context.getClassLoader());
                conditions.add(condition);
            }
        }
        AnnotationAwareOrderComparator.sort(conditions);
        return conditions;
    }

    @SuppressWarnings("unchecked")
    private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
        MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
        Object values = (attributes != null ? attributes.get("value") : null);
        return (List<String[]>) (values != null ? values : Collections.emptyList());
    }

    private Condition getCondition(String conditionClassName, ClassLoader classloader) {
        Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
        return (Condition) BeanUtils.instantiateClass(conditionClass);
    }


    /**
     * Implementation of a {@link ConditionContext}.
     */
    private static class ConditionContextImpl implements ConditionContext {

        private final  BeanDefinitionRegistry registry;

        private final  ConfigurableListableBeanFactory beanFactory;

        private final Environment environment;

        private final ResourceLoader resourceLoader;

        private final  ClassLoader classLoader;

        public ConditionContextImpl( BeanDefinitionRegistry registry,
                                     Environment environment,  ResourceLoader resourceLoader) {

            this.registry = registry;
            this.beanFactory = deduceBeanFactory(registry);
            this.environment = (environment != null ? environment : deduceEnvironment(registry));
            this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
            this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
        }

        private static  ConfigurableListableBeanFactory deduceBeanFactory( BeanDefinitionRegistry source) {
            if (source instanceof ConfigurableListableBeanFactory configurableListableBeanFactory) {
                return configurableListableBeanFactory;
            }
            if (source instanceof ConfigurableApplicationContext configurableApplicationContext) {
                return configurableApplicationContext.getBeanFactory();
            }
            return null;
        }

        private static Environment deduceEnvironment( BeanDefinitionRegistry source) {
            if (source instanceof EnvironmentCapable environmentCapable) {
                return environmentCapable.getEnvironment();
            }
            return new StandardEnvironment();
        }

        private static ResourceLoader deduceResourceLoader( BeanDefinitionRegistry source) {
            if (source instanceof ResourceLoader resourceLoader) {
                return resourceLoader;
            }
            return new DefaultResourceLoader();
        }

        private static  ClassLoader deduceClassLoader( ResourceLoader resourceLoader,
                                                                ConfigurableListableBeanFactory beanFactory) {

            if (resourceLoader != null) {
                ClassLoader classLoader = resourceLoader.getClassLoader();
                if (classLoader != null) {
                    return classLoader;
                }
            }
            if (beanFactory != null) {
                return beanFactory.getBeanClassLoader();
            }
            return ClassUtils.getDefaultClassLoader();
        }

        @Override
        public BeanDefinitionRegistry getRegistry() {
            Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
            return this.registry;
        }

        @Override
        public  ConfigurableListableBeanFactory getBeanFactory() {
            return this.beanFactory;
        }

        @Override
        public Environment getEnvironment() {
            return this.environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return this.resourceLoader;
        }

        @Override
        public  ClassLoader getClassLoader() {
            return this.classLoader;
        }
    }
}
