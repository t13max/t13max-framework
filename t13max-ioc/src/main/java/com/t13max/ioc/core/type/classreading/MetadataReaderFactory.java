package com.t13max.ioc.core.type.classreading;

import java.io.IOException;



import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;

public interface MetadataReaderFactory {

	MetadataReader getMetadataReader(String className) throws IOException;

	MetadataReader getMetadataReader(Resource resource) throws IOException;

	static MetadataReaderFactory create( ResourceLoader resourceLoader) {
		return MetadataReaderFactoryDelegate.create(resourceLoader);
	}

	static MetadataReaderFactory create( ClassLoader classLoader) {
		return MetadataReaderFactoryDelegate.create(classLoader);
	}
}
