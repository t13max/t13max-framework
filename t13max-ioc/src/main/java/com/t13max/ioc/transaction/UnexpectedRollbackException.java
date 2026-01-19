package com.t13max.ioc.transaction;

@SuppressWarnings("serial")
public class UnexpectedRollbackException extends TransactionException {

	public UnexpectedRollbackException(String msg) {
		super(msg);
	}

	public UnexpectedRollbackException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
