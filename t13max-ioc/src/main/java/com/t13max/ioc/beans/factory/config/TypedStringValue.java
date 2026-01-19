package com.t13max.ioc.beans.factory.config;

import java.util.Comparator;



import com.t13max.ioc.beans.BeanMetadataElement;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ObjectUtils;

public class TypedStringValue implements BeanMetadataElement, Comparable<TypedStringValue> {
	private  String value;
	private volatile  Object targetType;
	private  Object source;
	private  String specifiedTypeName;
	private volatile boolean dynamic;


	public TypedStringValue( String value) {
		setValue(value);
	}

	public TypedStringValue( String value, Class<?> targetType) {
		setValue(value);
		setTargetType(targetType);
	}

	public TypedStringValue( String value, String targetTypeName) {
		setValue(value);
		setTargetTypeName(targetTypeName);
	}


	public void setValue( String value) {
		this.value = value;
	}

	public  String getValue() {
		return this.value;
	}

	public void setTargetType(Class<?> targetType) {
		Assert.notNull(targetType, "'targetType' must not be null");
		this.targetType = targetType;
	}

	public Class<?> getTargetType() {
		Object targetTypeValue = this.targetType;
		if (!(targetTypeValue instanceof Class<?> clazz)) {
			throw new IllegalStateException("Typed String value does not carry a resolved target type");
		}
		return clazz;
	}

	public void setTargetTypeName( String targetTypeName) {
		this.targetType = targetTypeName;
	}

	public  String getTargetTypeName() {
		Object targetTypeValue = this.targetType;
		if (targetTypeValue instanceof Class<?> clazz) {
			return clazz.getName();
		}
		else {
			return (String) targetTypeValue;
		}
	}

	public boolean hasTargetType() {
		return (this.targetType instanceof Class);
	}

	public  Class<?> resolveTargetType( ClassLoader classLoader) throws ClassNotFoundException {
		String typeName = getTargetTypeName();
		if (typeName == null) {
			return null;
		}
		Class<?> resolvedClass = ClassUtils.forName(typeName, classLoader);
		this.targetType = resolvedClass;
		return resolvedClass;
	}


	public void setSource( Object source) {
		this.source = source;
	}
	@Override
	public  Object getSource() {
		return this.source;
	}

	public void setSpecifiedTypeName( String specifiedTypeName) {
		this.specifiedTypeName = specifiedTypeName;
	}

	public  String getSpecifiedTypeName() {
		return this.specifiedTypeName;
	}

	public void setDynamic() {
		this.dynamic = true;
	}

	public boolean isDynamic() {
		return this.dynamic;
	}
	@Override
	public int compareTo( TypedStringValue o) {
		return Comparator.comparing(TypedStringValue::getValue).compare(this, o);
	}
	@Override
	public boolean equals( Object other) {
		return (this == other || (other instanceof TypedStringValue that &&
				ObjectUtils.nullSafeEquals(this.value, that.value) &&
				ObjectUtils.nullSafeEquals(this.targetType, that.targetType)));
	}
	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(this.value, this.targetType);
	}
	@Override
	public String toString() {
		return "TypedStringValue: value [" + this.value + "], target type [" + this.targetType + "]";
	}

}
