package com.t13max.ioc.context.annotation;

import java.lang.annotation.*;

/**
 * @Author: t13max
 * @Since: 8:03 2026/1/17
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {

    Class<? extends Condition>[] value();
}
