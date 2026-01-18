package com.t13max.ioc.beans.factory;



import com.t13max.ioc.beans.FatalBeanException;

@SuppressWarnings("serial")
public class CannotLoadBeanClassException extends FatalBeanException {
	private final  String resourceDescription;
	private final String beanName;
	private final  String beanClassName;

	public CannotLoadBeanClassException( String resourceDescription, String beanName,
			 String beanClassName, ClassNotFoundException cause) {
		super("Cannot find class [" + beanClassName + "] for bean with name '" + beanName + "'" +
				(resourceDescription != null ? " defined in " + resourceDescription : ""), cause);
		this.resourceDescription = resourceDescription;
		this.beanName = beanName;
		this.beanClassName = beanClassName;
	}
	public CannotLoadBeanClassException( String resourceDescription, String beanName,
			 String beanClassName, LinkageError cause) {
		super("Error loading class [" + beanClassName + "] for bean with name '" + beanName + "'" +
				(resourceDescription != null ? " defined in " + resourceDescription : "") +
				": problem with class file or dependent class", cause);
		this.resourceDescription = resourceDescription;
		this.beanName = beanName;
		this.beanClassName = beanClassName;
	}

	public  String getResourceDescription() {
		return this.resourceDescription;
	}
	public String getBeanName() {
		return this.beanName;
	}
	public  String getBeanClassName() {
		return this.beanClassName;
	}

}
