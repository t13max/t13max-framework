package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;



public class ByteArrayPropertyEditor extends PropertyEditorSupport {
	@Override
	public void setAsText( String text) {
		setValue(text != null ? text.getBytes() : null);
	}
	@Override
	public String getAsText() {
		byte[] value = (byte[]) getValue();
		return (value != null ? new String(value) : "");
	}

}
