package com.t13max.ioc.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import reactor.adapter.JdkFlowAdapter;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ConcurrentReferenceHashMap;
import com.t13max.ioc.util.ReflectionUtils;

public class ReactiveAdapterRegistry {
	private static volatile  ReactiveAdapterRegistry sharedInstance;
	private static final boolean reactiveStreamsPresent;
	private static final boolean reactorPresent;
	private static final boolean rxjava3Present;
	private static final boolean kotlinCoroutinesPresent;
	private static final boolean mutinyPresent;
	static {
		ClassLoader classLoader = ReactiveAdapterRegistry.class.getClassLoader();
		reactiveStreamsPresent = ClassUtils.isPresent("org.reactivestreams.Publisher", classLoader);
		reactorPresent = ClassUtils.isPresent("reactor.core.publisher.Flux", classLoader);
		rxjava3Present = ClassUtils.isPresent("io.reactivex.rxjava3.core.Flowable", classLoader);
		kotlinCoroutinesPresent = ClassUtils.isPresent("kotlinx.coroutines.reactor.MonoKt", classLoader);
		mutinyPresent = ClassUtils.isPresent("io.smallrye.mutiny.Multi", classLoader);
	}
	private final List<ReactiveAdapter> adapters = new ArrayList<>();


	public ReactiveAdapterRegistry() {
		// Defensive guard for the Reactive Streams API itself
		if (!reactiveStreamsPresent) {
			return;
		}
		// Reactor
		if (reactorPresent) {
			new ReactorRegistrar().registerAdapters(this);
		}
		// RxJava
		if (rxjava3Present) {
			new RxJava3Registrar().registerAdapters(this);
		}
		// Kotlin Coroutines
		if (reactorPresent && kotlinCoroutinesPresent) {
			new CoroutinesRegistrar().registerAdapters(this);
		}
		// SmallRye Mutiny
		if (mutinyPresent) {
			new MutinyRegistrar().registerAdapters(this);
		}
		// Simple Flow.Publisher bridge if Reactor is not present
		if (!reactorPresent) {
			new FlowAdaptersRegistrar().registerAdapters(this);
		}
	}


	public void registerReactiveType(ReactiveTypeDescriptor descriptor,
			Function<Object, Publisher<?>> toAdapter, Function<Publisher<?>, Object> fromAdapter) {
		this.adapters.add(buildAdapter(descriptor, toAdapter, fromAdapter));
	}

	public void registerReactiveTypeOverride(ReactiveTypeDescriptor descriptor,
			Function<Object, Publisher<?>> toAdapter, Function<Publisher<?>, Object> fromAdapter) {
		this.adapters.add(0, buildAdapter(descriptor, toAdapter, fromAdapter));
	}
	private ReactiveAdapter buildAdapter(ReactiveTypeDescriptor descriptor,
			Function<Object, Publisher<?>> toAdapter, Function<Publisher<?>, Object> fromAdapter) {
		return (reactorPresent ? new ReactorAdapter(descriptor, toAdapter, fromAdapter) :
				new ReactiveAdapter(descriptor, toAdapter, fromAdapter));
	}

	public boolean hasAdapters() {
		return !this.adapters.isEmpty();
	}

	public  ReactiveAdapter getAdapter(Class<?> reactiveType) {
		return getAdapter(reactiveType, null);
	}

	public  ReactiveAdapter getAdapter( Class<?> reactiveType,  Object source) {
		if (this.adapters.isEmpty()) {
			return null;
		}
		Object sourceToUse = (source instanceof Optional<?> optional ? optional.orElse(null) : source);
		Class<?> clazz = (sourceToUse != null ? sourceToUse.getClass() : reactiveType);
		if (clazz == null) {
			return null;
		}
		for (ReactiveAdapter adapter : this.adapters) {
			if (adapter.getReactiveType() == clazz) {
				return adapter;
			}
		}
		for (ReactiveAdapter adapter : this.adapters) {
			if (adapter.getReactiveType().isAssignableFrom(clazz)) {
				return adapter;
			}
		}
		return null;
	}


	public static ReactiveAdapterRegistry getSharedInstance() {
		ReactiveAdapterRegistry registry = sharedInstance;
		if (registry == null) {
			synchronized (ReactiveAdapterRegistry.class) {
				registry = sharedInstance;
				if (registry == null) {
					registry = new ReactiveAdapterRegistry();
					sharedInstance = registry;
				}
			}
		}
		return registry;
	}


