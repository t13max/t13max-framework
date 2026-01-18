package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;



public class CharArrayPropertyEditor extends PropertyEditorSupport {
	@Override
	public void setAsText( String text) {
		setValue(text != null ? text.toCharArray() : null);
	}
	@Override
	public String getAsText() {
		char[] value = (char[]) getValue();
		return (value != null ? new String(value) : "");
	}

}
