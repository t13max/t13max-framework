package com.t13max.ioc.context.annotation;

import com.t13max.ioc.core.type.AnnotatedTypeMetadata;

@FunctionalInterface
public interface Condition {

	boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
