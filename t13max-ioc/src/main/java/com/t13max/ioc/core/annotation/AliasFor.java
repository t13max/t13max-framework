package com.t13max.ioc.core.annotation;

import java.lang.annotation.*;

/**
 * @Author: t13max
 * @Since: 22:32 2026/1/15
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface AliasFor {

    @AliasFor("attribute")
    String value() default "";

    @AliasFor("value")
    String attribute() default "";

    Class<? extends Annotation> annotation() default Annotation.class;
}
