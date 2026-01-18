package com.t13max.ioc.core.style;

import org.jspecify.annotations.Nullable;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.*;

public class DefaultValueStyler implements ValueStyler {

	private static final String EMPTY = "[[empty]]";
	private static final String NULL = "[null]";
	private static final String COLLECTION = "collection";
	private static final String SET = "set";
	private static final String LIST = "list";
	private static final String MAP = "map";
	private static final String EMPTY_MAP = MAP + EMPTY;
	private static final String ARRAY = "array";


	@Override
	public String style( Object value) {
		if (value == null) {
			return styleNull();
		}
		else if (value instanceof String str) {
			return styleString(str);
		}
		else if (value instanceof Class<?> clazz) {
			return styleClass(clazz);
		}
		else if (value instanceof Method method) {
			return styleMethod(method);
		}
		else if (value instanceof Map<?, ?> map) {
			return styleMap(map);
		}
		else if (value instanceof Map.Entry<?, ?> entry) {
			return styleMapEntry(entry);
		}
		else if (value instanceof Collection<?> collection) {
			return styleCollection(collection);
		}
		else if (value.getClass().isArray()) {
			return styleArray(ObjectUtils.toObjectArray(value));
		}
		else {
			return styleObject(value);
		}
	}

	
	protected String styleNull() {
		return NULL;
	}

	
	protected String styleString(String str) {
		return "\'" + str + "\'";
	}

	
	protected String styleClass(Class<?> clazz) {
		return ClassUtils.getShortName(clazz);
	}

	
	protected String styleMethod(Method method) {
		return method.getName() + "@" + ClassUtils.getShortName(method.getDeclaringClass());
	}

	
	protected <K, V> String styleMap(Map<K, V> map) {
		if (map.isEmpty()) {
			return EMPTY_MAP;
		}

		StringJoiner result = new StringJoiner(", ", "[", "]");
		for (Map.Entry<K, V> entry : map.entrySet()) {
			result.add(styleMapEntry(entry));
		}
		return MAP + result;
	}

	
	protected String styleMapEntry(Map.Entry<?, ?> entry) {
		return style(entry.getKey()) + " -> " + style(entry.getValue());
	}

	
	protected String styleCollection(Collection<?> collection) {
		String collectionType = getCollectionTypeString(collection);

		if (collection.isEmpty()) {
			return collectionType + EMPTY;
		}

		StringJoiner result = new StringJoiner(", ", "[", "]");
		for (Object element : collection) {
			result.add(style(element));
		}
		return collectionType + result;
	}

	
	protected String styleArray(Object[] array) {
		if (array.length == 0) {
			return ARRAY + '<' + ClassUtils.getShortName(array.getClass().componentType()) + '>' + EMPTY;
		}

		StringJoiner result = new StringJoiner(", ", "[", "]");
		for (Object element : array) {
			result.add(style(element));
		}
		return ARRAY + '<' + ClassUtils.getShortName(array.getClass().componentType()) + '>' + result;
	}

	
	protected String styleObject(Object obj) {
		return String.valueOf(obj);
	}


	private static String getCollectionTypeString(Collection<?> collection) {
		if (collection instanceof List) {
			return LIST;
		}
		else if (collection instanceof Set) {
			return SET;
		}
		else {
			return COLLECTION;
		}
	}

}
