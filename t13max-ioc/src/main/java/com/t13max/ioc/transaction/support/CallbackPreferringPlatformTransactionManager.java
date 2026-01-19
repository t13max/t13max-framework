package com.t13max.ioc.transaction.support;



import com.t13max.ioc.transaction.PlatformTransactionManager;
import com.t13max.ioc.transaction.TransactionDefinition;
import com.t13max.ioc.transaction.TransactionException;

public interface CallbackPreferringPlatformTransactionManager extends PlatformTransactionManager {

	<T>  T execute( TransactionDefinition definition, TransactionCallback<T> callback)
			throws TransactionException;

}
