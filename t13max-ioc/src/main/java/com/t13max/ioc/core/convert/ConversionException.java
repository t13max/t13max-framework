package com.t13max.ioc.core.convert;

import com.t13max.ioc.core.NestedRuntimeException;

@SuppressWarnings("serial")
public abstract class ConversionException extends NestedRuntimeException {	
	public ConversionException(String message) {
		super(message);
	}	
	public ConversionException(String message, Throwable cause) {
		super(message, cause);
	}

}
