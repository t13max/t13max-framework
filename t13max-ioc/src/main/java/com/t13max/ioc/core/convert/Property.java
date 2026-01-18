package com.t13max.ioc.core.convert;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;



import com.t13max.ioc.core.MethodParameter;
import com.t13max.ioc.util.ConcurrentReferenceHashMap;
import com.t13max.ioc.util.ObjectUtils;
import com.t13max.ioc.util.ReflectionUtils;
import com.t13max.ioc.util.StringUtils;

public final class Property {
	private static final Map<Property, Annotation[]> annotationCache = new ConcurrentReferenceHashMap<>();
	private final Class<?> objectType;
	private final  Method readMethod;
	private final  Method writeMethod;
	private final String name;
	private final MethodParameter methodParameter;
	private Annotation  [] annotations;

	public Property(Class<?> objectType,  Method readMethod,  Method writeMethod) {
		this(objectType, readMethod, writeMethod, null);
	}
	public Property(
			Class<?> objectType,  Method readMethod,  Method writeMethod,  String name) {
		this.objectType = objectType;
		this.readMethod = readMethod;
		this.writeMethod = writeMethod;
		this.methodParameter = resolveMethodParameter();
		this.name = (name != null ? name : resolveName());
	}


	public Class<?> getObjectType() {
		return this.objectType;
	}

	public String getName() {
		return this.name;
	}

	public Class<?> getType() {
		return this.methodParameter.getParameterType();
	}

	public  Method getReadMethod() {
		return this.readMethod;
	}

	public  Method getWriteMethod() {
		return this.writeMethod;
	}

	// Package private
	MethodParameter getMethodParameter() {
		return this.methodParameter;
	}
	Annotation[] getAnnotations() {
		if (this.annotations == null) {
			this.annotations = resolveAnnotations();
		}
		return this.annotations;
	}

	// Internal helpers
	private String resolveName() {
		if (this.readMethod != null) {
			int index = this.readMethod.getName().indexOf("get");
			if (index != -1) {
				index += 3;
			}
			else {
				index = this.readMethod.getName().indexOf("is");
				if (index != -1) {
					index += 2;
				}
				else {
					// Record-style plain accessor method, for example, name()
					index = 0;
				}
			}
			return StringUtils.uncapitalize(this.readMethod.getName().substring(index));
		}
		else if (this.writeMethod != null) {
			int index = this.writeMethod.getName().indexOf("set");
			if (index == -1) {
				throw new IllegalArgumentException("Not a setter method");
			}
			index += 3;
			return StringUtils.uncapitalize(this.writeMethod.getName().substring(index));
		}
		else {
			throw new IllegalStateException("Property is neither readable nor writeable");
		}
	}
	private MethodParameter resolveMethodParameter() {
		MethodParameter read = resolveReadMethodParameter();
		MethodParameter write = resolveWriteMethodParameter();
		if (write == null) {
			if (read == null) {
				throw new IllegalStateException("Property is neither readable nor writeable");
			}
			return read;
		}
		if (read != null) {
			Class<?> readType = read.getParameterType();
			Class<?> writeType = write.getParameterType();
			if (!writeType.equals(readType) && writeType.isAssignableFrom(readType)) {
				return read;
			}
		}
		return write;
	}
	private  MethodParameter resolveReadMethodParameter() {
		if (getReadMethod() == null) {
			return null;
		}
		return new MethodParameter(getReadMethod(), -1).withContainingClass(getObjectType());
	}
	private  MethodParameter resolveWriteMethodParameter() {
		if (getWriteMethod() == null) {
			return null;
		}
		return new MethodParameter(getWriteMethod(), 0).withContainingClass(getObjectType());
	}
	private Annotation[] resolveAnnotations() {
		Annotation[] annotations = annotationCache.get(this);
		if (annotations == null) {
			Map<Class<? extends Annotation>, Annotation> annotationMap = new LinkedHashMap<>();
			addAnnotationsToMap(annotationMap, getReadMethod());
			addAnnotationsToMap(annotationMap, getWriteMethod());
			addAnnotationsToMap(annotationMap, getField());
			annotations = annotationMap.values().toArray(new Annotation[0]);
			annotationCache.put(this, annotations);
		}
		return annotations;
	}
	private void addAnnotationsToMap(
			Map<Class<? extends Annotation>, Annotation> annotationMap,  AnnotatedElement object) {
		if (object != null) {
			for (Annotation annotation : object.getAnnotations()) {
				annotationMap.put(annotation.annotationType(), annotation);
			}
		}
	}
	private  Field getField() {
		String name = getName();
		if (!StringUtils.hasLength(name)) {
			return null;
		}
		Field field = null;
		Class<?> declaringClass = declaringClass();
		if (declaringClass != null) {
			field = ReflectionUtils.findField(declaringClass, name);
			if (field == null) {
				// Same lenient fallback checking as in CachedIntrospectionResults...
				field = ReflectionUtils.findField(declaringClass, StringUtils.uncapitalize(name));
				if (field == null) {
					field = ReflectionUtils.findField(declaringClass, StringUtils.capitalize(name));
				}
			}
		}
		return field;
	}
	private  Class<?> declaringClass() {
		if (getReadMethod() != null) {
			return getReadMethod().getDeclaringClass();
		}
		else if (getWriteMethod() != null) {
			return getWriteMethod().getDeclaringClass();
		}
		else {
			return null;
		}
	}

	@Override
	public boolean equals( Object other) {
		return (this == other || (other instanceof Property that &&
				ObjectUtils.nullSafeEquals(this.objectType, that.objectType) &&
				ObjectUtils.nullSafeEquals(this.name, that.name) &&
				ObjectUtils.nullSafeEquals(this.readMethod, that.readMethod) &&
				ObjectUtils.nullSafeEquals(this.writeMethod, that.writeMethod)));
	}
	@Override
	public int hashCode() {
		return Objects.hash(this.objectType, this.name);
	}

}
