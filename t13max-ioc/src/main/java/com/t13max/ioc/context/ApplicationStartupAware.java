package com.t13max.ioc.context;

import com.t13max.ioc.beans.factory.Aware;
import com.t13max.ioc.core.metrics.ApplicationStartup;

/**
 * @Author: t13max
 * @Since: 21:39 2026/1/16
 */
public interface ApplicationStartupAware extends Aware {

    void setApplicationStartup(ApplicationStartup applicationStartup);
}
