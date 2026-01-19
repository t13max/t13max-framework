package com.t13max.ioc.core.io.support;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.core.env.PropertyResolver;
import com.t13max.ioc.core.env.StandardEnvironment;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.StringUtils;

public class ResourceArrayPropertyEditor extends PropertyEditorSupport {
	private static final Logger logger = LogManager.getLogger(ResourceArrayPropertyEditor.class);
	private final ResourcePatternResolver resourcePatternResolver;
	private  PropertyResolver propertyResolver;
	private final boolean ignoreUnresolvablePlaceholders;


	public ResourceArrayPropertyEditor() {
		this(new PathMatchingResourcePatternResolver(), null, true);
	}

	public ResourceArrayPropertyEditor(
			ResourcePatternResolver resourcePatternResolver,  PropertyResolver propertyResolver) {
		this(resourcePatternResolver, propertyResolver, true);
	}

	public ResourceArrayPropertyEditor(ResourcePatternResolver resourcePatternResolver,
			 PropertyResolver propertyResolver, boolean ignoreUnresolvablePlaceholders) {
		Assert.notNull(resourcePatternResolver, "ResourcePatternResolver must not be null");
		this.resourcePatternResolver = resourcePatternResolver;
		this.propertyResolver = propertyResolver;
		this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
	}


	@Override
	public void setAsText(String text) {
		String pattern = resolvePath(text).trim();
		String[] locationPatterns = StringUtils.commaDelimitedListToStringArray(pattern);
		if (locationPatterns.length == 1) {
			setValue(getResources(locationPatterns[0]));
		}
		else {
			Resource[] resources = Arrays.stream(locationPatterns).map(String::trim)
					.map(this::getResources).flatMap(Arrays::stream).toArray(Resource[]::new);
			setValue(resources);
		}
	}
	private Resource[] getResources(String locationPattern) {
		try {
			return this.resourcePatternResolver.getResources(locationPattern);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(
					"Could not resolve resource location pattern [" + locationPattern + "]: " + ex.getMessage());
		}
	}

	@Override
	public void setValue(Object value) throws IllegalArgumentException {
		if (value instanceof Collection || (value instanceof Object[] && !(value instanceof Resource[]))) {
			Collection<?> input = (value instanceof Collection<?> collection ? collection : Arrays.asList((Object[]) value));
			Set<Resource> merged = new LinkedHashSet<>();
			for (Object element : input) {
				if (element instanceof String path) {
					// A location pattern: resolve it into a Resource array.
					// Might point to a single resource or to multiple resources.
					String pattern = resolvePath(path.trim());
					try {
						Resource[] resources = this.resourcePatternResolver.getResources(pattern);
						Collections.addAll(merged, resources);
					}
					catch (IOException ex) {
						// ignore - might be an unresolved placeholder or non-existing base directory
						if (logger.isDebugEnabled()) {
							logger.debug("Could not retrieve resources for pattern '" + pattern + "'", ex);
						}
					}
				}
				else if (element instanceof Resource resource) {
					// A Resource object: add it to the result.
					merged.add(resource);
				}
				else {
					throw new IllegalArgumentException("Cannot convert element [" + element + "] to [" +
							Resource.class.getName() + "]: only location String and Resource object supported");
				}
			}
			super.setValue(merged.toArray(new Resource[0]));
		}
		else {
			// An arbitrary value: probably a String or a Resource array.
			// setAsText will be called for a String; a Resource array will be used as-is.
			super.setValue(value);
		}
	}

	protected String resolvePath(String path) {
		if (this.propertyResolver == null) {
			this.propertyResolver = new StandardEnvironment();
		}
		return (this.ignoreUnresolvablePlaceholders ? this.propertyResolver.resolvePlaceholders(path) :
				this.propertyResolver.resolveRequiredPlaceholders(path));
	}

}
