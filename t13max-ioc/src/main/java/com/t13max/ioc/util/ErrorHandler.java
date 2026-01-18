package com.t13max.ioc.util;

@FunctionalInterface
public interface ErrorHandler {

	
	void handleError(Throwable t);

}
