package com.t13max.ioc.core.metrics;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Supplier;


class DefaultApplicationStartup implements ApplicationStartup {

    private static final DefaultStartupStep DEFAULT_STARTUP_STEP = new DefaultStartupStep();

    @Override
    public DefaultStartupStep start(String name) {
        return DEFAULT_STARTUP_STEP;
    }

    static class DefaultStartupStep implements StartupStep {

        private final DefaultTags TAGS = new DefaultTags();

        @Override
        public String getName() {
            return "default";
        }

        @Override
        public long getId() {
            return 0L;
        }

        @Override
        public Long getParentId() {
            return null;
        }

        @Override
        public Tags getTags() {
            return this.TAGS;
        }

        @Override
        public StartupStep tag(String key, String value) {
            return this;
        }

        @Override
        public StartupStep tag(String key, Supplier<String> value) {
            return this;
        }

        @Override
        public void end() {
        }


        static class DefaultTags implements StartupStep.Tags {

            @Override
            public Iterator<Tag> iterator() {
                return Collections.emptyIterator();
            }
        }
    }

}
