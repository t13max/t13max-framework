package com.t13max.ioc.stereotype;

import java.lang.annotation.*;

/**
 * @Author: t13max
 * @Since: 22:31 2026/1/15
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed
public @interface Component {
    String value() default "";
}
