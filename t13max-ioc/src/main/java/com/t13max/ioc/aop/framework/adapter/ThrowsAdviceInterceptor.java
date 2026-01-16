package com.t13max.ioc.aop.framework.adapter;

import com.t13max.ioc.aop.AfterAdvice;
import com.t13max.ioc.aop.framework.AopConfigException;
import com.t13max.ioc.aop.intecept.MethodInterceptor;
import com.t13max.ioc.aop.intecept.MethodInvocation;
import com.t13max.ioc.utils.Assert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ThrowsAdviceInterceptor implements MethodInterceptor, AfterAdvice {

	private static final String AFTER_THROWING = "afterThrowing";

	private static final Logger logger = LogManager.getLogger(ThrowsAdviceInterceptor.class);


	private final Object throwsAdvice;

	private final Map<Class<?>, Method> exceptionHandlerMap = new HashMap<>();

	public ThrowsAdviceInterceptor(Object throwsAdvice) {
		Assert.notNull(throwsAdvice, "Advice must not be null");
		this.throwsAdvice = throwsAdvice;

		Method[] methods = throwsAdvice.getClass().getMethods();
		for (Method method : methods) {
			if (method.getName().equals(AFTER_THROWING)) {
				Class<?> throwableParam = null;
				if (method.getParameterCount() == 1) {
					// just a Throwable parameter
					throwableParam = method.getParameterTypes()[0];
					if (!Throwable.class.isAssignableFrom(throwableParam)) {
						throw new AopConfigException("Invalid afterThrowing signature: " +
								"single argument must be a Throwable subclass");
					}
				}
				else if (method.getParameterCount() == 4) {
					// Method, Object[], target, throwable
					Class<?>[] paramTypes = method.getParameterTypes();
					if (!Method.class.equals(paramTypes[0]) || !Object[].class.equals(paramTypes[1]) ||
							Throwable.class.equals(paramTypes[2]) || !Throwable.class.isAssignableFrom(paramTypes[3])) {
						throw new AopConfigException("Invalid afterThrowing signature: " +
								"four arguments must be Method, Object[], target, throwable: " + method);
					}
					throwableParam = paramTypes[3];
				}
				if (throwableParam == null) {
					throw new AopConfigException("Unsupported afterThrowing signature: single throwable argument " +
							"or four arguments Method, Object[], target, throwable expected: " + method);
				}
				// An exception handler to register...
				Method existingMethod = this.exceptionHandlerMap.put(throwableParam, method);
				if (existingMethod != null) {
					throw new AopConfigException("Only one afterThrowing method per specific Throwable subclass " +
							"allowed: " + method + " / " + existingMethod);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Found exception handler method on throws advice: " + method);
				}
			}
		}

		if (this.exceptionHandlerMap.isEmpty()) {
			throw new AopConfigException(
					"At least one handler method must be found in class [" + throwsAdvice.getClass() + "]");
		}
	}


	
	public int getHandlerMethodCount() {
		return this.exceptionHandlerMap.size();
	}


	@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		try {
			return mi.proceed();
		}
		catch (Throwable ex) {
			Method handlerMethod = getExceptionHandler(ex);
			if (handlerMethod != null) {
				invokeHandlerMethod(mi, ex, handlerMethod);
			}
			throw ex;
		}
	}

	
	private Method getExceptionHandler(Throwable exception) {
		Class<?> exceptionClass = exception.getClass();
		if (logger.isTraceEnabled()) {
			logger.trace("Trying to find handler for exception of type [" + exceptionClass.getName() + "]");
		}
		Method handler = this.exceptionHandlerMap.get(exceptionClass);
		while (handler == null && exceptionClass != Throwable.class) {
			exceptionClass = exceptionClass.getSuperclass();
			handler = this.exceptionHandlerMap.get(exceptionClass);
		}
		if (handler != null && logger.isTraceEnabled()) {
			logger.trace("Found handler for exception of type [" + exceptionClass.getName() + "]: " + handler);
		}
		return handler;
	}

	private void invokeHandlerMethod(MethodInvocation mi, Throwable ex, Method method) throws Throwable {
		Object[] handlerArgs;
		if (method.getParameterCount() == 1) {
			handlerArgs = new Object[] {ex};
		}
		else {
			handlerArgs = new Object[] {mi.getMethod(), mi.getArguments(), mi.getThis(), ex};
		}
		try {
			method.invoke(this.throwsAdvice, handlerArgs);
		}
		catch (InvocationTargetException targetEx) {
			throw targetEx.getTargetException();
		}
	}

}
