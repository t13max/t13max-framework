package com.t13max.ioc.beans;

import java.beans.PropertyChangeEvent;



import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;

@SuppressWarnings("serial")
public class TypeMismatchException extends PropertyAccessException {	
	public static final String ERROR_CODE = "typeMismatch";

	private  String propertyName;
	private final transient  Object value;
	private final  Class<?> requiredType;
	
	public TypeMismatchException(PropertyChangeEvent propertyChangeEvent, Class<?> requiredType) {
		this(propertyChangeEvent, requiredType, null);
	}	
	public TypeMismatchException(PropertyChangeEvent propertyChangeEvent,  Class<?> requiredType,
			 Throwable cause) {
		super(propertyChangeEvent,
				"Failed to convert property value of type '" +
				ClassUtils.getDescriptiveType(propertyChangeEvent.getNewValue()) + "'" +
				(requiredType != null ?
				" to required type '" + ClassUtils.getQualifiedName(requiredType) + "'" : "") +
				(propertyChangeEvent.getPropertyName() != null ?
				" for property '" + propertyChangeEvent.getPropertyName() + "'" : "") +
				(cause != null ? "; " + cause.getMessage() : ""),
				cause);
		this.propertyName = propertyChangeEvent.getPropertyName();
		this.value = propertyChangeEvent.getNewValue();
		this.requiredType = requiredType;
	}	
	public TypeMismatchException( Object value,  Class<?> requiredType) {
		this(value, requiredType, null);
	}	
	public TypeMismatchException( Object value,  Class<?> requiredType,  Throwable cause) {
		super("Failed to convert value of type '" + ClassUtils.getDescriptiveType(value) + "'" +
				(requiredType != null ? " to required type '" + ClassUtils.getQualifiedName(requiredType) + "'" : "") +
				(cause != null ? "; " + cause.getMessage() : ""),
				cause);
		this.value = value;
		this.requiredType = requiredType;
	}
	
	public void initPropertyName(String propertyName) {
		Assert.state(this.propertyName == null, "Property name already initialized");
		this.propertyName = propertyName;
	}	
	@Override
	public  String getPropertyName() {
		return this.propertyName;
	}	
	@Override
	public  Object getValue() {
		return this.value;
	}	
	public  Class<?> getRequiredType() {
		return this.requiredType;
	}
	@Override
	public String getErrorCode() {
		return ERROR_CODE;
	}

}
