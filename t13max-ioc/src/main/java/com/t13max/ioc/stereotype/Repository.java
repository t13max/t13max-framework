package com.t13max.ioc.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.t13max.ioc.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Repository {

	@AliasFor(annotation = Component.class)
	String value() default "";

}
