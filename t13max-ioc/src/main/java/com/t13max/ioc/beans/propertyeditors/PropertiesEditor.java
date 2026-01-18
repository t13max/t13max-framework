package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;



public class PropertiesEditor extends PropertyEditorSupport {

	@Override
	public void setAsText( String text) throws IllegalArgumentException {
		Properties props = new Properties();
		if (text != null) {
			try {
				// Must use the ISO-8859-1 encoding because Properties.load(stream) expects it.
				props.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
			}
			catch (IOException ex) {
				// Should never happen.
				throw new IllegalArgumentException(
						"Failed to parse [" + text + "] into Properties", ex);
			}
		}
		setValue(props);
	}

	@Override
	public void setValue(Object value) {
		if (!(value instanceof Properties) && value instanceof Map<?, ?> map) {
			Properties props = new Properties();
			props.putAll(map);
			super.setValue(props);
		}
		else {
			super.setValue(value);
		}
	}

}
