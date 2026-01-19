package com.t13max.ioc.transaction;

import java.io.Flushable;

public interface TransactionStatus extends TransactionExecution, SavepointManager, Flushable {

	default boolean hasSavepoint() {
		return false;
	}

	@Override
	default void flush() {
	}

}
