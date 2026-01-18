package com.t13max.ioc.core;

import java.util.function.Function;


import org.reactivestreams.Publisher;

import com.t13max.ioc.util.Assert;

public class ReactiveAdapter {
	private final ReactiveTypeDescriptor descriptor;
	private final Function<Object, Publisher<?>> toPublisherFunction;
	private final Function<Publisher<?>, Object> fromPublisherFunction;


	public ReactiveAdapter(ReactiveTypeDescriptor descriptor,
			Function<Object, Publisher<?>> toPublisherFunction,
			Function<Publisher<?>, Object> fromPublisherFunction) {
		Assert.notNull(descriptor, "'descriptor' is required");
		Assert.notNull(toPublisherFunction, "'toPublisherFunction' is required");
		Assert.notNull(fromPublisherFunction, "'fromPublisherFunction' is required");
		this.descriptor = descriptor;
		this.toPublisherFunction = toPublisherFunction;
		this.fromPublisherFunction = fromPublisherFunction;
	}


	public ReactiveTypeDescriptor getDescriptor() {
		return this.descriptor;
	}

	public Class<?> getReactiveType() {
		return getDescriptor().getReactiveType();
	}

	public boolean isMultiValue() {
		return getDescriptor().isMultiValue();
	}

	public boolean isNoValue() {
		return getDescriptor().isNoValue();
	}

	public boolean supportsEmpty() {
		return getDescriptor().supportsEmpty();
	}


	@SuppressWarnings("unchecked")
	public <T> Publisher<T> toPublisher( Object source) {
		if (source == null) {
			source = getDescriptor().getEmptyValue();
		}
		return (Publisher<T>) this.toPublisherFunction.apply(source);
	}

	public Object fromPublisher(Publisher<?> publisher) {
		return this.fromPublisherFunction.apply(publisher);
	}

}
