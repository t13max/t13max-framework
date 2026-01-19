package com.t13max.ioc.transaction;

@SuppressWarnings("serial")
public class InvalidTimeoutException extends TransactionUsageException {
	private final int timeout;

	public InvalidTimeoutException(String msg, int timeout) {
		super(msg);
		this.timeout = timeout;
	}
	public int getTimeout() {
		return this.timeout;
	}

}
