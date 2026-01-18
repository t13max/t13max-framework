package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.BeanDefinitionStoreException;
import com.t13max.ioc.beans.factory.annotation.AnnotatedBeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.support.BeanDefinitionRegistry;
import com.t13max.ioc.context.ResourceLoaderAware;
import com.t13max.ioc.context.index.CandidateComponentsIndex;
import com.t13max.ioc.context.index.CandidateComponentsIndexLoader;
import com.t13max.ioc.core.SpringProperties;
import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.core.env.EnvironmentCapable;
import com.t13max.ioc.core.env.StandardEnvironment;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.io.support.PathMatchingResourcePatternResolver;
import com.t13max.ioc.core.io.support.ResourcePatternResolver;
import com.t13max.ioc.core.io.support.ResourcePatternUtils;
import com.t13max.ioc.core.type.AnnotationMetadata;
import com.t13max.ioc.core.type.classreading.CachingMetadataReaderFactory;
import com.t13max.ioc.core.type.classreading.ClassFormatException;
import com.t13max.ioc.core.type.classreading.MetadataReader;
import com.t13max.ioc.core.type.classreading.MetadataReaderFactory;
import com.t13max.ioc.core.type.filter.AnnotationTypeFilter;
import com.t13max.ioc.core.type.filter.AssignableTypeFilter;
import com.t13max.ioc.core.type.filter.TypeFilter;
import com.t13max.ioc.stereotype.Indexed;
import com.t13max.ioc.util.AnnotationUtils;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.List;

/**
 * @Author: t13max
 * @Since: 20:46 2026/1/16
 */
public class ClassPathScanningCandidateComponentProvider implements EnvironmentCapable, ResourceLoaderAware {

    static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";

    public static final String IGNORE_CLASSFORMAT_PROPERTY_NAME = "spring.classformat.ignore";

    private static final boolean shouldIgnoreClassFormatException = SpringProperties.getFlag(IGNORE_CLASSFORMAT_PROPERTY_NAME);

    protected final Logger logger = LogManager.getLogger(getClass());

    private String resourcePattern = DEFAULT_RESOURCE_PATTERN;

    private final List<TypeFilter> includeFilters = new ArrayList<>();

    private final List<TypeFilter> excludeFilters = new ArrayList<>();

    private Environment environment;

    private ConditionEvaluator conditionEvaluator;

    private ResourcePatternResolver resourcePatternResolver;

    private MetadataReaderFactory metadataReaderFactory;

    private CandidateComponentsIndex componentsIndex;

    protected ClassPathScanningCandidateComponentProvider() {
    }

