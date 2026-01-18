package com.t13max.ioc.beans.testfixture.beans.factory.generator;

import com.t13max.ioc.beans.factory.support.DefaultListableBeanFactory;

@FunctionalInterface
public interface BeanFactoryInitializer {
	void initializeBeanFactory(DefaultListableBeanFactory beanFactory);

}
