package com.t13max.ioc.context.annotation;

import com.t13max.ioc.beans.factory.annotation.AnnotatedBeanDefinition;
import com.t13max.ioc.beans.factory.support.GenericBeanDefinition;
import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;
import com.t13max.ioc.core.type.AnnotationMetadata;
import com.t13max.ioc.core.type.MethodMetadata;
import com.t13max.ioc.core.type.classreading.MetadataReader;
import com.t13max.ioc.util.Assert;

/**
 * @Author: t13max
 * @Since: 7:59 2026/1/17
 */
public class ScannedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition {

    private final AnnotationMetadata metadata;


    /**
     * Create a new ScannedGenericBeanDefinition for the class that the
     * given MetadataReader describes.
     * @param metadataReader the MetadataReader for the scanned target class
     */
    public ScannedGenericBeanDefinition(MetadataReader metadataReader) {
        Assert.notNull(metadataReader, "MetadataReader must not be null");
        this.metadata = metadataReader.getAnnotationMetadata();
        setBeanClassName(this.metadata.getClassName());
        setResource(metadataReader.getResource());
    }


    @Override
    public final AnnotationMetadata getMetadata() {
        return this.metadata;
    }

    @Override
    public  MethodMetadata getFactoryMethodMetadata() {
        return null;
    }
}
