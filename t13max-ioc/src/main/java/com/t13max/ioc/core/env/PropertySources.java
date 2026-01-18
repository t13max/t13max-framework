package com.t13max.ioc.core.env;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @Author: t13max
 * @Since: 7:35 2026/1/17
 */
public interface PropertySources extends Iterable<PropertySource<?>> {

    default Stream<PropertySource<?>> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    boolean contains(String name);

    PropertySource<?> get(String name);
}
