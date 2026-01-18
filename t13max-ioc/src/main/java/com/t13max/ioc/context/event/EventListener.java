package com.t13max.ioc.context.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

import com.t13max.ioc.aot.hint.annotation.Reflective;
import com.t13max.ioc.context.ApplicationEvent;
import com.t13max.ioc.core.annotation.AliasFor;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Reflective
public @interface EventListener {

	@AliasFor("classes")
	Class<?>[] value() default {};

	@AliasFor("value")
	Class<?>[] classes() default {};

	String condition() default "";

	boolean defaultExecution() default true;

	String id() default "";

}
