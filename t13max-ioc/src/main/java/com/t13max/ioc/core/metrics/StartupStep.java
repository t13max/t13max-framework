package com.t13max.ioc.core.metrics;

import java.util.function.Supplier;

/**
 * 记录指标
 *
 * @Author: t13max
 * @Since: 20:53 2026/1/15
 */
public interface StartupStep {

    String getName();

    long getId();

    Long getParentId();

    StartupStep tag(String key, String value);

    StartupStep tag(String key, Supplier<String> value);

    Tags getTags();

    void end();

    interface Tags extends Iterable<Tag> {
    }

    interface Tag {

        String getKey();

        String getValue();
    }
}
