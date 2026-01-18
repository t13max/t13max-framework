package com.t13max.ioc.transaction;



public interface TransactionExecutionListener {

	default void beforeBegin(TransactionExecution transaction) {
	}

	default void afterBegin(TransactionExecution transaction,  Throwable beginFailure) {
	}

	default void beforeCommit(TransactionExecution transaction) {
	}

	default void afterCommit(TransactionExecution transaction,  Throwable commitFailure) {
	}

	default void beforeRollback(TransactionExecution transaction) {
	}

	default void afterRollback(TransactionExecution transaction,  Throwable rollbackFailure) {
	}

}
