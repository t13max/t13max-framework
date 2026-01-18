package com.t13max.ioc.beans.factory.annotation;



import com.t13max.ioc.beans.factory.support.GenericBeanDefinition;
import com.t13max.ioc.core.type.AnnotationMetadata;
import com.t13max.ioc.core.type.MethodMetadata;
import com.t13max.ioc.core.type.StandardAnnotationMetadata;
import com.t13max.ioc.util.Assert;

@SuppressWarnings("serial")
public class AnnotatedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition {
	private final AnnotationMetadata metadata;
	private  MethodMetadata factoryMethodMetadata;


	public AnnotatedGenericBeanDefinition(Class<?> beanClass) {
		setBeanClass(beanClass);
		this.metadata = AnnotationMetadata.introspect(beanClass);
	}

	public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata) {
		Assert.notNull(metadata, "AnnotationMetadata must not be null");
		if (metadata instanceof StandardAnnotationMetadata sam) {
			setBeanClass(sam.getIntrospectedClass());
		}
		else {
			setBeanClassName(metadata.getClassName());
		}
		this.metadata = metadata;
	}

	public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata, MethodMetadata factoryMethodMetadata) {
		this(metadata);
		Assert.notNull(factoryMethodMetadata, "MethodMetadata must not be null");
		setFactoryMethodName(factoryMethodMetadata.getMethodName());
		this.factoryMethodMetadata = factoryMethodMetadata;
	}

	@Override
	public final AnnotationMetadata getMetadata() {
		return this.metadata;
	}
	@Override
	public final  MethodMetadata getFactoryMethodMetadata() {
		return this.factoryMethodMetadata;
	}

}
