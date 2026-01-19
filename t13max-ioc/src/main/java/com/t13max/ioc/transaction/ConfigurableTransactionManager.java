package com.t13max.ioc.transaction;

import java.util.Collection;

public interface ConfigurableTransactionManager extends TransactionManager {

	void setTransactionExecutionListeners(Collection<TransactionExecutionListener> listeners);

	Collection<TransactionExecutionListener> getTransactionExecutionListeners();

	default void addListener(TransactionExecutionListener listener) {
		getTransactionExecutionListeners().add(listener);
	}

}
