package com.t13max.ioc.core.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;



import com.t13max.ioc.core.annotation.MergedAnnotation.Adapt;
import com.t13max.ioc.util.LinkedMultiValueMap;
import com.t13max.ioc.util.MultiValueMap;

public abstract class MergedAnnotationCollectors {

	private static final Characteristics[] NO_CHARACTERISTICS = {};

	private static final Characteristics[] IDENTITY_FINISH_CHARACTERISTICS = {Characteristics.IDENTITY_FINISH};


	private MergedAnnotationCollectors() {
	}

	
	public static <A extends Annotation> Collector<MergedAnnotation<A>, ?, Set<A>> toAnnotationSet() {
		return Collector.of(LinkedHashSet::new, (set, annotation) -> set.add(annotation.synthesize()),
				MergedAnnotationCollectors::combiner);
	}
	
	public static <A extends Annotation> Collector<MergedAnnotation<A>, ?, Annotation[]> toAnnotationArray() {
		return toAnnotationArray(Annotation[]::new);
	}
	
	public static <R extends Annotation, A extends R> Collector<MergedAnnotation<A>, ?, R[]> toAnnotationArray(
			IntFunction<R[]> generator) {

		return Collector.of(ArrayList::new, (list, annotation) -> list.add(annotation.synthesize()),
				MergedAnnotationCollectors::combiner, list -> list.toArray(generator.apply(list.size())));
	}
	
	public static <A extends Annotation> Collector<MergedAnnotation<A>, ? extends  Object,  MultiValueMap<String,  Object>> toMultiValueMap(
			Adapt... adaptations) {

		return toMultiValueMap((MultiValueMap<String,  Object> t) -> t, adaptations);
	}
	
	public static <A extends Annotation> Collector<MergedAnnotation<A>, ? extends  Object,  MultiValueMap<String,  Object>> toMultiValueMap(
			Function<MultiValueMap<String,  Object>,  MultiValueMap<String,  Object>> finisher,
			Adapt... adaptations) {

		Characteristics[] characteristics = (isSameInstance(finisher, Function.identity()) ?
				IDENTITY_FINISH_CHARACTERISTICS : NO_CHARACTERISTICS);
		return Collector.of(LinkedMultiValueMap::new,
				(MultiValueMap<String,  Object> map, MergedAnnotation<A> annotation) -> annotation.asMap(adaptations).forEach(map::add),
				MergedAnnotationCollectors::combiner, finisher, characteristics);
	}


	private static boolean isSameInstance(Object instance, Object candidate) {
		return instance == candidate;
	}
	
	private static <E, C extends Collection<E>> C combiner(C collection, C additions) {
		collection.addAll(additions);
		return collection;
	}
	
	private static <K, V> MultiValueMap<K,  V> combiner(MultiValueMap<K,  V> map, MultiValueMap<K,  V> additions) {
		map.addAll(additions);
		return map;
	}

}
