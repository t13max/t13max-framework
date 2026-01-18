package com.t13max.ioc.transaction.reactive;

import java.util.ArrayDeque;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import com.t13max.ioc.transaction.NoTransactionException;

public abstract class TransactionContextManager {
	private TransactionContextManager() {
	}


	public static Mono<TransactionContext> currentContext() {
		return Mono.deferContextual(ctx -> {
			if (ctx.hasKey(TransactionContext.class)) {
				return Mono.just(ctx.get(TransactionContext.class));
			}
			if (ctx.hasKey(TransactionContextHolder.class)) {
				TransactionContextHolder holder = ctx.get(TransactionContextHolder.class);
				if (holder.hasContext()) {
					return Mono.just(holder.currentContext());
				}
			}
			return Mono.error(new NoTransactionInContextException());
		});
	}

	public static Function<Context, Context> createTransactionContext() {
		return context -> context.put(TransactionContext.class, new TransactionContext());
	}

	public static Function<Context, Context> getOrCreateContext() {
		return context -> {
			TransactionContextHolder holder = context.get(TransactionContextHolder.class);
			if (holder.hasContext()) {
				return context.put(TransactionContext.class, holder.currentContext());
			}
			return context.put(TransactionContext.class, holder.createContext());
		};
	}

	public static Function<Context, Context> getOrCreateContextHolder() {
		return context -> {
			if (!context.hasKey(TransactionContextHolder.class)) {
				return context.put(TransactionContextHolder.class, new TransactionContextHolder(new ArrayDeque<>()));
			}
			return context;
		};
	}


	@SuppressWarnings("serial")
	private static class NoTransactionInContextException extends NoTransactionException {
		public NoTransactionInContextException() {
			super("No transaction in context");
		}
		@Override
		public synchronized Throwable fillInStackTrace() {
			// stackless exception
			return this;
		}
	}

}
