package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.annotation.AnnotatedBeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.support.BeanDefinitionRegistry;
import com.t13max.ioc.beans.factory.support.BeanNameGenerator;
import com.t13max.ioc.core.annotation.AliasFor;
import com.t13max.ioc.core.annotation.AnnotationAttributes;
import com.t13max.ioc.core.annotation.MergedAnnotation;
import com.t13max.ioc.core.annotation.MergedAnnotations;
import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;
import com.t13max.ioc.core.type.AnnotationMetadata;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ReflectionUtils;
import com.t13max.ioc.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author: t13max
 * @Since: 7:56 2026/1/17
 */
public class AnnotationBeanNameGenerator implements BeanNameGenerator {

    /**
     * A convenient constant for a default {@code AnnotationBeanNameGenerator} instance,
     * as used for component scanning purposes.
     * @since 5.2
     */
    public static final AnnotationBeanNameGenerator INSTANCE = new AnnotationBeanNameGenerator();

    private static final String COMPONENT_ANNOTATION_CLASSNAME = "org.springframework.stereotype.Component";

    private static final MergedAnnotation.Adapt[] ADAPTATIONS = MergedAnnotation.Adapt.values(false, true);


    private static final Logger logger = LogManager.getLogger(AnnotationBeanNameGenerator.class);

    /**
     * Set used to track which stereotype annotations have already been checked
     * to see if they use a convention-based override for the {@code value}
     * attribute in {@code @Component}.
     * @since 6.1
     * @see #determineBeanNameFromAnnotation(AnnotatedBeanDefinition)
     */
    private static final Set<String> conventionBasedStereotypeCheckCache = ConcurrentHashMap.newKeySet();

