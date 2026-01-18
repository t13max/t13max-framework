package com.t13max.ioc.transaction;



import com.t13max.ioc.util.Assert;

@SuppressWarnings("serial")
public class TransactionSystemException extends TransactionException {
	private  Throwable applicationException;


	public TransactionSystemException(String msg) {
		super(msg);
	}

	public TransactionSystemException(String msg, Throwable cause) {
		super(msg, cause);
	}


	public void initApplicationException(Throwable ex) {
		Assert.notNull(ex, "Application exception must not be null");
		if (this.applicationException != null) {
			throw new IllegalStateException("Already holding an application exception: " + this.applicationException);
		}
		this.applicationException = ex;
	}

	public final  Throwable getApplicationException() {
		return this.applicationException;
	}

	public  Throwable getOriginalException() {
		return (this.applicationException != null ? this.applicationException : getCause());
	}
	@Override
	public boolean contains( Class<?> exType) {
		return super.contains(exType) || (exType != null && exType.isInstance(this.applicationException));
	}

}
