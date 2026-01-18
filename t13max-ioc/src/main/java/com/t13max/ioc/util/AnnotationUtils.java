package com.t13max.ioc.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * @Author: t13max
 * @Since: 22:18 2026/1/15
 */
public class AnnotationUtils {    public static final String VALUE = MergedAnnotation.VALUE;

    private static final AnnotationFilter JAVA_LANG_ANNOTATION_FILTER = AnnotationFilter.packages("java.lang.annotation");

    private static final Map<Class<? extends Annotation>, Map<String, DefaultValueHolder>> defaultValuesCache = new ConcurrentReferenceHashMap<>();    public static boolean isCandidateClass(Class<?> clazz, Collection<Class<? extends Annotation>> annotationTypes) {
        for (Class<? extends Annotation> annotationType : annotationTypes) {
            if (isCandidateClass(clazz, annotationType)) {
                return true;
            }
        }
        return false;
    }    public static boolean isCandidateClass(Class<?> clazz,  Class<? extends Annotation> annotationType) {
        return (annotationType != null && isCandidateClass(clazz, annotationType.getName()));
    }    public static boolean isCandidateClass(Class<?> clazz, String annotationName) {
        if (annotationName.startsWith("java.")) {
            return true;
        }
        if (AnnotationsScanner.hasPlainJavaAnnotationsOnly(clazz)) {
            return false;
        }
        return true;
    }    @SuppressWarnings("unchecked")
    public static <A extends Annotation>  A getAnnotation(Annotation annotation, Class<A> annotationType) {
        // Shortcut: directly present on the element, with no merging needed?
        if (annotationType.isInstance(annotation)) {
            return synthesizeAnnotation((A) annotation, annotationType);
        }
        // Shortcut: no searchable annotations to be found on plain Java classes and core Spring types...
        if (AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotation)) {
            return null;
        }
        // Exhaustive retrieval of merged annotations...
        return MergedAnnotations.from(annotation, new Annotation[] {annotation}, RepeatableContainers.none())
                .get(annotationType).withNonMergedAttributes()
                .synthesize(AnnotationUtils::isSingleLevelPresent).orElse(null);
    }    public static <A extends Annotation>  A getAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
        // Shortcut: directly present on the element, with no merging needed?
        if (AnnotationFilter.PLAIN.matches(annotationType) ||
                AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)) {
            return annotatedElement.getAnnotation(annotationType);
        }
        // Exhaustive retrieval of merged annotations...
        return MergedAnnotations.from(annotatedElement, SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
                .get(annotationType).withNonMergedAttributes()
                .synthesize(AnnotationUtils::isSingleLevelPresent).orElse(null);
    }

    private static <A extends Annotation> boolean isSingleLevelPresent(MergedAnnotation<A> mergedAnnotation) {
        int distance = mergedAnnotation.getDistance();
        return (distance == 0 || distance == 1);
    }    public static <A extends Annotation>  A getAnnotation(Method method, Class<A> annotationType) {
        Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
        return getAnnotation((AnnotatedElement) resolvedMethod, annotationType);
    }    @Deprecated(since = "5.2")
    public static Annotation  [] getAnnotations(AnnotatedElement annotatedElement) {
        try {
            return synthesizeAnnotationArray(annotatedElement.getAnnotations(), annotatedElement);
        }
        catch (Throwable ex) {
            handleIntrospectionFailure(annotatedElement, ex);
            return null;
        }
    }    @Deprecated(since = "5.2")
    public static Annotation  [] getAnnotations(Method method) {
        try {
            return synthesizeAnnotationArray(BridgeMethodResolver.findBridgedMethod(method).getAnnotations(), method);
        }
        catch (Throwable ex) {
            handleIntrospectionFailure(method, ex);
            return null;
        }
    }    @Deprecated(since = "5.2")
    public static <A extends Annotation> Set<A> getRepeatableAnnotations(AnnotatedElement annotatedElement,
                                                                         Class<A> annotationType) {

        return getRepeatableAnnotations(annotatedElement, annotationType, null);
    }    @Deprecated(since = "5.2")
    public static <A extends Annotation> Set<A> getRepeatableAnnotations(AnnotatedElement annotatedElement,
                                                                         Class<A> annotationType,  Class<? extends Annotation> containerAnnotationType) {

        RepeatableContainers repeatableContainers = (containerAnnotationType != null ?
                RepeatableContainers.explicitRepeatable(annotationType, containerAnnotationType) :
                RepeatableContainers.standardRepeatables());

        return MergedAnnotations.from(annotatedElement, SearchStrategy.SUPERCLASS, repeatableContainers)
                .stream(annotationType)
                .filter(MergedAnnotationPredicates.firstRunOf(MergedAnnotation::getAggregateIndex))
                .map(MergedAnnotation::withNonMergedAttributes)
                .collect(MergedAnnotationCollectors.toAnnotationSet());
    }    @Deprecated(since = "5.2")
    public static <A extends Annotation> Set<A> getDeclaredRepeatableAnnotations(AnnotatedElement annotatedElement,
                                                                                 Class<A> annotationType) {

        return getDeclaredRepeatableAnnotations(annotatedElement, annotationType, null);
    }    @Deprecated(since = "5.2")
    public static <A extends Annotation> Set<A> getDeclaredRepeatableAnnotations(AnnotatedElement annotatedElement,
                                                                                 Class<A> annotationType,  Class<? extends Annotation> containerAnnotationType) {

        RepeatableContainers repeatableContainers = containerAnnotationType != null ?
                RepeatableContainers.explicitRepeatable(annotationType, containerAnnotationType) :
                RepeatableContainers.standardRepeatables();

        return MergedAnnotations.from(annotatedElement, SearchStrategy.DIRECT, repeatableContainers)
                .stream(annotationType)
                .map(MergedAnnotation::withNonMergedAttributes)
                .collect(MergedAnnotationCollectors.toAnnotationSet());
    }    public static <A extends Annotation>  A findAnnotation(
            AnnotatedElement annotatedElement,  Class<A> annotationType) {

        if (annotationType == null) {
            return null;
        }

        // Shortcut: directly present on the element, with no merging needed?
        if (AnnotationFilter.PLAIN.matches(annotationType) ||
                AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)) {
            return annotatedElement.getDeclaredAnnotation(annotationType);
        }

        // Exhaustive retrieval of merged annotations...
        return MergedAnnotations.from(annotatedElement, SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
                .get(annotationType).withNonMergedAttributes()
                .synthesize(MergedAnnotation::isPresent).orElse(null);
    }    public static <A extends Annotation>  A findAnnotation(Method method,  Class<A> annotationType) {
        if (annotationType == null) {
            return null;
        }

        // Shortcut: directly present on the element, with no merging needed?
        if (AnnotationFilter.PLAIN.matches(annotationType) ||
                AnnotationsScanner.hasPlainJavaAnnotationsOnly(method)) {
            return method.getDeclaredAnnotation(annotationType);
        }

        // Exhaustive retrieval of merged annotations...
        return MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none())
                .get(annotationType).withNonMergedAttributes()
                .synthesize(MergedAnnotation::isPresent).orElse(null);
    }    public static <A extends Annotation>  A findAnnotation(Class<?> clazz,  Class<A> annotationType) {
        if (annotationType == null) {
            return null;
        }

        // Shortcut: directly present on the element, with no merging needed?
        if (AnnotationFilter.PLAIN.matches(annotationType) ||
                AnnotationsScanner.hasPlainJavaAnnotationsOnly(clazz)) {
            A annotation = clazz.getDeclaredAnnotation(annotationType);
            if (annotation != null) {
                return annotation;
            }
            // For backwards compatibility, perform a superclass search with plain annotations
            // even if not marked as @Inherited: for example, a findAnnotation search for @Deprecated
            Class<?> superclass = clazz.getSuperclass();
            if (superclass == null || superclass == Object.class) {
                return null;
            }
            return findAnnotation(superclass, annotationType);
        }

        // Exhaustive retrieval of merged annotations...
        return MergedAnnotations.from(clazz, SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none())
                .get(annotationType).withNonMergedAttributes()
                .synthesize(MergedAnnotation::isPresent).orElse(null);
    }    @Deprecated(since = "5.2")
    public static  Class<?> findAnnotationDeclaringClass(
            Class<? extends Annotation> annotationType,  Class<?> clazz) {

        if (clazz == null) {
            return null;
        }

        return (Class<?>) MergedAnnotations.from(clazz, SearchStrategy.SUPERCLASS)
                .get(annotationType, MergedAnnotation::isDirectlyPresent)
                .getSource();
    }    @Deprecated(since = "5.2")
    public static  Class<?> findAnnotationDeclaringClassForTypes(
            List<Class<? extends Annotation>> annotationTypes,  Class<?> clazz) {

        if (clazz == null) {
            return null;
        }

        MergedAnnotation<?> merged = MergedAnnotations.from(clazz, SearchStrategy.SUPERCLASS).stream()
                .filter(MergedAnnotationPredicates.typeIn(annotationTypes).and(MergedAnnotation::isDirectlyPresent))
                .findFirst().orElse(null);
        return (merged != null && merged.getSource() instanceof Class<?> sourceClass ? sourceClass : null);
    }    public static boolean isAnnotationDeclaredLocally(Class<? extends Annotation> annotationType, Class<?> clazz) {
        return MergedAnnotations.from(clazz).get(annotationType).isDirectlyPresent();
    }    @Deprecated(since = "5.2")
    public static boolean isAnnotationInherited(Class<? extends Annotation> annotationType, Class<?> clazz) {
        return MergedAnnotations.from(clazz, SearchStrategy.INHERITED_ANNOTATIONS)
                .stream(annotationType)
                .filter(MergedAnnotation::isDirectlyPresent)
                .findFirst().orElseGet(MergedAnnotation::missing)
                .getAggregateIndex() > 0;
    }    @Deprecated(since = "5.2")
    public static boolean isAnnotationMetaPresent(Class<? extends Annotation> annotationType,
                                                   Class<? extends Annotation> metaAnnotationType) {

        if (metaAnnotationType == null) {
            return false;
        }
        // Shortcut: directly present on the element, with no merging needed?
        if (AnnotationFilter.PLAIN.matches(metaAnnotationType) ||
                AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotationType)) {
            return annotationType.isAnnotationPresent(metaAnnotationType);
        }
        // Exhaustive retrieval of merged annotations...
        return MergedAnnotations.from(annotationType, SearchStrategy.INHERITED_ANNOTATIONS,
                RepeatableContainers.none()).isPresent(metaAnnotationType);
    }    public static boolean isInJavaLangAnnotationPackage( Annotation annotation) {
        return (annotation != null && JAVA_LANG_ANNOTATION_FILTER.matches(annotation));
    }    public static boolean isInJavaLangAnnotationPackage( String annotationType) {
        return (annotationType != null && JAVA_LANG_ANNOTATION_FILTER.matches(annotationType));
    }    public static void validateAnnotation(Annotation annotation) {
        AttributeMethods.forAnnotationType(annotation.annotationType()).validate(annotation);
    }    public static Map<String,  Object> getAnnotationAttributes(Annotation annotation) {
        return getAnnotationAttributes(null, annotation);
    }    public static Map<String,  Object> getAnnotationAttributes(
            Annotation annotation, boolean classValuesAsString) {

        return getAnnotationAttributes(annotation, classValuesAsString, false);
    }    public static AnnotationAttributes getAnnotationAttributes(
            Annotation annotation, boolean classValuesAsString, boolean nestedAnnotationsAsMap) {

        return getAnnotationAttributes(null, annotation, classValuesAsString, nestedAnnotationsAsMap);
    }    public static AnnotationAttributes getAnnotationAttributes(
             AnnotatedElement annotatedElement, Annotation annotation) {

        return getAnnotationAttributes(annotatedElement, annotation, false, false);
    }    public static AnnotationAttributes getAnnotationAttributes(
             AnnotatedElement annotatedElement, Annotation annotation,
            boolean classValuesAsString, boolean nestedAnnotationsAsMap) {

        Adapt[] adaptations = Adapt.values(classValuesAsString, nestedAnnotationsAsMap);
        return MergedAnnotation.from(annotatedElement, annotation)
                .withNonMergedAttributes()
                .asMap(mergedAnnotation ->
                        new AnnotationAttributes(mergedAnnotation.getType(), true), adaptations);
    }    public static void registerDefaultValues(AnnotationAttributes attributes) {
        Class<? extends Annotation> annotationType = attributes.annotationType();
        if (annotationType != null && Modifier.isPublic(annotationType.getModifiers()) &&
                !AnnotationFilter.PLAIN.matches(annotationType)) {
            Map<String, DefaultValueHolder> defaultValues = getDefaultValues(annotationType);
            defaultValues.forEach(attributes::putIfAbsent);
        }
    }

    private static Map<String, DefaultValueHolder> getDefaultValues(
            Class<? extends Annotation> annotationType) {

        return defaultValuesCache.computeIfAbsent(annotationType,
                AnnotationUtils::computeDefaultValues);
    }

    private static Map<String, DefaultValueHolder> computeDefaultValues(
            Class<? extends Annotation> annotationType) {

        AttributeMethods methods = AttributeMethods.forAnnotationType(annotationType);
        if (!methods.hasDefaultValueMethod()) {
            return Collections.emptyMap();
        }
        Map<String, DefaultValueHolder> result = CollectionUtils.newLinkedHashMap(methods.size());
        if (!methods.hasNestedAnnotation()) {
            // Use simpler method if there are no nested annotations
            for (int i = 0; i < methods.size(); i++) {
                Method method = methods.get(i);
                Object defaultValue = method.getDefaultValue();
                if (defaultValue != null) {
                    result.put(method.getName(), new DefaultValueHolder(defaultValue));
                }
            }
        }
        else {
            // If we have nested annotations, we need them as nested maps
            AnnotationAttributes attributes = MergedAnnotation.of(annotationType)
                    .asMap(annotation ->
                            new AnnotationAttributes(annotation.getType(), true), Adapt.ANNOTATION_TO_MAP);
            for (Map.Entry<String, Object> element : attributes.entrySet()) {
                result.put(element.getKey(), new DefaultValueHolder(element.getValue()));
            }
        }
        return result;
    }    public static void postProcessAnnotationAttributes( Object annotatedElement,
                                                        AnnotationAttributes attributes, boolean classValuesAsString) {

        if (attributes == null) {
            return;
        }
        if (!attributes.validated) {
            Class<? extends Annotation> annotationType = attributes.annotationType();
            if (annotationType == null) {
                return;
            }
            AnnotationTypeMapping mapping = AnnotationTypeMappings.forAnnotationType(annotationType).get(0);
            for (int i = 0; i < mapping.getMirrorSets().size(); i++) {
                MirrorSet mirrorSet = mapping.getMirrorSets().get(i);
                int resolved = mirrorSet.resolve(attributes.displayName, attributes,
                        AnnotationUtils::getAttributeValueForMirrorResolution);
                if (resolved != -1) {
                    Method attribute = mapping.getAttributes().get(resolved);
                    Object value = attributes.get(attribute.getName());
                    for (int j = 0; j < mirrorSet.size(); j++) {
                        Method mirror = mirrorSet.get(j);
                        if (mirror != attribute) {
                            attributes.put(mirror.getName(),
                                    adaptValue(annotatedElement, value, classValuesAsString));
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, Object> attributeEntry : attributes.entrySet()) {
            String attributeName = attributeEntry.getKey();
            Object value = attributeEntry.getValue();
            if (value instanceof DefaultValueHolder defaultValueHolder) {
                value = defaultValueHolder.defaultValue;
                attributes.put(attributeName,
                        adaptValue(annotatedElement, value, classValuesAsString));
            }
        }
    }

    private static  Object getAttributeValueForMirrorResolution(Method attribute,  Object attributes) {
        if (!(attributes instanceof AnnotationAttributes annotationAttributes)) {
            return null;
        }
        Object result = annotationAttributes.get(attribute.getName());
        return (result instanceof DefaultValueHolder defaultValueHolder ? defaultValueHolder.defaultValue : result);
    }

    private static  Object adaptValue(
             Object annotatedElement,  Object value, boolean classValuesAsString) {

        if (classValuesAsString) {
            if (value instanceof Class<?> clazz) {
                return clazz.getName();
            }
            if (value instanceof Class<?>[] classes) {
                String[] names = new String[classes.length];
                for (int i = 0; i < classes.length; i++) {
                    names[i] = classes[i].getName();
                }
                return names;
            }
        }
        if (value instanceof Annotation annotation) {
            return MergedAnnotation.from(annotatedElement, annotation).synthesize();
        }
        if (value instanceof Annotation[] annotations) {
            Annotation[] synthesized = (Annotation[]) Array.newInstance(
                    annotations.getClass().componentType(), annotations.length);
            for (int i = 0; i < annotations.length; i++) {
                synthesized[i] = MergedAnnotation.from(annotatedElement, annotations[i]).synthesize();
            }
            return synthesized;
        }
        return value;
    }    public static  Object getValue(Annotation annotation) {
        return getValue(annotation, VALUE);
    }    public static  Object getValue( Annotation annotation,  String attributeName) {
        if (annotation == null || !StringUtils.hasText(attributeName)) {
            return null;
        }
        try {
            for (Method method : annotation.annotationType().getDeclaredMethods()) {
                if (method.getName().equals(attributeName) && method.getParameterCount() == 0) {
                    return invokeAnnotationMethod(method, annotation);
                }
            }
        }
        catch (Throwable ex) {
            handleValueRetrievalFailure(annotation, ex);
        }
        return null;
    }    static  Object invokeAnnotationMethod(Method method,  Object annotation) {
        if (annotation == null) {
            return null;
        }
        if (Proxy.isProxyClass(annotation.getClass())) {
            try {
                InvocationHandler handler = Proxy.getInvocationHandler(annotation);
                return handler.invoke(annotation, method, null);
            }
            catch (Throwable ex) {
                // Ignore and fall back to reflection below
            }
        }
        return ReflectionUtils.invokeMethod(method, annotation);
    }    static void rethrowAnnotationConfigurationException(Throwable ex) {
        if (ex instanceof AnnotationConfigurationException exception) {
            throw exception;
        }
    }    static void handleIntrospectionFailure(AnnotatedElement element, Throwable ex) {
        rethrowAnnotationConfigurationException(ex);
        IntrospectionFailureLogger logger = IntrospectionFailureLogger.INFO;
        boolean meta = false;
        if (element instanceof Class<?> clazz && Annotation.class.isAssignableFrom(clazz)) {
            // Meta-annotation introspection failure
            logger = IntrospectionFailureLogger.DEBUG;
            meta = true;
        }
        if (logger.isEnabled()) {
            logger.log("Failed to " + (meta ? "meta-introspect annotation " : "introspect annotations on ") +
                    element + ": " + ex);
        }
    }    private static void handleValueRetrievalFailure(Annotation annotation, Throwable ex) {
        rethrowAnnotationConfigurationException(ex);
        IntrospectionFailureLogger logger = IntrospectionFailureLogger.INFO;
        if (logger.isEnabled()) {
            logger.log("Failed to retrieve value from " + annotation + ": " + ex);
        }
    }    public static  Object getDefaultValue(Annotation annotation) {
        return getDefaultValue(annotation, VALUE);
    }    public static  Object getDefaultValue( Annotation annotation,  String attributeName) {
        return (annotation != null ? getDefaultValue(annotation.annotationType(), attributeName) : null);
    }    public static  Object getDefaultValue(Class<? extends Annotation> annotationType) {
        return getDefaultValue(annotationType, VALUE);
    }    public static  Object getDefaultValue(
             Class<? extends Annotation> annotationType,  String attributeName) {

        if (annotationType == null || !StringUtils.hasText(attributeName)) {
            return null;
        }
        return MergedAnnotation.of(annotationType).getDefaultValue(attributeName).orElse(null);
    }    public static <A extends Annotation> A synthesizeAnnotation(
            A annotation,  AnnotatedElement annotatedElement) {

        if (isSynthesizedAnnotation(annotation) || AnnotationFilter.PLAIN.matches(annotation)) {
            return annotation;
        }
        return MergedAnnotation.from(annotatedElement, annotation).synthesize();
    }    public static <A extends Annotation> A synthesizeAnnotation(Class<A> annotationType) {
        return synthesizeAnnotation(Collections.emptyMap(), annotationType, null);
    }    public static <A extends Annotation> A synthesizeAnnotation(Map<String, Object> attributes,
                                                                Class<A> annotationType,  AnnotatedElement annotatedElement) {

        try {
            return MergedAnnotation.of(annotatedElement, annotationType, attributes).synthesize();
        }
        catch (NoSuchElementException | IllegalStateException ex) {
            throw new IllegalArgumentException(ex);
        }
    }    static Annotation[] synthesizeAnnotationArray(Annotation[] annotations, AnnotatedElement annotatedElement) {
        if (AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)) {
            return annotations;
        }
        Annotation[] synthesized = (Annotation[]) Array.newInstance(
                annotations.getClass().componentType(), annotations.length);
        for (int i = 0; i < annotations.length; i++) {
            synthesized[i] = synthesizeAnnotation(annotations[i], annotatedElement);
        }
        return synthesized;
    }    public static boolean isSynthesizedAnnotation( Annotation annotation) {
        try {
            return (annotation != null && Proxy.isProxyClass(annotation.getClass()) &&
                    Proxy.getInvocationHandler(annotation) instanceof SynthesizedMergedAnnotationInvocationHandler);
        }
        catch (SecurityException ex) {
            // Security settings disallow reflective access to the InvocationHandler:
            // assume the annotation has not been synthesized by Spring.
            return false;
        }
    }    public static void clearCache() {
        AnnotationTypeMappings.clearCache();
        AnnotationsScanner.clearCache();
        AttributeMethods.cache.clear();
        RepeatableContainers.cache.clear();
        OrderUtils.orderCache.clear();
    }
    private static class DefaultValueHolder {

        final Object defaultValue;

        public DefaultValueHolder(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public String toString() {
            return "*" + this.defaultValue;
        }
    }

}
