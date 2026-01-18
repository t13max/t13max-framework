package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;



import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ReflectionUtils;

public class CustomMapEditor extends PropertyEditorSupport {
	@SuppressWarnings("rawtypes")
	private final Class<? extends Map> mapType;
	private final boolean nullAsEmptyMap;


	@SuppressWarnings("rawtypes")
	public CustomMapEditor(Class<? extends Map> mapType) {
		this(mapType, false);
	}

	@SuppressWarnings("rawtypes")
	public CustomMapEditor(Class<? extends Map> mapType, boolean nullAsEmptyMap) {
		Assert.notNull(mapType, "Map type is required");
		if (!Map.class.isAssignableFrom(mapType)) {
			throw new IllegalArgumentException(
					"Map type [" + mapType.getName() + "] does not implement [java.util.Map]");
		}
		this.mapType = mapType;
		this.nullAsEmptyMap = nullAsEmptyMap;
	}


	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		setValue(text);
	}

	@Override
	public void setValue( Object value) {
		if (value == null && this.nullAsEmptyMap) {
			super.setValue(createMap(this.mapType, 0));
		}
		else if (value == null || (this.mapType.isInstance(value) && !alwaysCreateNewMap())) {
			// Use the source value as-is, as it matches the target type.
			super.setValue(value);
		}
		else if (value instanceof Map<?, ?> source) {
			// Convert Map elements.
			Map<Object, Object> target = createMap(this.mapType, source.size());
			source.forEach((key, val) -> target.put(convertKey(key), convertValue(val)));
			super.setValue(target);
		}
		else {
			throw new IllegalArgumentException("Value cannot be converted to Map: " + value);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	protected Map<Object, Object> createMap(Class<? extends Map> mapType, int initialCapacity) {
		if (!mapType.isInterface()) {
			try {
				return ReflectionUtils.accessibleConstructor(mapType).newInstance();
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException(
						"Could not instantiate map class: " + mapType.getName(), ex);
			}
		}
		else if (SortedMap.class == mapType) {
			return new TreeMap<>();
		}
		else {
			return new LinkedHashMap<>(initialCapacity);
		}
	}

	protected boolean alwaysCreateNewMap() {
		return false;
	}

	protected Object convertKey(Object key) {
		return key;
	}

	protected Object convertValue(Object value) {
		return value;
	}


	@Override
	public  String getAsText() {
		return null;
	}

}