	private static class ReactorAdapter extends ReactiveAdapter {
		ReactorAdapter(ReactiveTypeDescriptor descriptor,
				Function<Object, Publisher<?>> toPublisherFunction,
				Function<Publisher<?>, Object> fromPublisherFunction) {
			super(descriptor, toPublisherFunction, fromPublisherFunction);
		}
		@Override
		public <T> Publisher<T> toPublisher( Object source) {
			Publisher<T> publisher = super.toPublisher(source);
			return (isMultiValue() ? Flux.from(publisher) : Mono.from(publisher));
		}
	}

	private static class ReactorRegistrar {
		private static final Flow.Publisher<?> EMPTY_FLOW = JdkFlowAdapter.publisherToFlowPublisher(Flux.empty());
		void registerAdapters(ReactiveAdapterRegistry registry) {
			// Register Flux and Mono before Publisher...
			registry.registerReactiveType(
					ReactiveTypeDescriptor.singleOptionalValue(Mono.class, Mono::empty),
					source -> (Mono<?>) source,
					Mono::from);
			registry.registerReactiveType(
					ReactiveTypeDescriptor.multiValue(Flux.class, Flux::empty),
					source -> (Flux<?>) source,
					Flux::from);
			registry.registerReactiveType(
					ReactiveTypeDescriptor.multiValue(Publisher.class, Flux::empty),
					source -> (Publisher<?>) source,
					source -> source);
			registry.registerReactiveType(
					ReactiveTypeDescriptor.nonDeferredAsyncValue(CompletionStage.class, EmptyCompletableFuture::new),
					source -> Mono.fromCompletionStage((CompletionStage<?>) source),
					source -> Mono.from(source).toFuture());
			registry.registerReactiveType(
					ReactiveTypeDescriptor.multiValue(Flow.Publisher.class, () -> EMPTY_FLOW),
					source -> JdkFlowAdapter.flowPublisherToFlux((Flow.Publisher<?>) source),
					JdkFlowAdapter::publisherToFlowPublisher);
		}
	}

	private static class EmptyCompletableFuture<T> extends CompletableFuture<T> {
		EmptyCompletableFuture() {
			complete(null);
		}
	}

	private static class RxJava3Registrar {
		void registerAdapters(ReactiveAdapterRegistry registry) {
			registry.registerReactiveType(
					ReactiveTypeDescriptor.multiValue(
							io.reactivex.rxjava3.core.Flowable.class,
							io.reactivex.rxjava3.core.Flowable::empty),
					source -> (io.reactivex.rxjava3.core.Flowable<?>) source,
					io.reactivex.rxjava3.core.Flowable::fromPublisher);
			registry.registerReactiveType(
					ReactiveTypeDescriptor.multiValue(
							io.reactivex.rxjava3.core.Observable.class,
							io.reactivex.rxjava3.core.Observable::empty),
					source -> ((io.reactivex.rxjava3.core.Observable<?>) source).toFlowable(
							io.reactivex.rxjava3.core.BackpressureStrategy.BUFFER),
					io.reactivex.rxjava3.core.Observable::fromPublisher);
			registry.registerReactiveType(
					ReactiveTypeDescriptor.singleRequiredValue(io.reactivex.rxjava3.core.Single.class),
					source -> ((io.reactivex.rxjava3.core.Single<?>) source).toFlowable(),
					io.reactivex.rxjava3.core.Single::fromPublisher);
			registry.registerReactiveType(
					ReactiveTypeDescriptor.singleOptionalValue(
							io.reactivex.rxjava3.core.Maybe.class,
							io.reactivex.rxjava3.core.Maybe::empty),
					source -> ((io.reactivex.rxjava3.core.Maybe<?>) source).toFlowable(),
					io.reactivex.rxjava3.core.Maybe::fromPublisher);
			registry.registerReactiveType(
					ReactiveTypeDescriptor.noValue(
							io.reactivex.rxjava3.core.Completable.class,
							io.reactivex.rxjava3.core.Completable::complete),
					source -> ((io.reactivex.rxjava3.core.Completable) source).toFlowable(),
					io.reactivex.rxjava3.core.Completable::fromPublisher);
		}
	}

	private static class CoroutinesRegistrar {
		@SuppressWarnings("KotlinInternalInJava")
		void registerAdapters(ReactiveAdapterRegistry registry) {
			registry.registerReactiveType(
					ReactiveTypeDescriptor.singleOptionalValue(kotlinx.coroutines.Deferred.class,
							() -> kotlinx.coroutines.CompletableDeferredKt.CompletableDeferred(null)),
					source -> CoroutinesUtils.deferredToMono((kotlinx.coroutines.Deferred<?>) source),
					source -> CoroutinesUtils.monoToDeferred(Mono.from(source)));
			registry.registerReactiveType(
					ReactiveTypeDescriptor.multiValue(kotlinx.coroutines.flow.Flow.class, kotlinx.coroutines.flow.FlowKt::emptyFlow),
					source -> kotlinx.coroutines.reactor.ReactorFlowKt.asFlux((kotlinx.coroutines.flow.Flow<?>) source),
					kotlinx.coroutines.reactive.ReactiveFlowKt::asFlow);
		}
	}

