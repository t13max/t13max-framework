package com.t13max.ioc.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;



import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.StringUtils;

@SuppressWarnings("serial")
public class AnnotationAttributes extends LinkedHashMap<String,  Object> {

	private static final String UNKNOWN = "unknown";

	private final  Class<? extends Annotation> annotationType;

	final String displayName;

	final boolean validated;

	
	public AnnotationAttributes() {
		this.annotationType = null;
		this.displayName = UNKNOWN;
		this.validated = false;
	}
	
	public AnnotationAttributes(int initialCapacity) {
		super(initialCapacity);
		this.annotationType = null;
		this.displayName = UNKNOWN;
		this.validated = false;
	}
	
	public AnnotationAttributes(Map<String,  Object> map) {
		super(map);
		this.annotationType = null;
		this.displayName = UNKNOWN;
		this.validated = false;
	}
	
	public AnnotationAttributes(AnnotationAttributes other) {
		super(other);
		this.annotationType = other.annotationType;
		this.displayName = other.displayName;
		this.validated = other.validated;
	}
	
	public AnnotationAttributes(Class<? extends Annotation> annotationType) {
		this(annotationType, false);
	}
	
	AnnotationAttributes(Class<? extends Annotation> annotationType, boolean validated) {
		Assert.notNull(annotationType, "'annotationType' must not be null");
		this.annotationType = annotationType;
		this.displayName = annotationType.getName();
		this.validated = validated;
	}
	
	public AnnotationAttributes(String annotationType,  ClassLoader classLoader) {
		Assert.notNull(annotationType, "'annotationType' must not be null");
		this.annotationType = getAnnotationType(annotationType, classLoader);
		this.displayName = annotationType;
		this.validated = false;
	}

	@SuppressWarnings("unchecked")
	private static  Class<? extends Annotation> getAnnotationType(String annotationType,  ClassLoader classLoader) {
		if (classLoader != null) {
			try {
				return (Class<? extends Annotation>) classLoader.loadClass(annotationType);
			}
			catch (ClassNotFoundException ex) {
				// Annotation Class not resolvable
			}
		}
		return null;
	}

	
	public  Class<? extends Annotation> annotationType() {
		return this.annotationType;
	}
	
	public String getString(String attributeName) {
		return getRequiredAttribute(attributeName, String.class);
	}
	
	public String[] getStringArray(String attributeName) {
		return getRequiredAttribute(attributeName, String[].class);
	}
	
	public boolean getBoolean(String attributeName) {
		return getRequiredAttribute(attributeName, Boolean.class);
	}
	
	@SuppressWarnings("unchecked")
	public <N extends Number> N getNumber(String attributeName) {
		return (N) getRequiredAttribute(attributeName, Number.class);
	}
	
	@SuppressWarnings("unchecked")
	public <E extends Enum<?>> E getEnum(String attributeName) {
		return (E) getRequiredAttribute(attributeName, Enum.class);
	}
	
	@SuppressWarnings("unchecked")
	public <T> Class<? extends T> getClass(String attributeName) {
		return getRequiredAttribute(attributeName, Class.class);
	}
	
	public Class<?>[] getClassArray(String attributeName) {
		return getRequiredAttribute(attributeName, Class[].class);
	}
	
	public AnnotationAttributes getAnnotation(String attributeName) {
		return getRequiredAttribute(attributeName, AnnotationAttributes.class);
	}
	
	public <A extends Annotation> A getAnnotation(String attributeName, Class<A> annotationType) {
		return getRequiredAttribute(attributeName, annotationType);
	}
	
	public AnnotationAttributes[] getAnnotationArray(String attributeName) {
		return getRequiredAttribute(attributeName, AnnotationAttributes[].class);
	}
	
	@SuppressWarnings("unchecked")
	public <A extends Annotation> A[] getAnnotationArray(String attributeName, Class<A> annotationType) {
		return (A[]) getRequiredAttribute(attributeName, annotationType.arrayType());
	}
	
	@SuppressWarnings("unchecked")
	private <T> T getRequiredAttribute(String attributeName, Class<T> expectedType) {
		Assert.hasText(attributeName, "'attributeName' must not be null or empty");
		Object value = get(attributeName);
		if (value == null) {
			throw new IllegalArgumentException(String.format(
					"Attribute '%s' not found in attributes for annotation [%s]",
					attributeName, this.displayName));
		}
		if (value instanceof Throwable throwable) {
			throw new IllegalArgumentException(String.format(
					"Attribute '%s' for annotation [%s] was not resolvable due to exception [%s]",
					attributeName, this.displayName, value), throwable);
		}
		if (!expectedType.isInstance(value) && expectedType.isArray() &&
				expectedType.componentType().isInstance(value)) {
			Object array = Array.newInstance(expectedType.componentType(), 1);
			Array.set(array, 0, value);
			value = array;
		}
		if (!expectedType.isInstance(value)) {
			throw new IllegalArgumentException(String.format(
					"Attribute '%s' is of type %s, but %s was expected in attributes for annotation [%s]",
					attributeName, value.getClass().getSimpleName(), expectedType.getSimpleName(),
					this.displayName));
		}
		return (T) value;
	}

	@Override
	public String toString() {
		Iterator<Map.Entry<String,  Object>> entries = entrySet().iterator();
		StringBuilder sb = new StringBuilder("{");
		while (entries.hasNext()) {
			Map.Entry<String,  Object> entry = entries.next();
			sb.append(entry.getKey());
			sb.append('=');
			sb.append(valueToString(entry.getValue()));
			if (entries.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append('}');
		return sb.toString();
	}

	private String valueToString( Object value) {
		if (value == this) {
			return "(this Map)";
		}
		if (value instanceof Object[] objects) {
			return "[" + StringUtils.arrayToDelimitedString(objects, ", ") + "]";
		}
		return String.valueOf(value);
	}

	
	public static  AnnotationAttributes fromMap( Map<String,  Object> map) {
		if (map == null) {
			return null;
		}
		if (map instanceof AnnotationAttributes annotationAttributes) {
			return annotationAttributes;
		}
		return new AnnotationAttributes(map);
	}

}
