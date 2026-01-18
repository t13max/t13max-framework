package com.t13max.ioc.beans.factory.config;



import com.t13max.ioc.util.Assert;

public class RuntimeBeanReference implements BeanReference  {

	private final String beanName;
	private final  Class<?> beanType;
	private final boolean toParent;
	private  Object source;


	public RuntimeBeanReference(String beanName) {
		this(beanName, false);
	}

	public RuntimeBeanReference(String beanName, boolean toParent) {
		Assert.hasText(beanName, "'beanName' must not be empty");
		this.beanName = beanName;
		this.beanType = null;
		this.toParent = toParent;
	}

	public RuntimeBeanReference(Class<?> beanType) {
		this(beanType, false);
	}

	public RuntimeBeanReference(Class<?> beanType, boolean toParent) {
		Assert.notNull(beanType, "'beanType' must not be null");
		this.beanName = beanType.getName();
		this.beanType = beanType;
		this.toParent = toParent;
	}


	@Override
	public String getBeanName() {
		return this.beanName;
	}

	public  Class<?> getBeanType() {
		return this.beanType;
	}

	public boolean isToParent() {
		return this.toParent;
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
		return (this == other || (other instanceof RuntimeBeanReference that &&
				this.beanName.equals(that.beanName) && this.beanType == that.beanType &&
				this.toParent == that.toParent));
	}
	@Override
	public int hashCode() {
		int result = this.beanName.hashCode();
		result = 29 * result + (this.toParent ? 1 : 0);
		return result;
	}
	@Override
	public String toString() {
		return '<' + getBeanName() + '>';
	}

}
