package com.t13max.ioc.core.annotation;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;



@SuppressWarnings("serial")
public final class AnnotatedElementAdapter implements AnnotatedElement, Serializable {

	private static final AnnotatedElementAdapter EMPTY = new AnnotatedElementAdapter(new Annotation[0]);

	
	public static AnnotatedElementAdapter from(Annotation  [] annotations) {
		if (annotations == null || annotations.length == 0) {
			return EMPTY;
		}
		return new AnnotatedElementAdapter(annotations);
	}


	private final Annotation[] annotations;


	private AnnotatedElementAdapter(Annotation[] annotations) {
		this.annotations = annotations;
	}


	@Override
	public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
		for (Annotation annotation : this.annotations) {
			if (annotation.annotationType() == annotationClass) {
				return true;
			}
		}
		return false;
	}

	@Override
	public <A extends Annotation>  A getAnnotation(Class<A> annotationClass) {
		for (Annotation annotation : this.annotations) {
			if (annotation.annotationType() == annotationClass) {
				return annotationClass.cast(annotation);
			}
		}
		return null;
	}

	@Override
	public Annotation[] getAnnotations() {
		return (isEmpty() ? this.annotations : this.annotations.clone());
	}

	@Override
	public <A extends Annotation>  A getDeclaredAnnotation(Class<A> annotationClass) {
		return getAnnotation(annotationClass);
	}

	@Override
	public Annotation[] getDeclaredAnnotations() {
		return getAnnotations();
	}
	
	public boolean isEmpty() {
		return (this == EMPTY);
	}

	@Override
	public boolean equals( Object other) {
		return (this == other || (other instanceof AnnotatedElementAdapter that &&
				Arrays.equals(this.annotations, that.annotations)));
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.annotations);
	}

	@Override
	public String toString() {
		return Arrays.toString(this.annotations);
	}

}
