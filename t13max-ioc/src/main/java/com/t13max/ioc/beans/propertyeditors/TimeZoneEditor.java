package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.TimeZone;

import com.t13max.ioc.util.StringUtils;

public class TimeZoneEditor extends PropertyEditorSupport {
	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		if (StringUtils.hasText(text)) {
			text = text.trim();
		}
		setValue(StringUtils.parseTimeZoneString(text));
	}
	@Override
	public String getAsText() {
		TimeZone value = (TimeZone) getValue();
		return (value != null ? value.getID() : "");
	}

}
