package com.t13max.ioc.core.style;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimpleValueStyler extends DefaultValueStyler {

	
	public static final Function<Class<?>, String> DEFAULT_CLASS_STYLER = Class::getCanonicalName;

	
	public static final Function<Method, String> DEFAULT_METHOD_STYLER = SimpleValueStyler::toSimpleMethodSignature;


	private final Function<Class<?>, String> classStyler;

	private final Function<Method, String> methodStyler;


	
	public SimpleValueStyler() {
		this(DEFAULT_CLASS_STYLER, DEFAULT_METHOD_STYLER);
	}

	
	public SimpleValueStyler(Function<Class<?>, String> classStyler, Function<Method, String> methodStyler) {
		this.classStyler = classStyler;
		this.methodStyler = methodStyler;
	}


	@Override
	protected String styleNull() {
		return "null";
	}

	@Override
	protected String styleString(String str) {
		return "\"" + str + "\"";
	}

	@Override
	protected String styleClass(Class<?> clazz) {
		return this.classStyler.apply(clazz);
	}

	@Override
	protected String styleMethod(Method method) {
		return this.methodStyler.apply(method);
	}

	@Override
	protected <K, V> String styleMap(Map<K, V> map) {
		StringJoiner result = new StringJoiner(", ", "{", "}");
		for (Map.Entry<K, V> entry : map.entrySet()) {
			result.add(style(entry));
		}
		return result.toString();
	}

	@Override
	protected String styleCollection(Collection<?> collection) {
		StringJoiner result = new StringJoiner(", ", "[", "]");
		for (Object element : collection) {
			result.add(style(element));
		}
		return result.toString();
	}

	@Override
	protected String styleArray(Object[] array) {
		StringJoiner result = new StringJoiner(", ", "[", "]");
		for (Object element : array) {
			result.add(style(element));
		}
		return result.toString();
	}

	private static String toSimpleMethodSignature(Method method) {
		String parameterList = Arrays.stream(method.getParameterTypes())
				.map(Class::getSimpleName)
				.collect(Collectors.joining(", "));
		return String.format("%s(%s)", method.getName(), parameterList);
	}

}
