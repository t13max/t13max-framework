package com.t13max.ioc.core.type;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;



import com.t13max.ioc.core.annotation.AnnotationAttributes;
import com.t13max.ioc.core.annotation.MergedAnnotation;
import com.t13max.ioc.core.annotation.MergedAnnotation.Adapt;
import com.t13max.ioc.core.annotation.MergedAnnotationCollectors;
import com.t13max.ioc.core.annotation.MergedAnnotationPredicates;
import com.t13max.ioc.core.annotation.MergedAnnotationSelectors;
import com.t13max.ioc.core.annotation.MergedAnnotations;
import com.t13max.ioc.util.MultiValueMap;

public interface AnnotatedTypeMetadata {

	MergedAnnotations getAnnotations();

	default boolean isAnnotated(String annotationName) {
		return getAnnotations().isPresent(annotationName);
	}

	default  Map<String,  Object> getAnnotationAttributes(String annotationName) {
		return getAnnotationAttributes(annotationName, false);
	}

	default  Map<String,  Object> getAnnotationAttributes(String annotationName,
			boolean classValuesAsString) {

		MergedAnnotation<Annotation> annotation = getAnnotations().get(annotationName,
				null, MergedAnnotationSelectors.firstDirectlyDeclared());
		if (!annotation.isPresent()) {
			return null;
		}
		return annotation.asAnnotationAttributes(Adapt.values(classValuesAsString, true));
	}

	default  MultiValueMap<String,  Object> getAllAnnotationAttributes(String annotationName) {
		return getAllAnnotationAttributes(annotationName, false);
	}

	default  MultiValueMap<String,  Object> getAllAnnotationAttributes(
			String annotationName, boolean classValuesAsString) {

		Adapt[] adaptations = Adapt.values(classValuesAsString, true);
		return getAnnotations().stream(annotationName)
				.filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
				.map(MergedAnnotation::withNonMergedAttributes)
				.collect(MergedAnnotationCollectors.toMultiValueMap(
						(MultiValueMap<String,  Object> map) -> (map.isEmpty() ? null : map), adaptations));
	}

	default Set<AnnotationAttributes> getMergedRepeatableAnnotationAttributes(
			Class<? extends Annotation> annotationType, Class<? extends Annotation> containerType,
			boolean classValuesAsString) {

		return getMergedRepeatableAnnotationAttributes(annotationType, containerType, classValuesAsString, false);
	}

	default Set<AnnotationAttributes> getMergedRepeatableAnnotationAttributes(
			Class<? extends Annotation> annotationType, Class<? extends Annotation> containerType,
			boolean classValuesAsString, boolean sortByReversedMetaDistance) {

		return getMergedRepeatableAnnotationAttributes(annotationType, containerType,
				mergedAnnotation -> true, classValuesAsString, sortByReversedMetaDistance);
	}

	default Set<AnnotationAttributes> getMergedRepeatableAnnotationAttributes(
			Class<? extends Annotation> annotationType, Class<? extends Annotation> containerType,
			Predicate<MergedAnnotation<? extends Annotation>> predicate, boolean classValuesAsString,
			boolean sortByReversedMetaDistance) {

		Stream<MergedAnnotation<Annotation>> stream = getAnnotations().stream()
				.filter(predicate)
				.filter(MergedAnnotationPredicates.typeIn(containerType, annotationType));

		if (sortByReversedMetaDistance) {
			stream = stream.sorted(reversedMetaDistance());
		}

		Adapt[] adaptations = Adapt.values(classValuesAsString, true);
		return stream
				.map(annotation -> annotation.asAnnotationAttributes(adaptations))
				.flatMap(attributes -> {
					if (containerType.equals(attributes.annotationType())) {
						return Stream.of(attributes.getAnnotationArray(MergedAnnotation.VALUE));
					}
					return Stream.of(attributes);
				})
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}


	private static Comparator<MergedAnnotation<Annotation>> reversedMetaDistance() {
		return Comparator.<MergedAnnotation<Annotation>> comparingInt(MergedAnnotation::getDistance).reversed();
	}

}