    public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters) {
        this(useDefaultFilters, new StandardEnvironment());
    }

    public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters, Environment environment) {
        if (useDefaultFilters) {
            registerDefaultFilters();
        }
        setEnvironment(environment);
        setResourceLoader(null);
    }

    public void setResourcePattern(String resourcePattern) {
        Assert.notNull(resourcePattern, "'resourcePattern' must not be null");
        this.resourcePattern = resourcePattern;
    }

    public void addIncludeFilter(TypeFilter includeFilter) {
        this.includeFilters.add(includeFilter);
    }

    public void addExcludeFilter(TypeFilter excludeFilter) {
        this.excludeFilters.add(0, excludeFilter);
    }

    public void resetFilters(boolean useDefaultFilters) {
        this.includeFilters.clear();
        this.excludeFilters.clear();
        if (useDefaultFilters) {
            registerDefaultFilters();
        }
    }

    @SuppressWarnings("unchecked")
    protected void registerDefaultFilters() {
        this.includeFilters.add(new AnnotationTypeFilter(Component.class));
        ClassLoader cl = ClassPathScanningCandidateComponentProvider.class.getClassLoader();
        try {
            this.includeFilters.add(new AnnotationTypeFilter(((Class<? extends Annotation>) ClassUtils.forName("jakarta.inject.Named", cl)), false));
            logger.trace("JSR-330 'jakarta.inject.Named' annotation found and supported for component scanning");
        } catch (ClassNotFoundException ex) {
            // JSR-330 API (as included in Jakarta EE) not available - simply skip.
        }
    }

    public void setEnvironment(Environment environment) {
        Assert.notNull(environment, "Environment must not be null");
        this.environment = environment;
        this.conditionEvaluator = null;
    }

    @Override
    public final Environment getEnvironment() {
        if (this.environment == null) {
            this.environment = new StandardEnvironment();
        }
        return this.environment;
    }

    protected BeanDefinitionRegistry getRegistry() {
        return null;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
        this.componentsIndex = CandidateComponentsIndexLoader.loadIndex(this.resourcePatternResolver.getClassLoader());
    }

    public final ResourceLoader getResourceLoader() {
        return getResourcePatternResolver();
    }

    private ResourcePatternResolver getResourcePatternResolver() {
        if (this.resourcePatternResolver == null) {
            this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
        }
        return this.resourcePatternResolver;
    }

    public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
        this.metadataReaderFactory = metadataReaderFactory;
    }

    public final MetadataReaderFactory getMetadataReaderFactory() {
        if (this.metadataReaderFactory == null) {
            this.metadataReaderFactory = new CachingMetadataReaderFactory();
        }
        return this.metadataReaderFactory;
    }

    public Set<BeanDefinition> findCandidateComponents(String basePackage) {
        if (this.componentsIndex != null && indexSupportsIncludeFilters()) {
            return addCandidateComponentsFromIndex(this.componentsIndex, basePackage);
        } else {
            return scanCandidateComponents(basePackage);
        }
    }

    private boolean indexSupportsIncludeFilters() {
        for (TypeFilter includeFilter : this.includeFilters) {
            if (!indexSupportsIncludeFilter(includeFilter)) {
                return false;
            }
        }
        return true;
    }

    private boolean indexSupportsIncludeFilter(TypeFilter filter) {
        if (filter instanceof AnnotationTypeFilter annotationTypeFilter) {
            Class<? extends Annotation> annotationType = annotationTypeFilter.getAnnotationType();
            return (AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, annotationType) ||
                    annotationType.getName().startsWith("jakarta.") ||
                    annotationType.getName().startsWith("javax."));
        }
        if (filter instanceof AssignableTypeFilter assignableTypeFilter) {
            Class<?> target = assignableTypeFilter.getTargetType();
            return AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, target);
        }
        return false;
    }

    private String extractStereotype(TypeFilter filter) {
        if (filter instanceof AnnotationTypeFilter annotationTypeFilter) {
            return annotationTypeFilter.getAnnotationType().getName();
        }
        if (filter instanceof AssignableTypeFilter assignableTypeFilter) {
            return assignableTypeFilter.getTargetType().getName();
        }
        return null;
    }

    private Set<BeanDefinition> addCandidateComponentsFromIndex(CandidateComponentsIndex index, String basePackage) {
        Set<BeanDefinition> candidates = new LinkedHashSet<>();
        try {
            Set<String> types = new HashSet<>();
            for (TypeFilter filter : this.includeFilters) {
                String stereotype = extractStereotype(filter);
                if (stereotype == null) {
                    throw new IllegalArgumentException("Failed to extract stereotype from " + filter);
                }
                types.addAll(index.getCandidateTypes(basePackage, stereotype));
            }
            boolean traceEnabled = logger.isTraceEnabled();
            boolean debugEnabled = logger.isDebugEnabled();
            for (String type : types) {
                MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(type);
                if (isCandidateComponent(metadataReader)) {
                    ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
                    sbd.setSource(metadataReader.getResource());
                    if (isCandidateComponent(sbd)) {
                        if (debugEnabled) {
                            logger.debug("Using candidate component class from index: " + type);
                        }
                        candidates.add(sbd);
                    } else {
                        if (debugEnabled) {
                            logger.debug("Ignored because not a concrete top-level class: " + type);
                        }
                    }
                } else {
                    if (traceEnabled) {
                        logger.trace("Ignored because matching an exclude filter: " + type);
                    }
                }
            }
        } catch (IOException ex) {
            throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
        }
        return candidates;
    }

    private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
        Set<BeanDefinition> candidates = new LinkedHashSet<>();
        try {
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    resolveBasePackage(basePackage) + '/' + this.resourcePattern;
            Resource[] resources = getResourcePatternResolver().getResources(packageSearchPath);
            boolean traceEnabled = logger.isTraceEnabled();
            boolean debugEnabled = logger.isDebugEnabled();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
                    // Ignore CGLIB-generated classes in the classpath
                    continue;
                }
                if (traceEnabled) {
                    logger.trace("Scanning " + resource);
                }
                try {
                    MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
                    if (isCandidateComponent(metadataReader)) {
                        ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
                        sbd.setSource(resource);
                        if (isCandidateComponent(sbd)) {
                            if (debugEnabled) {
                                logger.debug("Identified candidate component class: " + resource);
                            }
                            candidates.add(sbd);
                        } else {
                            if (debugEnabled) {
                                logger.debug("Ignored because not a concrete top-level class: " + resource);
                            }
                        }
                    } else {
                        if (traceEnabled) {
                            logger.trace("Ignored because not matching any filter: " + resource);
                        }
                    }
                } catch (FileNotFoundException ex) {
                    if (traceEnabled) {
                        logger.trace("Ignored non-readable " + resource + ": " + ex.getMessage());
                    }
                } catch (ClassFormatException ex) {
                    if (shouldIgnoreClassFormatException) {
                        if (debugEnabled) {
                            logger.debug("Ignored incompatible class format in " + resource + ": " + ex.getMessage());
                        }
                    } else {
                        throw new BeanDefinitionStoreException("Incompatible class format in " + resource +
                                ": set system property 'spring.classformat.ignore' to 'true' " +
                                "if you mean to ignore such files during classpath scanning", ex);
                    }
                } catch (Throwable ex) {
                    throw new BeanDefinitionStoreException("Failed to read candidate component class: " + resource, ex);
                }
            }
        } catch (IOException ex) {
            throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
        }
        return candidates;
    }

    protected String resolveBasePackage(String basePackage) {
        return ClassUtils.convertClassNameToResourcePath(getEnvironment().resolveRequiredPlaceholders(basePackage));
    }

    protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
        for (TypeFilter tf : this.excludeFilters) {
            if (tf.match(metadataReader, getMetadataReaderFactory())) {
                return false;
            }
        }
        for (TypeFilter tf : this.includeFilters) {
            if (tf.match(metadataReader, getMetadataReaderFactory())) {
                return isConditionMatch(metadataReader);
            }
        }
        return false;
    }

    private boolean isConditionMatch(MetadataReader metadataReader) {
        if (this.conditionEvaluator == null) {
            this.conditionEvaluator =
                    new ConditionEvaluator(getRegistry(), this.environment, this.resourcePatternResolver);
        }
        return !this.conditionEvaluator.shouldSkip(metadataReader.getAnnotationMetadata());
    }

    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return (metadata.isIndependent() && (metadata.isConcrete() ||
                (metadata.isAbstract() && metadata.hasAnnotatedMethods(Lookup.class.getName()))));
    }

    public void clearCache() {
        if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory cmrf) {
            // Clear cache in externally provided MetadataReaderFactory; this is a no-op
            // for a shared cache since it'll be cleared by the ApplicationContext.
            cmrf.clearCache();
        }
    }
}
