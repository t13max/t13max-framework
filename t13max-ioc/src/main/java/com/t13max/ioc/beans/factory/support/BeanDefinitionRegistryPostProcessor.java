package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.config.BeanFactoryPostProcessor;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;

public interface BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor {	
	void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;	
	@Override
	default void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
	}

}
