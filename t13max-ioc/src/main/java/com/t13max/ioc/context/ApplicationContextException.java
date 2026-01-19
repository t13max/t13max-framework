package com.t13max.ioc.context;

import com.t13max.ioc.beans.FatalBeanException;

@SuppressWarnings("serial")
public class ApplicationContextException extends FatalBeanException {	
	public ApplicationContextException(String msg) {
		super(msg);
	}	
	public ApplicationContextException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
