package com.t13max.ioc.beans.factory.annotation;

import java.lang.annotation.*;

/**
 * 自动注入注解
 *
 * @Author: t13max
 * @Since: 22:08 2026/1/14
 */
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {
}
