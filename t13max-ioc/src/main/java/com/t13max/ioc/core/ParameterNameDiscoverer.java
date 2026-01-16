package com.t13max.ioc.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @Author: t13max
 * @Since: 22:33 2026/1/16
 */
public interface ParameterNameDiscoverer {

    String[] getParameterNames(Method method);

    String[] getParameterNames(Constructor<?> ctor);
}
