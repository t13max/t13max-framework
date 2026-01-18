package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.regex.Pattern;



public class PatternEditor extends PropertyEditorSupport {
	private final int flags;
	
	public PatternEditor() {
		this.flags = 0;
	}	
	public PatternEditor(int flags) {
		this.flags = flags;
	}

	@Override
	public void setAsText( String text) {
		setValue(text != null ? Pattern.compile(text, this.flags) : null);
	}
	@Override
	public String getAsText() {
		Pattern value = (Pattern) getValue();
		return (value != null ? value.pattern() : "");
	}

}
