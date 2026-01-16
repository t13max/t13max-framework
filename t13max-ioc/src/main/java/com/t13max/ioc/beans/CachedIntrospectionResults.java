package com.t13max.ioc.beans;

import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ConcurrentReferenceHashMap;
import com.t13max.ioc.utils.StringUtils;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CachedIntrospectionResults {

	private static final List<BeanInfoFactory> beanInfoFactories = SpringFactoriesLoader.loadFactories(
			BeanInfoFactory.class, CachedIntrospectionResults.class.getClassLoader());

	private static final SimpleBeanInfoFactory simpleBeanInfoFactory = new SimpleBeanInfoFactory();

	private static final Logger logger = LogManager.getLogger(CachedIntrospectionResults.class);

	
	static final Set<ClassLoader> acceptedClassLoaders = ConcurrentHashMap.newKeySet(16);

	
	static final ConcurrentMap<Class<?>, CachedIntrospectionResults> strongClassCache =
			new ConcurrentHashMap<>(64);

	
	static final ConcurrentMap<Class<?>, CachedIntrospectionResults> softClassCache =
			new ConcurrentReferenceHashMap<>(64);


	
	public static void acceptClassLoader( ClassLoader classLoader) {
		if (classLoader != null) {
			acceptedClassLoaders.add(classLoader);
		}
	}

	
	public static void clearClassLoader( ClassLoader classLoader) {
		acceptedClassLoaders.removeIf(registeredLoader ->
				isUnderneathClassLoader(registeredLoader, classLoader));
		strongClassCache.keySet().removeIf(beanClass ->
				isUnderneathClassLoader(beanClass.getClassLoader(), classLoader));
		softClassCache.keySet().removeIf(beanClass ->
				isUnderneathClassLoader(beanClass.getClassLoader(), classLoader));
	}

	
	static CachedIntrospectionResults forClass(Class<?> beanClass) throws BeansException {
		CachedIntrospectionResults results = strongClassCache.get(beanClass);
		if (results != null) {
			return results;
		}
		results = softClassCache.get(beanClass);
		if (results != null) {
			return results;
		}

		results = new CachedIntrospectionResults(beanClass);
		ConcurrentMap<Class<?>, CachedIntrospectionResults> classCacheToUse;

		if (ClassUtils.isCacheSafe(beanClass, CachedIntrospectionResults.class.getClassLoader()) ||
				isClassLoaderAccepted(beanClass.getClassLoader())) {
			classCacheToUse = strongClassCache;
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("Not strongly caching class [" + beanClass.getName() + "] because it is not cache-safe");
			}
			classCacheToUse = softClassCache;
		}

		CachedIntrospectionResults existing = classCacheToUse.putIfAbsent(beanClass, results);
		return (existing != null ? existing : results);
	}

	
	private static boolean isClassLoaderAccepted(ClassLoader classLoader) {
		for (ClassLoader acceptedLoader : acceptedClassLoaders) {
			if (isUnderneathClassLoader(classLoader, acceptedLoader)) {
				return true;
			}
		}
		return false;
	}

	
	private static boolean isUnderneathClassLoader( ClassLoader candidate,  ClassLoader parent) {
		if (candidate == parent) {
			return true;
		}
		if (candidate == null) {
			return false;
		}
		ClassLoader classLoaderToCheck = candidate;
		while (classLoaderToCheck != null) {
			classLoaderToCheck = classLoaderToCheck.getParent();
			if (classLoaderToCheck == parent) {
				return true;
			}
		}
		return false;
	}

	
	private static BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException {
		for (BeanInfoFactory beanInfoFactory : beanInfoFactories) {
			BeanInfo beanInfo = beanInfoFactory.getBeanInfo(beanClass);
			if (beanInfo != null) {
				return beanInfo;
			}
		}
		return simpleBeanInfoFactory.getBeanInfo(beanClass);
	}


	
	private final BeanInfo beanInfo;

	
	private final Map<String, PropertyDescriptor> propertyDescriptors;


	
	private CachedIntrospectionResults(Class<?> beanClass) throws BeansException {
		try {
			if (logger.isTraceEnabled()) {
				logger.trace("Getting BeanInfo for class [" + beanClass.getName() + "]");
			}
			this.beanInfo = getBeanInfo(beanClass);

			if (logger.isTraceEnabled()) {
				logger.trace("Caching PropertyDescriptors for class [" + beanClass.getName() + "]");
			}
			this.propertyDescriptors = new LinkedHashMap<>();

			Set<String> readMethodNames = new HashSet<>();

			// This call is slow so we do it once.
			PropertyDescriptor[] pds = this.beanInfo.getPropertyDescriptors();
			for (PropertyDescriptor pd : pds) {
				if (Class.class == beanClass && !("name".equals(pd.getName()) ||
						(pd.getName().endsWith("Name") && String.class == pd.getPropertyType()))) {
					// Only allow all name variants of Class properties
					continue;
				}
				if (URL.class == beanClass && "content".equals(pd.getName())) {
					// Only allow URL attribute introspection, not content resolution
					continue;
				}
				if (pd.getWriteMethod() == null && isInvalidReadOnlyPropertyType(pd.getPropertyType(), beanClass)) {
					// Ignore read-only properties such as ClassLoader - no need to bind to those
					continue;
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Found bean property '" + pd.getName() + "'" +
							(pd.getPropertyType() != null ? " of type [" + pd.getPropertyType().getName() + "]" : "") +
							(pd.getPropertyEditorClass() != null ?
									"; editor [" + pd.getPropertyEditorClass().getName() + "]" : ""));
				}
				pd = buildGenericTypeAwarePropertyDescriptor(beanClass, pd);
				this.propertyDescriptors.put(pd.getName(), pd);
				Method readMethod = pd.getReadMethod();
				if (readMethod != null) {
					readMethodNames.add(readMethod.getName());
				}
			}

			// Explicitly check implemented interfaces for setter/getter methods as well,
			// in particular for Java 8 default methods...
			Class<?> currClass = beanClass;
			while (currClass != null && currClass != Object.class) {
				introspectInterfaces(beanClass, currClass, readMethodNames);
				currClass = currClass.getSuperclass();
			}

			// Check for record-style accessors without prefix: for example, "lastName()"
			// - accessor method directly referring to instance field of same name
			// - same convention for component accessors of Java 15 record classes
			introspectPlainAccessors(beanClass, readMethodNames);
		}
		catch (IntrospectionException ex) {
			throw new FatalBeanException("Failed to obtain BeanInfo for class [" + beanClass.getName() + "]", ex);
		}
	}

	private void introspectInterfaces(Class<?> beanClass, Class<?> currClass, Set<String> readMethodNames)
			throws IntrospectionException {

		for (Class<?> ifc : currClass.getInterfaces()) {
			if (!ClassUtils.isJavaLanguageInterface(ifc)) {
				for (PropertyDescriptor pd : getBeanInfo(ifc).getPropertyDescriptors()) {
					PropertyDescriptor existingPd = this.propertyDescriptors.get(pd.getName());
					if (existingPd == null ||
							(existingPd.getReadMethod() == null && pd.getReadMethod() != null)) {
						// GenericTypeAwarePropertyDescriptor leniently resolves a set* write method
						// against a declared read method, so we prefer read method descriptors here.
						pd = buildGenericTypeAwarePropertyDescriptor(beanClass, pd);
						if (pd.getWriteMethod() == null &&
								isInvalidReadOnlyPropertyType(pd.getPropertyType(), beanClass)) {
							// Ignore read-only properties such as ClassLoader - no need to bind to those
							continue;
						}
						this.propertyDescriptors.put(pd.getName(), pd);
						Method readMethod = pd.getReadMethod();
						if (readMethod != null) {
							readMethodNames.add(readMethod.getName());
						}
					}
				}
				introspectInterfaces(ifc, ifc, readMethodNames);
			}
		}
	}

	private void introspectPlainAccessors(Class<?> beanClass, Set<String> readMethodNames)
			throws IntrospectionException {

		for (Method method : beanClass.getMethods()) {
			if (!this.propertyDescriptors.containsKey(method.getName()) &&
					!readMethodNames.contains(method.getName()) && isPlainAccessor(method)) {
				this.propertyDescriptors.put(method.getName(),
						new GenericTypeAwarePropertyDescriptor(beanClass, method.getName(), method, null, null));
				readMethodNames.add(method.getName());
			}
		}
	}

	private boolean isPlainAccessor(Method method) {
		if (Modifier.isStatic(method.getModifiers()) ||
				method.getDeclaringClass() == Object.class || method.getDeclaringClass() == Class.class ||
				method.getParameterCount() > 0 || method.getReturnType() == void.class ||
				isInvalidReadOnlyPropertyType(method.getReturnType(), method.getDeclaringClass())) {
			return false;
		}
		try {
			// Accessor method referring to instance field of same name?
			method.getDeclaringClass().getDeclaredField(method.getName());
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}

	private boolean isInvalidReadOnlyPropertyType( Class<?> returnType, Class<?> beanClass) {
		return (returnType != null && (ClassLoader.class.isAssignableFrom(returnType) ||
				ProtectionDomain.class.isAssignableFrom(returnType) ||
				(AutoCloseable.class.isAssignableFrom(returnType) &&
						!AutoCloseable.class.isAssignableFrom(beanClass))));
	}


	BeanInfo getBeanInfo() {
		return this.beanInfo;
	}

	Class<?> getBeanClass() {
		return this.beanInfo.getBeanDescriptor().getBeanClass();
	}

	 PropertyDescriptor getPropertyDescriptor(String name) {
		PropertyDescriptor pd = this.propertyDescriptors.get(name);
		if (pd == null && StringUtils.hasLength(name)) {
			// Same lenient fallback checking as in Property...
			pd = this.propertyDescriptors.get(StringUtils.uncapitalize(name));
			if (pd == null) {
				pd = this.propertyDescriptors.get(StringUtils.capitalize(name));
			}
		}
		return pd;
	}

	PropertyDescriptor[] getPropertyDescriptors() {
		return this.propertyDescriptors.values().toArray(PropertyDescriptorUtils.EMPTY_PROPERTY_DESCRIPTOR_ARRAY);
	}

	private PropertyDescriptor buildGenericTypeAwarePropertyDescriptor(Class<?> beanClass, PropertyDescriptor pd) {
		try {
			return new GenericTypeAwarePropertyDescriptor(beanClass, pd.getName(), pd.getReadMethod(),
					pd.getWriteMethod(), pd.getPropertyEditorClass());
		}
		catch (IntrospectionException ex) {
			throw new FatalBeanException("Failed to re-introspect class [" + beanClass.getName() + "]", ex);
		}
	}

}
