package com.t13max.ioc.core.type.classreading;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;



import com.t13max.ioc.core.io.DefaultResourceLoader;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;

public class CachingMetadataReaderFactory implements MetadataReaderFactory {

	public static final int DEFAULT_CACHE_LIMIT = 256;
	private final MetadataReaderFactory delegate;

	private  Map<Resource, MetadataReader> metadataReaderCache;


	public CachingMetadataReaderFactory() {
		this.delegate = MetadataReaderFactory.create((ClassLoader) null);
		setCacheLimit(DEFAULT_CACHE_LIMIT);
	}

	public CachingMetadataReaderFactory( ClassLoader classLoader) {
		this.delegate = MetadataReaderFactory.create(classLoader);
		setCacheLimit(DEFAULT_CACHE_LIMIT);
	}

	public CachingMetadataReaderFactory( ResourceLoader resourceLoader) {
		this.delegate = MetadataReaderFactory.create(resourceLoader);
		if (resourceLoader instanceof DefaultResourceLoader defaultResourceLoader) {
			this.metadataReaderCache = defaultResourceLoader.getResourceCache(MetadataReader.class);
		}
		else {
			setCacheLimit(DEFAULT_CACHE_LIMIT);
		}
	}

	public void setCacheLimit(int cacheLimit) {
		if (cacheLimit <= 0) {
			this.metadataReaderCache = null;
		}
		else if (this.metadataReaderCache instanceof LocalResourceCache localResourceCache) {
			localResourceCache.setCacheLimit(cacheLimit);
		}
		else {
			this.metadataReaderCache = new LocalResourceCache(cacheLimit);
		}
	}

	public int getCacheLimit() {
		if (this.metadataReaderCache instanceof LocalResourceCache localResourceCache) {
			return localResourceCache.getCacheLimit();
		}
		else {
			return (this.metadataReaderCache != null ? Integer.MAX_VALUE : 0);
		}
	}
	@Override
	public MetadataReader getMetadataReader(String className) throws IOException {
		return this.delegate.getMetadataReader(className);
	}
	@Override
	public MetadataReader getMetadataReader(Resource resource) throws IOException {
		if (this.metadataReaderCache instanceof ConcurrentMap) {
			// No synchronization necessary...
			MetadataReader metadataReader = this.metadataReaderCache.get(resource);
			if (metadataReader == null) {
				metadataReader = this.delegate.getMetadataReader(resource);
				this.metadataReaderCache.put(resource, metadataReader);
			}
			return metadataReader;
		}
		else if (this.metadataReaderCache != null) {
			synchronized (this.metadataReaderCache) {
				MetadataReader metadataReader = this.metadataReaderCache.get(resource);
				if (metadataReader == null) {
					metadataReader = this.delegate.getMetadataReader(resource);
					this.metadataReaderCache.put(resource, metadataReader);
				}
				return metadataReader;
			}
		}
		else {
			return this.delegate.getMetadataReader(resource);
		}
	}

	public void clearCache() {
		if (this.metadataReaderCache instanceof LocalResourceCache) {
			synchronized (this.metadataReaderCache) {
				this.metadataReaderCache.clear();
			}
		}
		else if (this.metadataReaderCache != null) {
			// Shared resource cache -> reset to local cache.
			setCacheLimit(DEFAULT_CACHE_LIMIT);
		}
	}

	@SuppressWarnings("serial")
	private static class LocalResourceCache extends LinkedHashMap<Resource, MetadataReader> {
		private volatile int cacheLimit;
		public LocalResourceCache(int cacheLimit) {
			super(cacheLimit, 0.75f, true);
			this.cacheLimit = cacheLimit;
		}
		public void setCacheLimit(int cacheLimit) {
			this.cacheLimit = cacheLimit;
		}
		public int getCacheLimit() {
			return this.cacheLimit;
		}
		@Override
		protected boolean removeEldestEntry(Map.Entry<Resource, MetadataReader> eldest) {
			return size() > this.cacheLimit;
		}
	}

}
