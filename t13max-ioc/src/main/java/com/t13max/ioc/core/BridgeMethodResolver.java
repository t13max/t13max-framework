package com.t13max.ioc.core;

import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ConcurrentReferenceHashMap;
import com.t13max.ioc.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 
 * @Author: t13max
 * @Since: 22:16 2026/1/16
 */
public final class BridgeMethodResolver {
	private static final Map<Object, Method> cache = new ConcurrentReferenceHashMap<>();
	private BridgeMethodResolver() {
	}
	
	public static Method findBridgedMethod(Method bridgeMethod) {
		return resolveBridgeMethod(bridgeMethod, bridgeMethod.getDeclaringClass());
	}	
	public static Method getMostSpecificMethod(Method bridgeMethod,  Class<?> targetClass) {
		if (targetClass != null &&
				!ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()).isAssignableFrom(targetClass) &&
				!Proxy.isProxyClass(bridgeMethod.getDeclaringClass())) {
			// From a different class hierarchy, and not a JDK or CGLIB proxy either -> return as-is.
			return bridgeMethod;
		}
		Method specificMethod = ClassUtils.getMostSpecificMethod(bridgeMethod, targetClass);
		return resolveBridgeMethod(specificMethod,
				(targetClass != null ? targetClass : specificMethod.getDeclaringClass()));
	}
	private static Method resolveBridgeMethod(Method bridgeMethod, Class<?> targetClass) {
		boolean localBridge = (targetClass == bridgeMethod.getDeclaringClass());
		Class<?> userClass = targetClass;
		if (!bridgeMethod.isBridge() && localBridge) {
			userClass = ClassUtils.getUserClass(targetClass);
			if (userClass == targetClass) {
				return bridgeMethod;
			}
		}
		Object cacheKey = (localBridge ? bridgeMethod : new MethodClassKey(bridgeMethod, targetClass));
		Method bridgedMethod = cache.get(cacheKey);
		if (bridgedMethod == null) {
			// Gather all methods with matching name and parameter size.
			List<Method> candidateMethods = new ArrayList<>();
			MethodFilter filter = (candidateMethod -> isBridgedCandidateFor(candidateMethod, bridgeMethod));
			ReflectionUtils.doWithMethods(userClass, candidateMethods::add, filter);
			if (!candidateMethods.isEmpty()) {
				bridgedMethod = (candidateMethods.size() == 1 ? candidateMethods.get(0) :
						searchCandidates(candidateMethods, bridgeMethod));
			}
			if (bridgedMethod == null) {
				// A bridge method was passed in but we couldn't find the bridged method.
				// Let's proceed with the passed-in method and hope for the best...
				bridgedMethod = bridgeMethod;
			}
			cache.put(cacheKey, bridgedMethod);
		}
		return bridgedMethod;
	}	
	private static boolean isBridgedCandidateFor(Method candidateMethod, Method bridgeMethod) {
		return (!candidateMethod.isBridge() &&
				candidateMethod.getName().equals(bridgeMethod.getName()) &&
				candidateMethod.getParameterCount() == bridgeMethod.getParameterCount());
	}	
	private static  Method searchCandidates(List<Method> candidateMethods, Method bridgeMethod) {
		if (candidateMethods.isEmpty()) {
			return null;
		}
		Method previousMethod = null;
		boolean sameSig = true;
		for (Method candidateMethod : candidateMethods) {
			if (isBridgeMethodFor(bridgeMethod, candidateMethod, bridgeMethod.getDeclaringClass())) {
				return candidateMethod;
			}
			else if (previousMethod != null) {
				sameSig = sameSig && Arrays.equals(
						candidateMethod.getGenericParameterTypes(), previousMethod.getGenericParameterTypes());
			}
			previousMethod = candidateMethod;
		}
		return (sameSig ? candidateMethods.get(0) : null);
	}	
	static boolean isBridgeMethodFor(Method bridgeMethod, Method candidateMethod, Class<?> declaringClass) {
		if (isResolvedTypeMatch(candidateMethod, bridgeMethod, declaringClass)) {
			return true;
		}
		Method method = findGenericDeclaration(bridgeMethod);
		return (method != null && isResolvedTypeMatch(method, candidateMethod, declaringClass));
	}	
	private static boolean isResolvedTypeMatch(Method genericMethod, Method candidateMethod, Class<?> declaringClass) {
		Type[] genericParameters = genericMethod.getGenericParameterTypes();
		if (genericParameters.length != candidateMethod.getParameterCount()) {
			return false;
		}
		Class<?>[] candidateParameters = candidateMethod.getParameterTypes();
		for (int i = 0; i < candidateParameters.length; i++) {
			ResolvableType genericParameter = ResolvableType.forMethodParameter(genericMethod, i, declaringClass);
			Class<?> candidateParameter = candidateParameters[i];
			if (candidateParameter.isArray()) {
				// An array type: compare the component type.
				if (!candidateParameter.componentType().equals(genericParameter.getComponentType().toClass())) {
					return false;
				}
			}
			// A non-array type: compare the type itself.
			if (!ClassUtils.resolvePrimitiveIfNecessary(candidateParameter).equals(
					ClassUtils.resolvePrimitiveIfNecessary(genericParameter.toClass()))) {
				return false;
			}
		}
		return true;
	}	
	private static  Method findGenericDeclaration(Method bridgeMethod) {
		if (!bridgeMethod.isBridge()) {
			return bridgeMethod;
		}
		// Search parent types for method that has same signature as bridge.
		Class<?> superclass = bridgeMethod.getDeclaringClass().getSuperclass();
		while (superclass != null && Object.class != superclass) {
			Method method = searchForMatch(superclass, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			}
			superclass = superclass.getSuperclass();
		}
		Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(bridgeMethod.getDeclaringClass());
		return searchInterfaces(interfaces, bridgeMethod);
	}
	private static  Method searchInterfaces(Class<?>[] interfaces, Method bridgeMethod) {
		for (Class<?> ifc : interfaces) {
			Method method = searchForMatch(ifc, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			}
			else {
				method = searchInterfaces(ifc.getInterfaces(), bridgeMethod);
				if (method != null) {
					return method;
				}
			}
		}
		return null;
	}	
	private static  Method searchForMatch(Class<?> type, Method bridgeMethod) {
		try {
			return type.getDeclaredMethod(bridgeMethod.getName(), bridgeMethod.getParameterTypes());
		}
		catch (NoSuchMethodException ex) {
			return null;
		}
	}	
	public static boolean isVisibilityBridgeMethodPair(Method bridgeMethod, Method bridgedMethod) {
		if (bridgeMethod == bridgedMethod) {
			// Same method: for common purposes, return true to proceed as if it was a visibility bridge.
			return true;
		}
		if (ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()) != bridgeMethod.getDeclaringClass()) {
			// Method on generated subclass: return false to consistently ignore it for visibility purposes.
			return false;
		}
		return (bridgeMethod.getReturnType().equals(bridgedMethod.getReturnType()) &&
				bridgeMethod.getParameterCount() == bridgedMethod.getParameterCount() &&
				Arrays.equals(bridgeMethod.getParameterTypes(), bridgedMethod.getParameterTypes()));
	}

}
