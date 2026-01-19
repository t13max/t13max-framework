package com.t13max.ioc.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;



import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.LinkedMultiValueMap;
import com.t13max.ioc.util.MultiValueMap;
import com.t13max.ioc.util.ReflectionUtils;

public final class CollectionFactory {
	private static final Set<Class<?>> approximableCollectionTypes = Set.of(
			// Standard collection interfaces
			Collection.class,
			List.class,
			Set.class,
			SortedSet.class,
			NavigableSet.class,
			// Common concrete collection classes
			ArrayList.class,
			LinkedList.class,
			HashSet.class,
			LinkedHashSet.class,
			TreeSet.class,
			EnumSet.class);
	private static final Set<Class<?>> approximableMapTypes = Set.of(
			// Standard map interfaces
			Map.class,
			MultiValueMap.class,
			SortedMap.class,
			NavigableMap.class,
			// Common concrete map classes
			HashMap.class,
			LinkedHashMap.class,
			LinkedMultiValueMap.class,
			TreeMap.class,
			EnumMap.class);

	private CollectionFactory() {
	}


	public static boolean isApproximableCollectionType( Class<?> collectionType) {
		return (collectionType != null && (approximableCollectionTypes.contains(collectionType) ||
				collectionType.getName().equals("java.util.SequencedSet") ||
				collectionType.getName().equals("java.util.SequencedCollection")));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static <E> Collection<E> createApproximateCollection( Object collection, int capacity) {
		if (collection instanceof EnumSet enumSet) {
			Collection<E> copy = EnumSet.copyOf(enumSet);
			copy.clear();
			return copy;
		}
		else if (collection instanceof SortedSet sortedSet) {
			return new TreeSet<>(sortedSet.comparator());
		}
		else if (collection instanceof LinkedList) {
			return new LinkedList<>();
		}
		else if (collection instanceof List) {
			return new ArrayList<>(capacity);
		}
		else {
			return new LinkedHashSet<>(capacity);
		}
	}

	public static <E> Collection<E> createCollection(Class<?> collectionType, int capacity) {
		return createCollection(collectionType, null, capacity);
	}

	@SuppressWarnings("unchecked")
	public static <E> Collection<E> createCollection(Class<?> collectionType,  Class<?> elementType, int capacity) {
		Assert.notNull(collectionType, "Collection type must not be null");
		if (LinkedHashSet.class == collectionType ||
				Set.class == collectionType || Collection.class == collectionType ||
				collectionType.getName().equals("java.util.SequencedSet") ||
				collectionType.getName().equals("java.util.SequencedCollection")) {
			return new LinkedHashSet<>(capacity);
		}
		else if (ArrayList.class == collectionType || List.class == collectionType) {
			return new ArrayList<>(capacity);
		}
		else if (LinkedList.class == collectionType) {
			return new LinkedList<>();
		}
		else if (TreeSet.class == collectionType || NavigableSet.class == collectionType ||
				SortedSet.class == collectionType) {
			return new TreeSet<>();
		}
		else if (EnumSet.class.isAssignableFrom(collectionType)) {
			Assert.notNull(elementType, "Cannot create EnumSet for unknown element type");
			return EnumSet.noneOf(asEnumType(elementType));
		}
		else if (HashSet.class == collectionType) {
			return new HashSet<>(capacity);
		}
		else {
			if (collectionType.isInterface() || !Collection.class.isAssignableFrom(collectionType)) {
				throw new IllegalArgumentException("Unsupported Collection type: " + collectionType.getName());
			}
			try {
				return (Collection<E>) ReflectionUtils.accessibleConstructor(collectionType).newInstance();
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException(
					"Could not instantiate Collection type: " + collectionType.getName(), ex);
			}
		}
	}

	public static boolean isApproximableMapType( Class<?> mapType) {
		return (mapType != null && (approximableMapTypes.contains(mapType) ||
				mapType.getName().equals("java.util.SequencedMap")));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static <K, V> Map<K, V> createApproximateMap( Object map, int capacity) {
		if (map instanceof EnumMap enumMap) {
			EnumMap copy = new EnumMap(enumMap);
			copy.clear();
			return copy;
		}
		else if (map instanceof SortedMap sortedMap) {
			return new TreeMap<>(sortedMap.comparator());
		}
		else if (map instanceof MultiValueMap) {
			return new LinkedMultiValueMap(capacity);
		}
		else {
			return new LinkedHashMap<>(capacity);
		}
	}

	public static <K, V> Map<K, V> createMap(Class<?> mapType, int capacity) {
		return createMap(mapType, null, capacity);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static <K, V> Map<K, V> createMap(Class<?> mapType,  Class<?> keyType, int capacity) {
		Assert.notNull(mapType, "Map type must not be null");
		if (LinkedHashMap.class == mapType || Map.class == mapType ||
				mapType.getName().equals("java.util.SequencedMap")) {
			return new LinkedHashMap<>(capacity);
		}
		else if (LinkedMultiValueMap.class == mapType || MultiValueMap.class == mapType) {
			return new LinkedMultiValueMap();
		}
		else if (TreeMap.class == mapType || SortedMap.class == mapType || NavigableMap.class == mapType) {
			return new TreeMap<>();
		}
		else if (EnumMap.class == mapType) {
			Assert.notNull(keyType, "Cannot create EnumMap for unknown key type");
			return new EnumMap(asEnumType(keyType));
		}
		else if (HashMap.class == mapType) {
			return new HashMap<>(capacity);
		}
		else {
			if (mapType.isInterface() || !Map.class.isAssignableFrom(mapType)) {
				throw new IllegalArgumentException("Unsupported Map type: " + mapType.getName());
			}
			try {
				return (Map<K, V>) ReflectionUtils.accessibleConstructor(mapType).newInstance();
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Could not instantiate Map type: " + mapType.getName(), ex);
			}
		}
	}

	@SuppressWarnings("serial")
	public static Properties createStringAdaptingProperties() {
		return new SortedProperties(false) {
			@Override
			public  String getProperty(String key) {
				Object value = get(key);
				return (value != null ? value.toString() : null);
			}
		};
	}

	public static Properties createSortedProperties(boolean omitComments) {
		return new SortedProperties(omitComments);
	}

	public static Properties createSortedProperties(Properties properties, boolean omitComments) {
		return new SortedProperties(properties, omitComments);
	}

	@SuppressWarnings("rawtypes")
	private static Class<? extends Enum> asEnumType(Class<?> enumType) {
		Assert.notNull(enumType, "Enum type must not be null");
		if (!Enum.class.isAssignableFrom(enumType)) {
			throw new IllegalArgumentException("Supplied type is not an enum: " + enumType.getName());
		}
		return enumType.asSubclass(Enum.class);
	}

}
