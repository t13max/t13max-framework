package com.t13max.ioc.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;



import com.t13max.ioc.lang.Contract;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ConcurrentReferenceHashMap;
import com.t13max.ioc.util.ObjectUtils;

public abstract class RepeatableContainers {

	static final Map<Class<? extends Annotation>, Object> cache = new ConcurrentReferenceHashMap<>();

	private final  RepeatableContainers parent;


	private RepeatableContainers( RepeatableContainers parent) {
		this.parent = parent;
	}
	
	public final RepeatableContainers plus(Class<? extends Annotation> repeatable,
			Class<? extends Annotation> container) {

		return new ExplicitRepeatableContainer(this, repeatable, container);
	}
	
	@Deprecated(since = "7.0")
	public RepeatableContainers and(Class<? extends Annotation> container,
			Class<? extends Annotation> repeatable) {

		return plus(repeatable, container);
	}
	
	Annotation  [] findRepeatedAnnotations(Annotation annotation) {
		if (this.parent == null) {
			return null;
		}
		return this.parent.findRepeatedAnnotations(annotation);
	}


	@Override
	@Contract("null -> false")
	public boolean equals( Object other) {
		if (other == this) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(this.parent, ((RepeatableContainers) other).parent);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.parent);
	}

	
	public static RepeatableContainers standardRepeatables() {
		return StandardRepeatableContainers.INSTANCE;
	}
	
	public static RepeatableContainers explicitRepeatable(
			Class<? extends Annotation> repeatable,  Class<? extends Annotation> container) {

		return new ExplicitRepeatableContainer(null, repeatable, container);
	}
	
	@Deprecated(since = "7.0")
	public static RepeatableContainers of(
			Class<? extends Annotation> repeatable,  Class<? extends Annotation> container) {

		return explicitRepeatable(repeatable, container);
	}
	
	public static RepeatableContainers none() {
		return NoRepeatableContainers.INSTANCE;
	}

	
	private static class StandardRepeatableContainers extends RepeatableContainers {

		private static final Object NONE = new Object();

		private static final StandardRepeatableContainers INSTANCE = new StandardRepeatableContainers();

		StandardRepeatableContainers() {
			super(null);
		}

		@Override
		Annotation  [] findRepeatedAnnotations(Annotation annotation) {
			Method method = getRepeatedAnnotationsMethod(annotation.annotationType());
			if (method != null) {
				return (Annotation[]) AnnotationUtils.invokeAnnotationMethod(method, annotation);
			}
			return super.findRepeatedAnnotations(annotation);
		}

		private static  Method getRepeatedAnnotationsMethod(Class<? extends Annotation> annotationType) {
			Object result = cache.computeIfAbsent(annotationType,
					StandardRepeatableContainers::computeRepeatedAnnotationsMethod);
			return (result != NONE ? (Method) result : null);
		}

		private static Object computeRepeatedAnnotationsMethod(Class<? extends Annotation> annotationType) {
			AttributeMethods methods = AttributeMethods.forAnnotationType(annotationType);
			Method method = methods.get(MergedAnnotation.VALUE);
			if (method != null) {
				Class<?> returnType = method.getReturnType();
				if (returnType.isArray()) {
					Class<?> componentType = returnType.componentType();
					if (Annotation.class.isAssignableFrom(componentType) &&
							componentType.isAnnotationPresent(Repeatable.class)) {
						return method;
					}
				}
			}
			return NONE;
		}
	}

	
	private static class ExplicitRepeatableContainer extends RepeatableContainers {

		private final Class<? extends Annotation> repeatable;

		private final Class<? extends Annotation> container;

		private final Method valueMethod;

		ExplicitRepeatableContainer( RepeatableContainers parent,
				Class<? extends Annotation> repeatable,  Class<? extends Annotation> container) {

			super(parent);
			Assert.notNull(repeatable, "Repeatable must not be null");
			if (container == null) {
				container = deduceContainer(repeatable);
			}
			Method valueMethod = AttributeMethods.forAnnotationType(container).get(MergedAnnotation.VALUE);
			try {
				if (valueMethod == null) {
					throw new NoSuchMethodException("No value method found");
				}
				Class<?> returnType = valueMethod.getReturnType();
				if (returnType.componentType() != repeatable) {
					throw new AnnotationConfigurationException(
							"Container type [%s] must declare a 'value' attribute for an array of type [%s]"
								.formatted(container.getName(), repeatable.getName()));
				}
			}
			catch (AnnotationConfigurationException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new AnnotationConfigurationException(
						"Invalid declaration of container type [%s] for repeatable annotation [%s]"
							.formatted(container.getName(), repeatable.getName()), ex);
			}
			this.repeatable = repeatable;
			this.container = container;
			this.valueMethod = valueMethod;
		}

		private Class<? extends Annotation> deduceContainer(Class<? extends Annotation> repeatable) {
			Repeatable annotation = repeatable.getAnnotation(Repeatable.class);
			Assert.notNull(annotation, () -> "Annotation type must be a repeatable annotation: " +
						"failed to resolve container type for " + repeatable.getName());
			return annotation.value();
		}

		@Override
		Annotation  [] findRepeatedAnnotations(Annotation annotation) {
			if (this.container.isAssignableFrom(annotation.annotationType())) {
				return (Annotation[]) AnnotationUtils.invokeAnnotationMethod(this.valueMethod, annotation);
			}
			return super.findRepeatedAnnotations(annotation);
		}

		@Override
		public boolean equals( Object other) {
			if (!super.equals(other)) {
				return false;
			}
			ExplicitRepeatableContainer otherErc = (ExplicitRepeatableContainer) other;
			return (this.container.equals(otherErc.container) && this.repeatable.equals(otherErc.repeatable));
		}

		@Override
		public int hashCode() {
			int hashCode = super.hashCode();
			hashCode = 31 * hashCode + this.container.hashCode();
			hashCode = 31 * hashCode + this.repeatable.hashCode();
			return hashCode;
		}
	}

	
	private static class NoRepeatableContainers extends RepeatableContainers {

		private static final NoRepeatableContainers INSTANCE = new NoRepeatableContainers();

		NoRepeatableContainers() {
			super(null);
		}
	}

}
