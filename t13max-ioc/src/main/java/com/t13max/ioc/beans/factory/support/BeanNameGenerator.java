package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.config.BeanDefinition;

public interface BeanNameGenerator {	
	String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry);

}
