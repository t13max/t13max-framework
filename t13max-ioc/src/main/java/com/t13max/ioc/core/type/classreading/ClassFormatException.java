package com.t13max.ioc.core.type.classreading;

import java.io.IOException;

import com.t13max.ioc.core.io.Resource;

@SuppressWarnings("serial")
public class ClassFormatException extends IOException {	
	public ClassFormatException(String message) {
		super(message);
	}	
	public ClassFormatException(String message, Throwable cause) {
		super(message, cause);
	}

}
