package com.t13max.ioc.transaction;


import reactor.core.publisher.Mono;

public interface ReactiveTransactionManager extends TransactionManager {

	Mono<ReactiveTransaction> getReactiveTransaction( TransactionDefinition definition);

	Mono<Void> commit(ReactiveTransaction transaction);

	Mono<Void> rollback(ReactiveTransaction transaction);

}
