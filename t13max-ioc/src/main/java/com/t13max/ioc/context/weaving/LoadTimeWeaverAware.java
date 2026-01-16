package com.t13max.ioc.context.weaving;

import com.t13max.ioc.beans.factory.Aware;

/**
 * @Author: t13max
 * @Since: 21:34 2026/1/16
 */
public interface LoadTimeWeaverAware extends Aware {

    void setLoadTimeWeaver(LoadTimeWeaver loadTimeWeaver);
}
