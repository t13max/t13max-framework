package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;

import com.t13max.ioc.util.StringUtils;

public class LocaleEditor extends PropertyEditorSupport {
	@Override
	public void setAsText(String text) {
		setValue(StringUtils.parseLocale(text));
	}
	@Override
	public String getAsText() {
		Object value = getValue();
		return (value != null ? value.toString() : "");
	}

}
