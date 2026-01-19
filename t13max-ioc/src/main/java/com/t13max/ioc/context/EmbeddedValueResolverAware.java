package com.t13max.ioc.context;

import com.t13max.ioc.util.StringValueResolver;

/**
 * @Author: t13max
 * @Since: 20:34 2026/1/16
 */
public interface EmbeddedValueResolverAware {

    void setEmbeddedValueResolver(StringValueResolver resolver);
}
