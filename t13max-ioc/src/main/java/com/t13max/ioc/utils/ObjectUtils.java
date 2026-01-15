package com.t13max.ioc.utils;

import com.t13max.ioc.lang.Contract;

import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.*;

public abstract class ObjectUtils {

	private static final String EMPTY_STRING = "";
	private static final String NULL_STRING = "null";
	private static final String ARRAY_START = "{";
	private static final String ARRAY_END = "}";
	private static final String EMPTY_ARRAY = ARRAY_START + ARRAY_END;
	private static final String ARRAY_ELEMENT_SEPARATOR = ", ";
	private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
	private static final String NON_EMPTY_ARRAY = ARRAY_START + "..." + ARRAY_END;
	private static final String COLLECTION = "[...]";
	private static final String MAP = NON_EMPTY_ARRAY;

	
	public static boolean isCheckedException(Throwable ex) {
		return !(ex instanceof RuntimeException || ex instanceof Error);
	}
	
	public static boolean isCompatibleWithThrowsClause(Throwable ex, Class<?> ... declaredExceptions) {
		if (!isCheckedException(ex)) {
			return true;
		}
		if (declaredExceptions != null) {
			for (Class<?> declaredException : declaredExceptions) {
				if (declaredException.isInstance(ex)) {
					return true;
				}
			}
		}
		return false;
	}
	
	@Contract("null -> false")
	public static boolean isArray(Object obj) {
		return (obj != null && obj.getClass().isArray());
	}
	
	@Contract("null -> true")
	public static boolean isEmpty(Object [] array) {
		return (array == null || array.length == 0);
	}
	
	@Contract("null -> true")
	public static boolean isEmpty(Object obj) {
		if (obj == null) {
			return true;
		}

		if (obj instanceof Optional<?> optional) {
			return optional.isEmpty();
		}
		if (obj instanceof CharSequence charSequence) {
			return charSequence.isEmpty();
		}
		if (obj.getClass().isArray()) {
			return Array.getLength(obj) == 0;
		}
		if (obj instanceof Collection<?> collection) {
			return collection.isEmpty();
		}
		if (obj instanceof Map<?, ?> map) {
			return map.isEmpty();
		}

		// else
		return false;
	}
	
	@Contract("null -> null")
	public static Object unwrapOptional(Object obj) {
		if (obj instanceof Optional<?> optional) {
			Object result = optional.orElse(null);
			Assert.isTrue(!(result instanceof Optional), "Multi-level Optional usage not supported");
			return result;
		}
		return obj;
	}
	
