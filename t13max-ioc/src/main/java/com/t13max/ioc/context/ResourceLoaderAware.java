package com.t13max.ioc.context;

import com.t13max.ioc.core.io.ResourceLoader;

/**
 * @Author: t13max
 * @Since: 20:46 2026/1/16
 */
public interface ResourceLoaderAware {

    void setResourceLoader(ResourceLoader resourceLoader);
}
