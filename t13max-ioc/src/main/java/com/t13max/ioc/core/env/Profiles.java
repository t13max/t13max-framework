package com.t13max.ioc.core.env;

import java.util.function.Predicate;

/**
 * @Author: t13max
 * @Since: 7:32 2026/1/17
 */
@FunctionalInterface
public interface Profiles {

    boolean matches(Predicate<String> isProfileActive);

    static Profiles of(String... profileExpressions) {
        return ProfilesParser.parse(profileExpressions);
    }

}
