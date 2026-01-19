package com.t13max.ioc.beans.factory;



import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.util.StringUtils;

@SuppressWarnings("serial")
public class UnsatisfiedDependencyException extends BeanCreationException {
	private final  InjectionPoint injectionPoint;
	
	public UnsatisfiedDependencyException(
			 String resourceDescription,  String beanName, String propertyName,  String msg) {
		super(resourceDescription, beanName,
				"Unsatisfied dependency expressed through bean property '" + propertyName + "'" +
				(StringUtils.hasLength(msg) ? ": " + msg : ""));
		this.injectionPoint = null;
	}	
	public UnsatisfiedDependencyException(
			 String resourceDescription,  String beanName, String propertyName, BeansException ex) {
		this(resourceDescription, beanName, propertyName, ex.getMessage());
		initCause(ex);
	}	
	public UnsatisfiedDependencyException(
			 String resourceDescription,  String beanName,  InjectionPoint injectionPoint,  String msg) {
		super(resourceDescription, beanName,
				"Unsatisfied dependency expressed through " + injectionPoint +
				(StringUtils.hasLength(msg) ? ": " + msg : ""));
		this.injectionPoint = injectionPoint;
	}	
	public UnsatisfiedDependencyException(
			 String resourceDescription,  String beanName,  InjectionPoint injectionPoint, BeansException ex) {
		this(resourceDescription, beanName, injectionPoint, ex.getMessage());
		initCause(ex);
	}
	
	public  InjectionPoint getInjectionPoint() {
		return this.injectionPoint;
	}

}
