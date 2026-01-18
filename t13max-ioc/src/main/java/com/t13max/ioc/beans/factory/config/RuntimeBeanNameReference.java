package com.t13max.ioc.beans.factory.config;



import com.t13max.ioc.util.Assert;

public class RuntimeBeanNameReference implements BeanReference {
	private final String beanName;
	private  Object source;


	public RuntimeBeanNameReference(String beanName) {
		Assert.hasText(beanName, "'beanName' must not be empty");
		this.beanName = beanName;
	}
	@Override
	public String getBeanName() {
		return this.beanName;
	}

	public void setSource( Object source) {
		this.source = source;
	}
	@Override
	public  Object getSource() {
		return this.source;
	}

	@Override
	public boolean equals( Object other) {
		return (this == other || (other instanceof RuntimeBeanNameReference that &&
				this.beanName.equals(that.beanName)));
	}
	@Override
	public int hashCode() {
		return this.beanName.hashCode();
	}
	@Override
	public String toString() {
		return '<' + getBeanName() + '>';
	}

}
