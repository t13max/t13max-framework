package com.t13max.ioc.stereotype;

import com.t13max.ioc.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @Author: t13max
 * @Since: 22:31 2026/1/15
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Service {
    @AliasFor(annotation = Component.class)
    String value() default "";
}
