package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.HexFormat;



import com.t13max.ioc.util.StringUtils;

public class CharacterEditor extends PropertyEditorSupport {

	private static final String UNICODE_PREFIX = "\\u";

	private static final int UNICODE_LENGTH = 6;

	private final boolean allowEmpty;


	public CharacterEditor(boolean allowEmpty) {
		this.allowEmpty = allowEmpty;
	}

	@Override
	public void setAsText( String text) throws IllegalArgumentException {
		if (this.allowEmpty && !StringUtils.hasLength(text)) {
			// Treat empty String as null value.
			setValue(null);
		}
		else if (text == null) {
			throw new IllegalArgumentException("null String cannot be converted to char type");
		}
		else if (isUnicodeCharacterSequence(text)) {
			setAsUnicode(text);
		}
		else if (text.length() == 1) {
			setValue(text.charAt(0));
		}
		else {
			throw new IllegalArgumentException("String [" + text + "] with length " +
					text.length() + " cannot be converted to char type: neither Unicode nor single character");
		}
	}
	@Override
	public String getAsText() {
		Object value = getValue();
		return (value != null ? value.toString() : "");
	}
	private static boolean isUnicodeCharacterSequence(String sequence) {
		return (sequence.startsWith(UNICODE_PREFIX) && sequence.length() == UNICODE_LENGTH);
	}
	private void setAsUnicode(String text) {
		int code = HexFormat.fromHexDigits(text, UNICODE_PREFIX.length(), text.length());
		setValue((char) code);
	}

}
