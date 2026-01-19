package com.t13max.ioc.transaction;



public interface PlatformTransactionManager extends TransactionManager {	
	TransactionStatus getTransaction( TransactionDefinition definition) throws TransactionException;	
	void commit(TransactionStatus status) throws TransactionException;	
	void rollback(TransactionStatus status) throws TransactionException;

}
