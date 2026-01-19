package com.t13max.ioc.context.annotation;

import com.t13max.ioc.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @Author: t13max
 * @Since: 8:01 2026/1/17
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scope {

    @AliasFor("scopeName")
    String value() default "";

    @AliasFor("value")
    String scopeName() default "";

    ScopedProxyMode proxyMode() default ScopedProxyMode.DEFAULT;
}
