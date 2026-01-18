package com.t13max.ioc.beans.factory.support;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import com.t13max.ioc.beans.factory.BeanDefinitionStoreException;
import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.core.env.EnvironmentCapable;
import com.t13max.ioc.core.env.StandardEnvironment;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.io.support.PathMatchingResourcePatternResolver;
import com.t13max.ioc.core.io.support.ResourcePatternResolver;
import com.t13max.ioc.scripting.groovy.Log;
import com.t13max.ioc.util.Assert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractBeanDefinitionReader implements BeanDefinitionReader, EnvironmentCapable {
	
	protected final Logger logger = LogManager.getLogger(getClass());

	private final BeanDefinitionRegistry registry;

	private  ResourceLoader resourceLoader;

	private  ClassLoader beanClassLoader;

	private Environment environment;

	private BeanNameGenerator beanNameGenerator = DefaultBeanNameGenerator.INSTANCE;

	
	protected AbstractBeanDefinitionReader(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		this.registry = registry;

		// Determine ResourceLoader to use.
		if (this.registry instanceof ResourceLoader _resourceLoader) {
			this.resourceLoader = _resourceLoader;
		}
		else {
			this.resourceLoader = new PathMatchingResourcePatternResolver();
		}

		// Inherit Environment if possible
		if (this.registry instanceof EnvironmentCapable environmentCapable) {
			this.environment = environmentCapable.getEnvironment();
		}
		else {
			this.environment = new StandardEnvironment();
		}
	}


	@Override
	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}
	
	public void setResourceLoader( ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public  ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}
	
	public void setBeanClassLoader( ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	@Override
	public  ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}
	
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	@Override
	public Environment getEnvironment() {
		return this.environment;
	}
	
	public void setBeanNameGenerator( BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = (beanNameGenerator != null ? beanNameGenerator : DefaultBeanNameGenerator.INSTANCE);
	}

	@Override
	public BeanNameGenerator getBeanNameGenerator() {
		return this.beanNameGenerator;
	}


	@Override
	public int loadBeanDefinitions(Resource... resources) throws BeanDefinitionStoreException {
		Assert.notNull(resources, "Resource array must not be null");
		int count = 0;
		for (Resource resource : resources) {
			count += loadBeanDefinitions(resource);
		}
		return count;
	}

	@Override
	public int loadBeanDefinitions(String location) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(location, null);
	}
	
	public int loadBeanDefinitions(String location,  Set<Resource> actualResources) throws BeanDefinitionStoreException {
		ResourceLoader resourceLoader = getResourceLoader();
		if (resourceLoader == null) {
			throw new BeanDefinitionStoreException(
					"Cannot load bean definitions from location [" + location + "]: no ResourceLoader available");
		}

		if (resourceLoader instanceof ResourcePatternResolver resourcePatternResolver) {
			// Resource pattern matching available.
			try {
				Resource[] resources = resourcePatternResolver.getResources(location);
				int count = loadBeanDefinitions(resources);
				if (actualResources != null) {
					Collections.addAll(actualResources, resources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Loaded " + count + " bean definitions from location pattern [" + location + "]");
				}
				return count;
			}
			catch (IOException ex) {
				throw new BeanDefinitionStoreException(
						"Could not resolve bean definition resource pattern [" + location + "]", ex);
			}
		}
		else {
			// Can only load single resources by absolute URL.
			Resource resource = resourceLoader.getResource(location);
			int count = loadBeanDefinitions(resource);
			if (actualResources != null) {
				actualResources.add(resource);
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Loaded " + count + " bean definitions from location [" + location + "]");
			}
			return count;
		}
	}

	@Override
	public int loadBeanDefinitions(String... locations) throws BeanDefinitionStoreException {
		Assert.notNull(locations, "Location array must not be null");
		int count = 0;
		for (String location : locations) {
			count += loadBeanDefinitions(location);
		}
		return count;
	}

}
