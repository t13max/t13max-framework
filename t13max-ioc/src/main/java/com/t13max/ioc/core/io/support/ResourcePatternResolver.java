package com.t13max.ioc.core.io.support;

import com.t13max.ioc.core.io.Resource;

import java.io.IOException;

/**
 * @Author: t13max
 * @Since: 21:44 2026/1/15
 */
public interface ResourcePatternResolver {

    String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

    Resource[] getResources(String locationPattern) throws IOException;
}
