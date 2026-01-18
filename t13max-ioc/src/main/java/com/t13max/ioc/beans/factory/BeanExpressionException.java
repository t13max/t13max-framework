package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.FatalBeanException;

@SuppressWarnings("serial")
public class BeanExpressionException extends FatalBeanException {	
	public BeanExpressionException(String msg) {
		super(msg);
	}	
	public BeanExpressionException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
