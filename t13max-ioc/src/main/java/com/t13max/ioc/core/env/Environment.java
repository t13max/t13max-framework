package com.t13max.ioc.core.env;

/**
 * @Author: t13max
 * @Since: 21:50 2026/1/15
 */
public interface Environment extends PropertyResolver {

    String[] getActiveProfiles();

    String[] getDefaultProfiles();

    default boolean matchesProfiles(String... profileExpressions) {
        return acceptsProfiles(Profiles.of(profileExpressions));
    }

    @Deprecated(since = "5.1")
    boolean acceptsProfiles(String... profiles);

    boolean acceptsProfiles(Profiles profiles);
}
