package com.t13max.ioc.transaction;

import com.t13max.ioc.core.NestedRuntimeException;

@SuppressWarnings("serial")
public abstract class TransactionException extends NestedRuntimeException {

	public TransactionException(String msg) {
		super(msg);
	}

	public TransactionException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
