package com.t13max.ioc.core.type.filter;

import java.io.IOException;

import com.t13max.ioc.core.type.classreading.MetadataReader;
import com.t13max.ioc.core.type.classreading.MetadataReaderFactory;

@FunctionalInterface
public interface TypeFilter {	
	boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
			throws IOException;

}
