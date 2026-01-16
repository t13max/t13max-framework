package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.framework.adapter.AdvisorAdapter;
import com.t13max.ioc.aop.framework.adapter.AdvisorAdapterRegistry;
import com.t13max.ioc.aop.framework.adapter.DefaultAdvisorAdapterRegistry;

/**
 * @Author: t13max
 * @Since: 21:55 2026/1/16
 */
public class GlobalAdvisorAdapterRegistry {

    private GlobalAdvisorAdapterRegistry() {

    }

    private static AdvisorAdapterRegistry instance = new DefaultAdvisorAdapterRegistry();

    public static AdvisorAdapterRegistry getInstance() {
        return instance;
    }

    static void reset() {
        instance = new DefaultAdvisorAdapterRegistry();
    }
}
