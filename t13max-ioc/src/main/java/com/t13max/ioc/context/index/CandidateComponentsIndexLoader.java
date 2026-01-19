package com.t13max.ioc.context.index;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import com.t13max.ioc.core.SpringProperties;
import com.t13max.ioc.core.io.UrlResource;
import com.t13max.ioc.core.io.support.PropertiesLoaderUtils;
import com.t13max.ioc.util.ConcurrentReferenceHashMap;

@Deprecated(since = "6.1", forRemoval = true)
@SuppressWarnings("removal")
public final class CandidateComponentsIndexLoader {

	public static final String COMPONENTS_RESOURCE_LOCATION = "META-INF/spring.components";

	public static final String IGNORE_INDEX = "spring.index.ignore";

	private static final boolean shouldIgnoreIndex = SpringProperties.getFlag(IGNORE_INDEX);
	private static final Logger logger = LogManager.getLogger(CandidateComponentsIndexLoader.class);
	private static final ConcurrentMap<ClassLoader, CandidateComponentsIndex> cache =
			new ConcurrentReferenceHashMap<>();

	private CandidateComponentsIndexLoader() {
	}


	public static  CandidateComponentsIndex loadIndex( ClassLoader classLoader) {
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = CandidateComponentsIndexLoader.class.getClassLoader();
		}
		return cache.computeIfAbsent(classLoaderToUse, CandidateComponentsIndexLoader::doLoadIndex);
	}
	private static  CandidateComponentsIndex doLoadIndex(ClassLoader classLoader) {
		if (shouldIgnoreIndex) {
			return null;
		}
		try {
			Enumeration<URL> urls = classLoader.getResources(COMPONENTS_RESOURCE_LOCATION);
			if (!urls.hasMoreElements()) {
				return null;
			}
			List<Properties> result = new ArrayList<>();
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				Properties properties = PropertiesLoaderUtils.loadProperties(new UrlResource(url));
				result.add(properties);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Loaded " + result.size() + " index(es)");
			}
			int totalCount = result.stream().mapToInt(Properties::size).sum();
			return (totalCount > 0 ? new CandidateComponentsIndex(result) : null);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to load indexes from location [" +
					COMPONENTS_RESOURCE_LOCATION + "]", ex);
		}
	}

}
