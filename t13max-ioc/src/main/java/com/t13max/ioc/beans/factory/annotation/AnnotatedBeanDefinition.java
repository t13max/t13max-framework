package com.t13max.ioc.beans.factory.annotation;



import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.core.type.AnnotationMetadata;
import com.t13max.ioc.core.type.MethodMetadata;

public interface AnnotatedBeanDefinition extends BeanDefinition {

	AnnotationMetadata getMetadata();

	 MethodMetadata getFactoryMethodMetadata();

}
