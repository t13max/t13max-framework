package com.t13max.ioc.transaction;

@SuppressWarnings("serial")
public class NestedTransactionNotSupportedException extends CannotCreateTransactionException {
	public NestedTransactionNotSupportedException(String msg) {
		super(msg);
	}
	public NestedTransactionNotSupportedException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
