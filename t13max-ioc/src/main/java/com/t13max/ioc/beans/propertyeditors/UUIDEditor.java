package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.UUID;

import com.t13max.ioc.util.StringUtils;

public class UUIDEditor extends PropertyEditorSupport {
	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		if (StringUtils.hasText(text)) {
			setValue(UUID.fromString(text.trim()));
		}
		else {
			setValue(null);
		}
	}
	@Override
	public String getAsText() {
		UUID value = (UUID) getValue();
		return (value != null ? value.toString() : "");
	}

}
