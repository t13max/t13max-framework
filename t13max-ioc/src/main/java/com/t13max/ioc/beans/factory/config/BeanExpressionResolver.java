package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeansException;

/**
 * @author t13max
 * @since 11:07 2026/1/16
 */
public interface BeanExpressionResolver {
    Object evaluate(String value, BeanExpressionContext beanExpressionContext) throws BeansException;
}