    private final Map<String, Set<String>> metaAnnotationTypesCache = new ConcurrentHashMap<>();



    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        if (definition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
            String beanName = determineBeanNameFromAnnotation(annotatedBeanDefinition);
            if (StringUtils.hasText(beanName)) {
                // Explicit bean name found.
                return beanName;
            }
        }
        // Fallback: generate a unique default bean name.
        return buildDefaultBeanName(definition, registry);
    }

    /**
     * Derive a bean name from one of the annotations on the class.
     * @param annotatedDef the annotation-aware bean definition
     * @return the bean name, or {@code null} if none is found
     */
    protected  String determineBeanNameFromAnnotation(AnnotatedBeanDefinition annotatedDef) {
        AnnotationMetadata metadata = annotatedDef.getMetadata();

        String beanName = getExplicitBeanName(metadata);
        if (beanName != null) {
            return beanName;
        }

        // List of annotations directly present on the class we're searching on.
        // MergedAnnotation implementations do not implement equals()/hashCode(),
        // so we use a List and a 'visited' Set below.
        List<MergedAnnotation<Annotation>> mergedAnnotations = metadata.getAnnotations().stream()
                .filter(MergedAnnotation::isDirectlyPresent)
                .toList();

        Set<AnnotationAttributes> visited = new HashSet<>();

        for (MergedAnnotation<Annotation> mergedAnnotation : mergedAnnotations) {
            AnnotationAttributes attributes = mergedAnnotation.asAnnotationAttributes(ADAPTATIONS);
            if (visited.add(attributes)) {
                String annotationType = mergedAnnotation.getType().getName();
                Set<String> metaAnnotationTypes = this.metaAnnotationTypesCache.computeIfAbsent(annotationType,
                        key -> getMetaAnnotationTypes(mergedAnnotation));
                if (isStereotypeWithNameValue(annotationType, metaAnnotationTypes, attributes)) {
                    Object value = attributes.get(MergedAnnotation.VALUE);
                    if (value instanceof String currentName && !currentName.isBlank() &&
                            !hasExplicitlyAliasedValueAttribute(mergedAnnotation.getType())) {
                        if (conventionBasedStereotypeCheckCache.add(annotationType) &&
                                metaAnnotationTypes.contains(COMPONENT_ANNOTATION_CLASSNAME) && logger.isWarnEnabled()) {
                            logger.warn("""
									Support for convention-based @Component names is deprecated and will \
									be removed in a future version of the framework. Please annotate the \
									'value' attribute in @%s with @AliasFor(annotation=Component.class) \
									to declare an explicit alias for @Component's 'value' attribute."""
                                    .formatted(annotationType));
                        }
                        if (beanName != null && !currentName.equals(beanName)) {
                            throw new IllegalStateException("Stereotype annotations suggest inconsistent " +
                                    "component names: '" + beanName + "' versus '" + currentName + "'");
                        }
                        beanName = currentName;
                    }
                }
            }
        }
        return beanName;
    }

    private Set<String> getMetaAnnotationTypes(MergedAnnotation<Annotation> mergedAnnotation) {
        Set<String> result = MergedAnnotations.from(mergedAnnotation.getType()).stream()
                .map(metaAnnotation -> metaAnnotation.getType().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return (result.isEmpty() ? Collections.emptySet() : result);
    }

    private  String getExplicitBeanName(AnnotationMetadata metadata) {
        List<String> names = metadata.getAnnotations().stream(COMPONENT_ANNOTATION_CLASSNAME)
                .map(annotation -> annotation.getString(MergedAnnotation.VALUE))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() > 1) {
            throw new IllegalStateException(
                    "Stereotype annotations suggest inconsistent component names: " + names);
        }
        return null;
    }

    /**
     * Check whether the given annotation is a stereotype that is allowed
     * to suggest a component name through its {@code value()} attribute.
     * @param annotationType the name of the annotation class to check
     * @param metaAnnotationTypes the names of meta-annotations on the given annotation
     * @param attributes the map of attributes for the given annotation
     * @return whether the annotation qualifies as a stereotype with component name
     */
    protected boolean isStereotypeWithNameValue(String annotationType, Set<String> metaAnnotationTypes, Map<String, Object> attributes) {

        boolean isStereotype = metaAnnotationTypes.contains(COMPONENT_ANNOTATION_CLASSNAME) ||
                annotationType.equals("jakarta.inject.Named");

        return (isStereotype && attributes.containsKey(MergedAnnotation.VALUE));
    }

    /**
     * Derive a default bean name from the given bean definition.
     * <p>The default implementation delegates to {@link #buildDefaultBeanName(BeanDefinition)}.
     * @param definition the bean definition to build a bean name for
     * @param registry the registry that the given bean definition is being registered with
     * @return the default bean name (never {@code null})
     */
    protected String buildDefaultBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        return buildDefaultBeanName(definition);
    }

    /**
     * Derive a default bean name from the given bean definition.
     * <p>The default implementation simply builds a decapitalized version
     * of the short class name: for example, "mypackage.MyJdbcDao" &rarr; "myJdbcDao".
     * <p>Note that inner classes will thus have names of the form
     * "outerClassName.InnerClassName", which because of the period in the
     * name may be an issue if you are autowiring by name.
     * @param definition the bean definition to build a bean name for
     * @return the default bean name (never {@code null})
     */
    protected String buildDefaultBeanName(BeanDefinition definition) {
        String beanClassName = definition.getBeanClassName();
        Assert.state(beanClassName != null, "No bean class name set");
        String shortClassName = ClassUtils.getShortName(beanClassName);
        return StringUtils.uncapitalizeAsProperty(shortClassName);
    }

    /**
     * Determine if the supplied annotation type declares a {@code value()} attribute
     * with an explicit alias configured via {@link AliasFor @AliasFor}.
     * @since 6.2.3
     */
    private static boolean hasExplicitlyAliasedValueAttribute(Class<? extends Annotation> annotationType) {
        Method valueAttribute = ReflectionUtils.findMethod(annotationType, MergedAnnotation.VALUE);
        return (valueAttribute != null && valueAttribute.isAnnotationPresent(AliasFor.class));
    }

}
