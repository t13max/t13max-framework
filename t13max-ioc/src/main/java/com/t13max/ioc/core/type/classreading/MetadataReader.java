package com.t13max.ioc.core.type.classreading;

import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.type.AnnotationMetadata;
import com.t13max.ioc.core.type.ClassMetadata;

public interface MetadataReader {
	Resource getResource();
	ClassMetadata getClassMetadata();
	AnnotationMetadata getAnnotationMetadata();

}
