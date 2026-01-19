package com.t13max.ioc.beans.factory.support;



import com.t13max.ioc.util.StringUtils;

public class BeanDefinitionDefaults {
	private  Boolean lazyInit;
	private int autowireMode = AbstractBeanDefinition.AUTOWIRE_NO;
	private int dependencyCheck = AbstractBeanDefinition.DEPENDENCY_CHECK_NONE;
	private  String initMethodName;
	private  String destroyMethodName;


	public void setLazyInit(boolean lazyInit) {
		this.lazyInit = lazyInit;
	}

	public boolean isLazyInit() {
		return (this.lazyInit != null && this.lazyInit);
	}

	public  Boolean getLazyInit() {
		return this.lazyInit;
	}

	public void setAutowireMode(int autowireMode) {
		this.autowireMode = autowireMode;
	}

	public int getAutowireMode() {
		return this.autowireMode;
	}

	public void setDependencyCheck(int dependencyCheck) {
		this.dependencyCheck = dependencyCheck;
	}

	public int getDependencyCheck() {
		return this.dependencyCheck;
	}

	public void setInitMethodName( String initMethodName) {
		this.initMethodName = (StringUtils.hasText(initMethodName) ? initMethodName : null);
	}

	public  String getInitMethodName() {
		return this.initMethodName;
	}

	public void setDestroyMethodName( String destroyMethodName) {
		this.destroyMethodName = (StringUtils.hasText(destroyMethodName) ? destroyMethodName : null);
	}

	public  String getDestroyMethodName() {
		return this.destroyMethodName;
	}

}
