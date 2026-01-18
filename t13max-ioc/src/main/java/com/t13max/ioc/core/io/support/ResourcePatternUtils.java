package com.t13max.ioc.core.io.support;



import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.util.ResourceUtils;

public abstract class ResourcePatternUtils {	
	public static boolean isUrl( String resourceLocation) {
		return (resourceLocation != null &&
				(resourceLocation.startsWith(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX) ||
						ResourceUtils.isUrl(resourceLocation)));
	}	
	public static ResourcePatternResolver getResourcePatternResolver( ResourceLoader resourceLoader) {
		if (resourceLoader instanceof ResourcePatternResolver resolver) {
			return resolver;
		}
		else if (resourceLoader != null) {
			return new PathMatchingResourcePatternResolver(resourceLoader);
		}
		else {
			return new PathMatchingResourcePatternResolver();
		}
	}

}
