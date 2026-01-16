package com.t13max.ioc.core;

/**
 * @Author: t13max
 * @Since: 22:33 2026/1/16
 */
public class DefaultParameterNameDiscoverer extends PrioritizedParameterNameDiscoverer {

    public DefaultParameterNameDiscoverer() {
        /*if (KotlinDetector.isKotlinReflectPresent()) {
            addDiscoverer(new KotlinReflectionParameterNameDiscoverer());
        }*/

        // Recommended approach on Java 8+: compilation with -parameters.
        addDiscoverer(new StandardReflectionParameterNameDiscoverer());
    }
}
