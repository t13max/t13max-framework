package com.t13max.ioc.core.io;

import java.beans.PropertyEditorSupport;
import java.io.IOException;



import com.t13max.ioc.core.env.PropertyResolver;
import com.t13max.ioc.core.env.StandardEnvironment;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.StringUtils;

public class ResourceEditor extends PropertyEditorSupport {
	private final ResourceLoader resourceLoader;
	private  PropertyResolver propertyResolver;
	private final boolean ignoreUnresolvablePlaceholders;


	public ResourceEditor() {
		this(new DefaultResourceLoader(), null);
	}

	public ResourceEditor(ResourceLoader resourceLoader,  PropertyResolver propertyResolver) {
		this(resourceLoader, propertyResolver, true);
	}

	public ResourceEditor(ResourceLoader resourceLoader,  PropertyResolver propertyResolver,
			boolean ignoreUnresolvablePlaceholders) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
		this.propertyResolver = propertyResolver;
		this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
	}

	@Override
	public void setAsText(String text) {
		if (StringUtils.hasText(text)) {
			String locationToUse = resolvePath(text).trim();
			setValue(this.resourceLoader.getResource(locationToUse));
		}
		else {
			setValue(null);
		}
	}

	protected String resolvePath(String path) {
		if (this.propertyResolver == null) {
			this.propertyResolver = new StandardEnvironment();
		}
		return (this.ignoreUnresolvablePlaceholders ? this.propertyResolver.resolvePlaceholders(path) :
				this.propertyResolver.resolveRequiredPlaceholders(path));
	}

	@Override
	public  String getAsText() {
		Resource value = (Resource) getValue();
		try {
			// Try to determine URL for resource.
			return (value != null ? value.getURL().toExternalForm() : "");
		}
		catch (IOException ex) {
			// Couldn't determine resource URL - return null to indicate
			// that there is no appropriate text representation.
			return null;
		}
	}

}
