package com.t13max.ioc.core.io.support;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import com.t13max.ioc.core.KotlinDetector;
import com.t13max.ioc.core.annotation.AnnotationAwareOrderComparator;
import com.t13max.ioc.core.io.UrlResource;
import com.t13max.ioc.core.log.LogMessage;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.CollectionUtils;
import com.t13max.ioc.util.ConcurrentReferenceHashMap;
import com.t13max.ioc.util.ReflectionUtils;
import com.t13max.ioc.util.StringUtils;

public class SpringFactoriesLoader {

	public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";
	private static final FailureHandler THROWING_FAILURE_HANDLER = FailureHandler.throwing();
	private static final Logger logger = LogManager.getLogger(SpringFactoriesLoader.class);
	static final Map<ClassLoader, Map<String, SpringFactoriesLoader>> cache = new ConcurrentReferenceHashMap<>();

	private final  ClassLoader classLoader;
	private final Map<String, List<String>> factories;


	protected SpringFactoriesLoader( ClassLoader classLoader, Map<String, List<String>> factories) {
		this.classLoader = classLoader;
		this.factories = factories;
	}


	public <T> List<T> load(Class<T> factoryType) {
		return load(factoryType, null, null);
	}

	public <T> List<T> load(Class<T> factoryType,  ArgumentResolver argumentResolver) {
		return load(factoryType, argumentResolver, null);
	}

	public <T> List<T> load(Class<T> factoryType,  FailureHandler failureHandler) {
		return load(factoryType, null, failureHandler);
	}

	public <T> List<T> load(Class<T> factoryType,  ArgumentResolver argumentResolver,
			 FailureHandler failureHandler) {
		Assert.notNull(factoryType, "'factoryType' must not be null");
		List<String> implementationNames = loadFactoryNames(factoryType);
		logger.trace(LogMessage.format("Loaded [%s] names: %s", factoryType.getName(), implementationNames));
		List<T> result = new ArrayList<>(implementationNames.size());
		FailureHandler failureHandlerToUse = (failureHandler != null) ? failureHandler : THROWING_FAILURE_HANDLER;
		for (String implementationName : implementationNames) {
			T factory = instantiateFactory(implementationName, factoryType, argumentResolver, failureHandlerToUse);
			if (factory != null) {
				result.add(factory);
			}
		}
		AnnotationAwareOrderComparator.sort(result);
		return result;
	}
	private List<String> loadFactoryNames(Class<?> factoryType) {
		return this.factories.getOrDefault(factoryType.getName(), Collections.emptyList());
	}
	protected <T>  T instantiateFactory(String implementationName, Class<T> type,
			 ArgumentResolver argumentResolver, FailureHandler failureHandler) {
		try {
			Class<?> factoryImplementationClass = ClassUtils.forName(implementationName, this.classLoader);
			Assert.isTrue(type.isAssignableFrom(factoryImplementationClass), () ->
					"Class [%s] is not assignable to factory type [%s]".formatted(implementationName, type.getName()));
			FactoryInstantiator<T> factoryInstantiator = FactoryInstantiator.forClass(factoryImplementationClass);
			return factoryInstantiator.instantiate(argumentResolver);
		}
		catch (Throwable ex) {
			failureHandler.handleFailure(type, implementationName, ex);
			return null;
		}
	}


	public static <T> List<T> loadFactories(Class<T> factoryType,  ClassLoader classLoader) {
		return forDefaultResourceLocation(classLoader).load(factoryType);
	}

	@Deprecated(since = "6.0")
	public static List<String> loadFactoryNames(Class<?> factoryType,  ClassLoader classLoader) {
		return forDefaultResourceLocation(classLoader).loadFactoryNames(factoryType);
	}

	public static SpringFactoriesLoader forDefaultResourceLocation() {
		return forDefaultResourceLocation(null);
	}

	public static SpringFactoriesLoader forDefaultResourceLocation( ClassLoader classLoader) {
		return forResourceLocation(FACTORIES_RESOURCE_LOCATION, classLoader);
	}

	public static SpringFactoriesLoader forResourceLocation(String resourceLocation) {
		return forResourceLocation(resourceLocation, null);
	}

