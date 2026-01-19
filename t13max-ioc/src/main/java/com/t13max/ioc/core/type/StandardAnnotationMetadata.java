package com.t13max.ioc.core.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;



import com.t13max.ioc.core.annotation.AnnotatedElementUtils;
import com.t13max.ioc.core.annotation.AnnotationUtils;
import com.t13max.ioc.core.annotation.MergedAnnotation;
import com.t13max.ioc.core.annotation.MergedAnnotations;
import com.t13max.ioc.core.annotation.MergedAnnotations.SearchStrategy;
import com.t13max.ioc.core.annotation.RepeatableContainers;
import com.t13max.ioc.util.MultiValueMap;
import com.t13max.ioc.util.ReflectionUtils;

public class StandardAnnotationMetadata extends StandardClassMetadata implements AnnotationMetadata {

	private final MergedAnnotations mergedAnnotations;

	private final boolean nestedAnnotationsAsMap;

	private  Set<String> annotationTypes;

	
	@Deprecated(since = "5.2")
	public StandardAnnotationMetadata(Class<?> introspectedClass) {
		this(introspectedClass, false);
	}
	
	@Deprecated(since = "5.2")
	public StandardAnnotationMetadata(Class<?> introspectedClass, boolean nestedAnnotationsAsMap) {
		super(introspectedClass);
		this.mergedAnnotations = MergedAnnotations.from(introspectedClass,
				SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none());
		this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
	}


	@Override
	public MergedAnnotations getAnnotations() {
		return this.mergedAnnotations;
	}

	@Override
	public Set<String> getAnnotationTypes() {
		Set<String> annotationTypes = this.annotationTypes;
		if (annotationTypes == null) {
			annotationTypes = Collections.unmodifiableSet(AnnotationMetadata.super.getAnnotationTypes());
			this.annotationTypes = annotationTypes;
		}
		return annotationTypes;
	}

	@Override
	public  Map<String,  Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		if (this.nestedAnnotationsAsMap) {
			return AnnotationMetadata.super.getAnnotationAttributes(annotationName, classValuesAsString);
		}
		return AnnotatedElementUtils.getMergedAnnotationAttributes(
				getIntrospectedClass(), annotationName, classValuesAsString, false);
	}

	@Override
	@SuppressWarnings("NullAway") // Null-safety of Java super method not yet managed
	public  MultiValueMap<String,  Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		if (this.nestedAnnotationsAsMap) {
			return AnnotationMetadata.super.getAllAnnotationAttributes(annotationName, classValuesAsString);
		}
		return AnnotatedElementUtils.getAllAnnotationAttributes(
				getIntrospectedClass(), annotationName, classValuesAsString, false);
	}

	@Override
	public boolean hasAnnotatedMethods(String annotationName) {
		if (AnnotationUtils.isCandidateClass(getIntrospectedClass(), annotationName)) {
			try {
				Method[] methods = ReflectionUtils.getDeclaredMethods(getIntrospectedClass());
				for (Method method : methods) {
					if (isAnnotatedMethod(method, annotationName)) {
						return true;
					}
				}
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex);
			}
		}
		return false;
	}

	@Override
	public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
		Set<MethodMetadata> result = new LinkedHashSet<>(4);
		if (AnnotationUtils.isCandidateClass(getIntrospectedClass(), annotationName)) {
			ReflectionUtils.doWithLocalMethods(getIntrospectedClass(), method -> {
				if (isAnnotatedMethod(method, annotationName)) {
					result.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap));
				}
			});
		}
		return result;
	}

	@Override
	public Set<MethodMetadata> getDeclaredMethods() {
		Set<MethodMetadata> result = new LinkedHashSet<>(16);
		ReflectionUtils.doWithLocalMethods(getIntrospectedClass(), method ->
				result.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap)));
		return result;
	}


	private static boolean isAnnotatedMethod(Method method, String annotationName) {
		return !method.isBridge() && method.getAnnotations().length > 0 &&
				AnnotatedElementUtils.isAnnotated(method, annotationName);
	}

	static AnnotationMetadata from(Class<?> introspectedClass) {
		return new StandardAnnotationMetadata(introspectedClass, true);
	}

}
