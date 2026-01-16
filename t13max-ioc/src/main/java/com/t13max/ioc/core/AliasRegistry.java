package com.t13max.ioc.core;

/**
 * 别名注册表
 *
 * @Author: t13max
 * @Since: 22:44 2026/1/15
 */
public interface AliasRegistry {

    void registerAlias(String name, String alias);

    void removeAlias(String alias);

    boolean isAlias(String name);

    String[] getAliases(String name);
}
