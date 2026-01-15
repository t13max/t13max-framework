package com.t13max.ioc.core.io.support;

import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.utils.Assert;

import java.io.IOException;

/**
 * @Author: t13max
 * @Since: 21:45 2026/1/15
 */
public class PathMatchingResourcePatternResolver implements ResourcePatternResolver{

    private final ResourceLoader resourceLoader;

    public PathMatchingResourcePatternResolver(ResourceLoader resourceLoader) {
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Resource[] getResources(String locationPattern) throws IOException {
        return new Resource[0];
    }
}