	public static SpringFactoriesLoader forResourceLocation(String resourceLocation,  ClassLoader classLoader) {
		Assert.hasText(resourceLocation, "'resourceLocation' must not be empty");
		ClassLoader resourceClassLoader = (classLoader != null ? classLoader :
				SpringFactoriesLoader.class.getClassLoader());
		Map<String, SpringFactoriesLoader> loaders = cache.computeIfAbsent(
				resourceClassLoader, key -> new ConcurrentReferenceHashMap<>());
		return loaders.computeIfAbsent(resourceLocation, key ->
				new SpringFactoriesLoader(classLoader, loadFactoriesResource(resourceClassLoader, resourceLocation)));
	}
	protected static Map<String, List<String>> loadFactoriesResource(ClassLoader classLoader, String resourceLocation) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		try {
			Enumeration<URL> urls = classLoader.getResources(resourceLocation);
			while (urls.hasMoreElements()) {
				UrlResource resource = new UrlResource(urls.nextElement());
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				properties.forEach((name, value) -> {
					String[] factoryImplementationNames = StringUtils.commaDelimitedListToStringArray((String) value);
					List<String> implementations = result.computeIfAbsent(((String) name).trim(),
							key -> new ArrayList<>(factoryImplementationNames.length));
					Arrays.stream(factoryImplementationNames).map(String::trim).forEach(implementations::add);
				});
			}
			result.replaceAll(SpringFactoriesLoader::toDistinctUnmodifiableList);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" + resourceLocation + "]", ex);
		}
		return Collections.unmodifiableMap(result);
	}
	private static List<String> toDistinctUnmodifiableList(String factoryType, List<String> implementations) {
		return implementations.stream().distinct().toList();
	}


	static final class FactoryInstantiator<T> {
		private final Constructor<T> constructor;
		private FactoryInstantiator(Constructor<T> constructor) {
			ReflectionUtils.makeAccessible(constructor);
			this.constructor = constructor;
		}
		T instantiate( ArgumentResolver argumentResolver) throws Exception {
			Object[] args = resolveArgs(argumentResolver);
			if (KotlinDetector.isKotlinType(this.constructor.getDeclaringClass())) {
				return KotlinDelegate.instantiate(this.constructor, args);
			}
			return this.constructor.newInstance(args);
		}
		private Object[] resolveArgs( ArgumentResolver argumentResolver) {
			Class<?>[] types = this.constructor.getParameterTypes();
			return (argumentResolver != null ?
					Arrays.stream(types).map(argumentResolver::resolve).toArray() :
					new Object[types.length]);
		}
		@SuppressWarnings("unchecked")
		static <T> FactoryInstantiator<T> forClass(Class<?> factoryImplementationClass) {
			Constructor<?> constructor = findConstructor(factoryImplementationClass);
			Assert.state(constructor != null, () ->
					"Class [%s] has no suitable constructor".formatted(factoryImplementationClass.getName()));
			return new FactoryInstantiator<>((Constructor<T>) constructor);
		}
		private static  Constructor<?> findConstructor(Class<?> factoryImplementationClass) {
			// Same algorithm as BeanUtils.getResolvableConstructor
			Constructor<?> constructor = findPrimaryKotlinConstructor(factoryImplementationClass);
			constructor = (constructor != null ? constructor :
					findSingleConstructor(factoryImplementationClass.getConstructors()));
			constructor = (constructor != null ? constructor :
					findSingleConstructor(factoryImplementationClass.getDeclaredConstructors()));
			constructor = (constructor != null ? constructor :
					findDeclaredConstructor(factoryImplementationClass));
			return constructor;
		}
		private static  Constructor<?> findPrimaryKotlinConstructor(Class<?> factoryImplementationClass) {
			return (KotlinDetector.isKotlinType(factoryImplementationClass) ?
					KotlinDelegate.findPrimaryConstructor(factoryImplementationClass) : null);
		}
		private static  Constructor<?> findSingleConstructor(Constructor<?>[] constructors) {
			return (constructors.length == 1 ? constructors[0] : null);
		}
		private static  Constructor<?> findDeclaredConstructor(Class<?> factoryImplementationClass) {
			try {
				return factoryImplementationClass.getDeclaredConstructor();
			}
			catch (NoSuchMethodException ex) {
				return null;
			}
		}
	}


	private static class KotlinDelegate {
		static <T>  Constructor<T> findPrimaryConstructor(Class<T> clazz) {
			try {
				KFunction<T> primaryConstructor = KClasses.getPrimaryConstructor(JvmClassMappingKt.getKotlinClass(clazz));
				if (primaryConstructor != null) {
					Constructor<T> constructor = ReflectJvmMapping.getJavaConstructor(
							primaryConstructor);
					Assert.state(constructor != null, () ->
							"Failed to find Java constructor for Kotlin primary constructor: " + clazz.getName());
					return constructor;
				}
			}
			catch (UnsupportedOperationException ignored) {
			}
			return null;
		}
		static <T> T instantiate(Constructor<T> constructor, Object[] args) throws Exception {
			KFunction<T> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(constructor);
			if (kotlinConstructor == null) {
				return constructor.newInstance(args);
			}
			makeAccessible(constructor, kotlinConstructor);
			return instantiate(kotlinConstructor, convertArgs(args, kotlinConstructor.getParameters()));
		}
		private static <T> void makeAccessible(Constructor<T> constructor,
				KFunction<T> kotlinConstructor) {
			if ((!Modifier.isPublic(constructor.getModifiers()) ||
					!Modifier.isPublic(constructor.getDeclaringClass().getModifiers()))) {
				KCallablesJvm.setAccessible(kotlinConstructor, true);
			}
		}
		private static Map<KParameter, Object> convertArgs(Object[] args, List<KParameter> parameters) {
			Map<KParameter, Object> result = CollectionUtils.newHashMap(parameters.size());
			Assert.isTrue(args.length <= parameters.size(),
					"Number of provided arguments should be less than or equal to the number of constructor parameters");
			for (int i = 0; i < args.length; i++) {
				if (!parameters.get(i).isOptional() || args[i] != null) {
					result.put(parameters.get(i), args[i]);
				}
			}
			return result;
		}
		private static <T> T instantiate(KFunction<T> kotlinConstructor, Map<KParameter, Object> args) {
			return kotlinConstructor.callBy(args);
		}
	}


	@FunctionalInterface
	public interface ArgumentResolver {

		<T>  T resolve(Class<T> type);

		default <T> ArgumentResolver and(Class<T> type, T value) {
			return and(ArgumentResolver.of(type, value));
		}

		default <T> ArgumentResolver andSupplied(Class<T> type, Supplier<T> valueSupplier) {
			return and(ArgumentResolver.ofSupplied(type, valueSupplier));
		}

		default ArgumentResolver and(ArgumentResolver argumentResolver) {
			return from(type -> {
				Object resolved = resolve(type);
				return (resolved != null ? resolved : argumentResolver.resolve(type));
			});
		}

		static ArgumentResolver none() {
			return from(type -> null);
		}

		static <T> ArgumentResolver of(Class<T> type, T value) {
			return ofSupplied(type, () -> value);
		}

		static <T> ArgumentResolver ofSupplied(Class<T> type, Supplier<T> valueSupplier) {
			return from(candidateType -> (candidateType.equals(type) ? valueSupplier.get() : null));
		}

		static ArgumentResolver from(Function<Class<?>,  Object> function) {
			return new ArgumentResolver() {
				@SuppressWarnings("unchecked")
				@Override
				public <T>  T resolve(Class<T> type) {
					return (T) function.apply(type);
				}
			};
		}
	}


	@FunctionalInterface
	public interface FailureHandler {

		void handleFailure(Class<?> factoryType, String factoryImplementationName, Throwable failure);


		static FailureHandler throwing() {
			return throwing(IllegalArgumentException::new);
		}

		static FailureHandler throwing(BiFunction<String, Throwable, ? extends RuntimeException> exceptionFactory) {
			return handleMessage((messageSupplier, failure) -> {
				throw exceptionFactory.apply(messageSupplier.get(), failure);
			});
		}

		static FailureHandler logging(Log logger) {
			return handleMessage((messageSupplier, failure) -> logger.trace(LogMessage.of(messageSupplier), failure));
		}

		static FailureHandler handleMessage(BiConsumer<Supplier<String>, Throwable> messageHandler) {
			return (factoryType, factoryImplementationName, failure) -> {
				Supplier<String> messageSupplier = () -> "Unable to instantiate factory class [%s] for factory type [%s]"
						.formatted(factoryImplementationName, factoryType.getName());
				messageHandler.accept(messageSupplier, failure);
			};
		}
	}

}
