package com.t13max.ioc.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.full.KClassifiers;
import kotlin.reflect.full.KTypes;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactor.MonoKt;
import kotlinx.coroutines.reactor.ReactorFlowKt;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.CollectionUtils;

public abstract class CoroutinesUtils {
	private static final KType flowType = KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Flow.class));
	private static final KType monoType = KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Mono.class));
	private static final KType publisherType = KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Publisher.class));
	
	public static <T> Mono<T> deferredToMono(Deferred<T> source) {
		return MonoKt.mono(Dispatchers.getUnconfined(),
				(scope, continuation) -> source.await(continuation));
	}	
	public static <T> Deferred<T> monoToDeferred(Mono<T> source) {
		return BuildersKt.async(GlobalScope.INSTANCE, Dispatchers.getUnconfined(),
				CoroutineStart.DEFAULT,
				(scope, continuation) -> MonoKt.awaitSingleOrNull(source, continuation));
	}	
	public static Publisher<?> invokeSuspendingFunction(Method method, Object target,  Object... args) {
		return invokeSuspendingFunction(Dispatchers.getUnconfined(), method, target, args);
	}	
	@SuppressWarnings({"DataFlowIssue", "NullAway"})
	public static Publisher<?> invokeSuspendingFunction(
			CoroutineContext context, Method method,  Object target,  Object... args) {
		Assert.isTrue(KotlinDetector.isSuspendingFunction(method), "Method must be a suspending function");
		KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
		Assert.notNull(function, () -> "Failed to get Kotlin function for method: " + method);
		if (!KCallablesJvm.isAccessible(function)) {
			KCallablesJvm.setAccessible(function, true);
		}
		Mono<Object> mono = MonoKt.mono(context, (scope, continuation) -> {
					Map<KParameter, Object> argMap = CollectionUtils.newHashMap(args.length + 1);
					int index = 0;
					for (KParameter parameter : function.getParameters()) {
						switch (parameter.getKind()) {
							case INSTANCE -> argMap.put(parameter, target);
							case VALUE, EXTENSION_RECEIVER -> {
								Object arg = args[index];
								if (!(parameter.isOptional() && arg == null)) {
									KType type = parameter.getType();
									if (!(type.isMarkedNullable() && arg == null) &&
											type.getClassifier() instanceof KClass<?> kClass &&
											KotlinDetector.isInlineClass(JvmClassMappingKt.getJavaClass(kClass))) {
										arg = box(kClass, arg);
									}
									argMap.put(parameter, arg);
								}
								index++;
							}
						}
					}
					return KCallables.callSuspendBy(function, argMap, continuation);
				})
				.filter(result -> result != Unit.INSTANCE)
				.onErrorMap(InvocationTargetException.class, InvocationTargetException::getTargetException);
		KType returnType = function.getReturnType();
		if (KTypes.isSubtypeOf(returnType, flowType)) {
			return mono.flatMapMany(CoroutinesUtils::asFlux);
		}
		if (KTypes.isSubtypeOf(returnType, publisherType)) {
			if (KTypes.isSubtypeOf(returnType, monoType)) {
				return mono.flatMap(o -> ((Mono<?>)o));
			}
			return mono.flatMapMany(o -> ((Publisher<?>)o));
		}
		return mono;
	}
	private static Object box(KClass<?> kClass,  Object arg) {
		KFunction<?> constructor = Objects.requireNonNull(KClasses.getPrimaryConstructor(kClass));
		KType type = constructor.getParameters().get(0).getType();
		if (!(type.isMarkedNullable() && arg == null) &&
				type.getClassifier() instanceof KClass<?> parameterClass &&
				KotlinDetector.isInlineClass(JvmClassMappingKt.getJavaClass(parameterClass))) {
			arg = box(parameterClass, arg);
		}
		if (!KCallablesJvm.isAccessible(constructor)) {
			KCallablesJvm.setAccessible(constructor, true);
		}
		return constructor.call(arg);
	}
	private static Flux<?> asFlux(Object flow) {
		return ReactorFlowKt.asFlux(((Flow<?>) flow));
	}

}
