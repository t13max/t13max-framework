package com.t13max.ioc.beans.factory.config;

import java.lang.reflect.Constructor;



import com.t13max.ioc.beans.BeansException;

public interface SmartInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessor {

	default  Class<?> predictBeanType(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	default Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {
		return beanClass;
	}

	default Constructor<?>  [] determineCandidateConstructors(Class<?> beanClass, String beanName)
			throws BeansException {
		return null;
	}

	default Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