	@Contract("null, _ -> false")
	public static boolean containsElement(Object [] array, Object element) {
		if (array == null) {
			return false;
		}
		for (Object arrayEle : array) {
			if (nullSafeEquals(arrayEle, element)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean containsConstant(Enum<?>[] enumValues, String constant) {
		return containsConstant(enumValues, constant, false);
	}
	
	public static boolean containsConstant(Enum<?>[] enumValues, String constant, boolean caseSensitive) {
		for (Enum<?> candidate : enumValues) {
			if (caseSensitive ? candidate.toString().equals(constant) :
					candidate.toString().equalsIgnoreCase(constant)) {
				return true;
			}
		}
		return false;
	}
	
	public static <E extends Enum<?>> E caseInsensitiveValueOf(E[] enumValues, String constant) {
		for (E candidate : enumValues) {
			if (candidate.toString().equalsIgnoreCase(constant)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("Constant [" + constant + "] does not exist in enum type " +
				enumValues.getClass().componentType().getName());
	}
	
	public static <A, O extends A> A[] addObjectToArray(A [] array, O obj) {
		return addObjectToArray(array, obj, (array != null ? array.length : 0));
	}
	
	public static <A, O extends A> A[] addObjectToArray(A [] array, O obj, int position) {
		Class<?> componentType = Object.class;
		if (array != null) {
			componentType = array.getClass().componentType();
		}
		else if (obj != null) {
			componentType = obj.getClass();
		}
		int newArrayLength = (array != null ? array.length + 1 : 1);
		@SuppressWarnings("unchecked")
		A[] newArray = (A[]) Array.newInstance(componentType, newArrayLength);
		if (array != null) {
			System.arraycopy(array, 0, newArray, 0, position);
			System.arraycopy(array, position, newArray, position + 1, array.length - position);
		}
		newArray[position] = obj;
		return newArray;
	}
	
	public static Object[] toObjectArray(Object source) {
		if (source instanceof Object[] objects) {
			return objects;
		}
		if (source == null) {
			return EMPTY_OBJECT_ARRAY;
		}
		if (!source.getClass().isArray()) {
			throw new IllegalArgumentException("Source is not an array: " + source);
		}
		int length = Array.getLength(source);
		if (length == 0) {
			return EMPTY_OBJECT_ARRAY;
		}
		Class<?> wrapperType = Array.get(source, 0).getClass();
		Object[] newArray = (Object[]) Array.newInstance(wrapperType, length);
		for (int i = 0; i < length; i++) {
			newArray[i] = Array.get(source, i);
		}
		return newArray;
	}


	//---------------------------------------------------------------------
	// Convenience methods for content-based equality/hash-code handling
	//---------------------------------------------------------------------
	
	@Contract("null, null -> true; null, _ -> false; _, null -> false")
	public static boolean nullSafeEquals(Object o1, Object o2) {
		if (o1 == o2) {
			return true;
		}
		if (o1 == null || o2 == null) {
			return false;
		}
		if (o1.equals(o2)) {
			return true;
		}
		if (o1.getClass().isArray() && o2.getClass().isArray()) {
			return arrayEquals(o1, o2);
		}
		return false;
	}
	
	private static boolean arrayEquals(Object o1, Object o2) {
		if (o1 instanceof Object[] objects1 && o2 instanceof Object[] objects2) {
			return Arrays.equals(objects1, objects2);
		}
		if (o1 instanceof boolean[] booleans1 && o2 instanceof boolean[] booleans2) {
			return Arrays.equals(booleans1, booleans2);
		}
		if (o1 instanceof byte[] bytes1 && o2 instanceof byte[] bytes2) {
			return Arrays.equals(bytes1, bytes2);
		}
		if (o1 instanceof char[] chars1 && o2 instanceof char[] chars2) {
			return Arrays.equals(chars1, chars2);
		}
		if (o1 instanceof double[] doubles1 && o2 instanceof double[] doubles2) {
			return Arrays.equals(doubles1, doubles2);
		}
		if (o1 instanceof float[] floats1 && o2 instanceof float[] floats2) {
			return Arrays.equals(floats1, floats2);
		}
		if (o1 instanceof int[] ints1 && o2 instanceof int[] ints2) {
			return Arrays.equals(ints1, ints2);
		}
		if (o1 instanceof long[] longs1 && o2 instanceof long[] longs2) {
			return Arrays.equals(longs1, longs2);
		}
		if (o1 instanceof short[] shorts1 && o2 instanceof short[] shorts2) {
			return Arrays.equals(shorts1, shorts2);
		}
		return false;
	}
	
	public static int nullSafeHash(Object ... elements) {
		if (elements == null) {
			return 0;
		}
		int result = 1;
		for (Object element : elements) {
			result = 31 * result + nullSafeHashCode(element);
		}
		return result;
	}
	
	public static int nullSafeHashCode(Object obj) {
		if (obj == null) {
			return 0;
		}
		if (obj.getClass().isArray()) {
			if (obj instanceof Object[] objects) {
				return Arrays.hashCode(objects);
			}
			if (obj instanceof boolean[] booleans) {
				return Arrays.hashCode(booleans);
			}
			if (obj instanceof byte[] bytes) {
				return Arrays.hashCode(bytes);
			}
			if (obj instanceof char[] chars) {
				return Arrays.hashCode(chars);
			}
			if (obj instanceof double[] doubles) {
				return Arrays.hashCode(doubles);
			}
			if (obj instanceof float[] floats) {
				return Arrays.hashCode(floats);
			}
			if (obj instanceof int[] ints) {
				return Arrays.hashCode(ints);
			}
			if (obj instanceof long[] longs) {
				return Arrays.hashCode(longs);
			}
			if (obj instanceof short[] shorts) {
				return Arrays.hashCode(shorts);
			}
		}
		return obj.hashCode();
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(Object [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(boolean [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(byte [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(char [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(double [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(float [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(int [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(long [] array) {
		return Arrays.hashCode(array);
	}
	
	@Deprecated(since = "6.1")
	public static int nullSafeHashCode(short [] array) {
		return Arrays.hashCode(array);
	}


	//---------------------------------------------------------------------
	// Convenience methods for toString output
	//---------------------------------------------------------------------
	
	public static String identityToString(Object obj) {
		if (obj == null) {
			return EMPTY_STRING;
		}
		return obj.getClass().getName() + "@" + getIdentityHexString(obj);
	}
	
	public static String getIdentityHexString(Object obj) {
		return Integer.toHexString(System.identityHashCode(obj));
	}
	
	public static String getDisplayString(Object obj) {
		if (obj == null) {
			return EMPTY_STRING;
		}
		return nullSafeToString(obj);
	}
	
	public static String nullSafeClassName(Object obj) {
		return (obj != null ? obj.getClass().getName() : NULL_STRING);
	}
	
	public static String nullSafeToString(Object obj) {
		if (obj == null) {
			return NULL_STRING;
		}
		if (obj instanceof String string) {
			return string;
		}
		if (obj instanceof Object[] objects) {
			return nullSafeToString(objects);
		}
		if (obj instanceof boolean[] booleans) {
			return nullSafeToString(booleans);
		}
		if (obj instanceof byte[] bytes) {
			return nullSafeToString(bytes);
		}
		if (obj instanceof char[] chars) {
			return nullSafeToString(chars);
		}
		if (obj instanceof double[] doubles) {
			return nullSafeToString(doubles);
		}
		if (obj instanceof float[] floats) {
			return nullSafeToString(floats);
		}
		if (obj instanceof int[] ints) {
			return nullSafeToString(ints);
		}
		if (obj instanceof long[] longs) {
			return nullSafeToString(longs);
		}
		if (obj instanceof short[] shorts) {
			return nullSafeToString(shorts);
		}
		String str = obj.toString();
		return (str != null ? str : EMPTY_STRING);
	}
	
	public static String nullSafeToString(Object [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (Object o : array) {
			stringJoiner.add(String.valueOf(o));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(boolean [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (boolean b : array) {
			stringJoiner.add(String.valueOf(b));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(byte [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		if (array.length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (byte b : array) {
			stringJoiner.add(String.valueOf(b));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(char [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		if (array.length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (char c : array) {
			stringJoiner.add('\'' + String.valueOf(c) + '\'');
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(double [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		if (array.length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (double d : array) {
			stringJoiner.add(String.valueOf(d));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(float [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		if (array.length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (float f : array) {
			stringJoiner.add(String.valueOf(f));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(int [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		if (array.length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (int i : array) {
			stringJoiner.add(String.valueOf(i));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(long [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (long l : array) {
			stringJoiner.add(String.valueOf(l));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeToString(short [] array) {
		if (array == null) {
			return NULL_STRING;
		}
		if (array.length == 0) {
			return EMPTY_ARRAY;
		}
		StringJoiner stringJoiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
		for (short s : array) {
			stringJoiner.add(String.valueOf(s));
		}
		return stringJoiner.toString();
	}
	
	public static String nullSafeConciseToString(Object obj) {
		if (obj == null) {
			return NULL_STRING;
		}
		if (obj instanceof Optional<?> optional) {
			return (optional.isEmpty() ? "Optional.empty" :
				"Optional[%s]".formatted(nullSafeConciseToString(optional.get())));
		}
		if (obj.getClass().isArray()) {
			return (Array.getLength(obj) == 0 ? EMPTY_ARRAY : NON_EMPTY_ARRAY);
		}
		if (obj instanceof Collection) {
			return COLLECTION;
		}
		if (obj instanceof Map) {
			return MAP;
		}
		if (obj instanceof Class<?> clazz) {
			return clazz.getName();
		}
		if (obj instanceof Charset charset) {
			return charset.name();
		}
		if (obj instanceof TimeZone timeZone) {
			return timeZone.getID();
		}
		if (obj instanceof ZoneId zoneId) {
			return zoneId.getId();
		}
		if (obj instanceof CharSequence charSequence) {
			return StringUtils.truncate(charSequence);
		}
		Class<?> type = obj.getClass();
		if (ClassUtils.isSimpleValueType(type)) {
			String str = obj.toString();
			if (str != null) {
				return StringUtils.truncate(str);
			}
		}
		return type.getTypeName() + "@" + getIdentityHexString(obj);
	}

}
