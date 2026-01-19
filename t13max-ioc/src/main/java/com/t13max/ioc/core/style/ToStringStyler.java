package com.t13max.ioc.core.style;

import org.jspecify.annotations.Nullable;

public interface ToStringStyler {

	
	void styleStart(StringBuilder buffer, Object obj);

	
	void styleEnd(StringBuilder buffer, Object obj);

	
	void styleField(StringBuilder buffer, String fieldName,  Object value);

	
	void styleValue(StringBuilder buffer, Object value);

	
	void styleFieldSeparator(StringBuilder buffer);

}
