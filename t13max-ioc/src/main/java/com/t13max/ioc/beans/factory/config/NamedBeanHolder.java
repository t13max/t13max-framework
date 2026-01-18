package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.factory.NamedBean;
import com.t13max.ioc.util.Assert;

public class NamedBeanHolder<T> implements NamedBean {

	private final String beanName;

	private final T beanInstance;

	public NamedBeanHolder(String beanName, T beanInstance) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.beanName = beanName;
		this.beanInstance = beanInstance;
	}

	@Override
	public String getBeanName() {
		return this.beanName;
	}

	public T getBeanInstance() {
		return this.beanInstance;
	}

}
