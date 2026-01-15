package com.t13max.ioc.context;

import java.util.EventListener;
import java.util.function.Consumer;

/**
 * @Author: t13max
 * @Since: 21:06 2026/1/15
 */
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {

    void onApplicationEvent(E event);

    default boolean supportsAsyncExecution() {
        return true;
    }

    static <T> ApplicationListener<PayloadApplicationEvent<T>> forPayload(Consumer<T> consumer) {
        return event -> consumer.accept(event.getPayload());
    }
}
