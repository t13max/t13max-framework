package com.t13max.ioc.core.annotation;

import java.lang.annotation.Annotation;
import java.util.function.Predicate;

public abstract class MergedAnnotationSelectors {

	private static final MergedAnnotationSelector<?> NEAREST = new Nearest();

	private static final MergedAnnotationSelector<?> FIRST_DIRECTLY_DECLARED = new FirstDirectlyDeclared();


	private MergedAnnotationSelectors() {
	}

	
	@SuppressWarnings("unchecked")
	public static <A extends Annotation> MergedAnnotationSelector<A> nearest() {
		return (MergedAnnotationSelector<A>) NEAREST;
	}
	
	@SuppressWarnings("unchecked")
	public static <A extends Annotation> MergedAnnotationSelector<A> firstDirectlyDeclared() {
		return (MergedAnnotationSelector<A>) FIRST_DIRECTLY_DECLARED;
	}

	
	private static class Nearest implements MergedAnnotationSelector<Annotation> {

		@Override
		public boolean isBestCandidate(MergedAnnotation<Annotation> annotation) {
			return annotation.getDistance() == 0;
		}

		@Override
		public MergedAnnotation<Annotation> select(
				MergedAnnotation<Annotation> existing, MergedAnnotation<Annotation> candidate) {

			if (candidate.getDistance() < existing.getDistance()) {
				return candidate;
			}
			return existing;
		}

	}

	
	private static class FirstDirectlyDeclared implements MergedAnnotationSelector<Annotation> {

		@Override
		public boolean isBestCandidate(MergedAnnotation<Annotation> annotation) {
			return annotation.getDistance() == 0;
		}

		@Override
		public MergedAnnotation<Annotation> select(
				MergedAnnotation<Annotation> existing, MergedAnnotation<Annotation> candidate) {

			if (existing.getDistance() > 0 && candidate.getDistance() == 0) {
				return candidate;
			}
			return existing;
		}

	}

}
