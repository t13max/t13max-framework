package com.t13max.ioc.context;

import com.t13max.ioc.beans.factory.Aware;
import com.t13max.ioc.core.env.Environment;

/**
 * @Author: t13max
 * @Since: 21:37 2026/1/16
 */
public interface EnvironmentAware extends Aware {

    void setEnvironment(Environment environment);
}
