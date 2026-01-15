package com.t13max.ioc.lang;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * @Author: t13max
 * @Since: 20:51 2026/1/15
 */
@Documented
@Target(ElementType.METHOD)
public @interface Contract {
    String value() default "";
}
