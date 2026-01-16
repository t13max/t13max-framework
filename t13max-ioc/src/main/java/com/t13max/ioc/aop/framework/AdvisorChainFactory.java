package com.t13max.ioc.aop.framework;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author t13max
 * @since 17:29 2026/1/16
 */
public interface AdvisorChainFactory {

    List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, Class<?> targetClass);
}