	private static class MutinyRegistrar {
		private static final Method uniToPublisher = ClassUtils.getMethod(io.smallrye.mutiny.groups.UniConvert.class, "toPublisher");
		@SuppressWarnings("unchecked")
		void registerAdapters(ReactiveAdapterRegistry registry) {
			ReactiveTypeDescriptor uniDesc = ReactiveTypeDescriptor.singleOptionalValue(
					io.smallrye.mutiny.Uni.class,
					() -> io.smallrye.mutiny.Uni.createFrom().nothing());
			ReactiveTypeDescriptor multiDesc = ReactiveTypeDescriptor.multiValue(
					io.smallrye.mutiny.Multi.class,
					() -> io.smallrye.mutiny.Multi.createFrom().empty());
			if (Flow.Publisher.class.isAssignableFrom(uniToPublisher.getReturnType())) {
				// Mutiny 2 based on Flow.Publisher
				Method uniPublisher = ClassUtils.getMethod(
						io.smallrye.mutiny.groups.UniCreate.class, "publisher", Flow.Publisher.class);
				Method multiPublisher = ClassUtils.getMethod(
						io.smallrye.mutiny.groups.MultiCreate.class, "publisher", Flow.Publisher.class);
				registry.registerReactiveType(uniDesc,
						uni -> FlowAdapters.toPublisher((Flow.Publisher<Object>)
								Objects.requireNonNull(ReflectionUtils.invokeMethod(uniToPublisher, ((Uni<?>) uni).convert()))),
						publisher -> Objects.requireNonNull(ReflectionUtils.invokeMethod(uniPublisher, Uni.createFrom(),
								FlowAdapters.toFlowPublisher(publisher))));
				registry.registerReactiveType(multiDesc,
						multi -> FlowAdapters.toPublisher((Flow.Publisher<Object>) multi),
						publisher -> Objects.requireNonNull(ReflectionUtils.invokeMethod(multiPublisher, Multi.createFrom(),
								FlowAdapters.toFlowPublisher(publisher))));
			}
			else {
				// Mutiny 1 based on Reactive Streams
				registry.registerReactiveType(uniDesc,
						uni -> ((io.smallrye.mutiny.Uni<?>) uni).convert().toPublisher(),
						publisher -> io.smallrye.mutiny.Uni.createFrom().publisher(publisher));
				registry.registerReactiveType(multiDesc,
						multi -> (io.smallrye.mutiny.Multi<?>) multi,
						publisher -> io.smallrye.mutiny.Multi.createFrom().publisher(publisher));
			}
		}
	}

	private static class FlowAdaptersRegistrar {
		private static final Flow.Subscription EMPTY_SUBSCRIPTION = new Flow.Subscription() {
			@Override
			public void request(long n) {
			}
			@Override
			public void cancel() {
			}
		};
		private static final Flow.Publisher<Object> EMPTY_PUBLISHER = subscriber -> {
			subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
			subscriber.onComplete();
		};
		@SuppressWarnings("unchecked")
		void registerAdapters(ReactiveAdapterRegistry registry) {
			registry.registerReactiveType(
					ReactiveTypeDescriptor.multiValue(Flow.Publisher.class, () -> EMPTY_PUBLISHER),
					source -> FlowAdapters.toPublisher((Flow.Publisher<Object>) source),
					source -> FlowAdapters.toFlowPublisher((Publisher<Object>) source));
		}
	}


	public static class SpringCoreBlockHoundIntegration implements BlockHoundIntegration {
		@Override
		public void applyTo(BlockHound.Builder builder) {
			// Avoid hard references potentially anywhere in spring-core (no need for structural dependency)
			String segmentClassName = "org.springframework.util.ConcurrentReferenceHashMap$Segment";
			builder.allowBlockingCallsInside(segmentClassName, "doTask");
			builder.allowBlockingCallsInside(segmentClassName, "clear");
			builder.allowBlockingCallsInside(segmentClassName, "restructure");
			String referenceManagerClassName = "org.springframework.util.ConcurrentReferenceHashMap$ReferenceManager";
			builder.allowBlockingCallsInside(referenceManagerClassName, "pollForPurge");
		}
	}

}
