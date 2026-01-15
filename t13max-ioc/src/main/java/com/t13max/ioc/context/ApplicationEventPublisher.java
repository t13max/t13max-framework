package com.t13max.ioc.context;

/**
 * @Author: t13max
 * @Since: 21:19 2026/1/15
 */
@FunctionalInterface
public interface ApplicationEventPublisher {

    default void publishEvent(ApplicationEvent event) {
        publishEvent((Object) event);
    }

    void publishEvent(Object event);
}
