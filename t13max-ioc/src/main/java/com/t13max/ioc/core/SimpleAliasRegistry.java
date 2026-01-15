package com.t13max.ioc.core;

import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ObjectUtils;
import com.t13max.ioc.utils.StringUtils;
import com.t13max.ioc.utils.StringValueResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleAliasRegistry implements AliasRegistry {

    protected final Logger logger = LogManager.getLogger(getClass());

    //别名集合
    private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);

    private final List<String> aliasNames = new ArrayList<>(16);

    @Override
    public void registerAlias(String name, String alias) {
        Assert.hasText(name, "'name' must not be empty");
        Assert.hasText(alias, "'alias' must not be empty");
        synchronized (this.aliasMap) {
            if (alias.equals(name)) {
                this.aliasMap.remove(alias);
                this.aliasNames.remove(alias);
                if (logger.isDebugEnabled()) {
                    logger.debug("Alias definition '{}' ignored since it points to same name", alias);
                }
            } else {
                String registeredName = this.aliasMap.get(alias);
                if (registeredName != null) {
                    if (registeredName.equals(name)) {
                        // An existing alias - no need to re-register
                        return;
                    }
                    if (!allowAliasOverriding()) {
                        throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
                                name + "': It is already registered for name '" + registeredName + "'.");
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Overriding alias '" + alias + "' definition for registered name '" +
                                registeredName + "' with new target name '" + name + "'");
                    }
                }
                checkForAliasCircle(name, alias);
                this.aliasMap.put(alias, name);
                this.aliasNames.add(alias);
                if (logger.isTraceEnabled()) {
                    logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
                }
            }
        }
    }

    protected boolean allowAliasOverriding() {
        return true;
    }

    public boolean hasAlias(String name, String alias) {
        String registeredName = this.aliasMap.get(alias);
        return ObjectUtils.nullSafeEquals(registeredName, name) ||
                (registeredName != null && hasAlias(name, registeredName));
    }

    @Override
    public void removeAlias(String alias) {
        synchronized (this.aliasMap) {
            String name = this.aliasMap.remove(alias);
            this.aliasNames.remove(alias);
            if (name == null) {
                throw new IllegalStateException("No alias '" + alias + "' registered");
            }
        }
    }

    @Override
    public boolean isAlias(String name) {
        return this.aliasMap.containsKey(name);
    }

    @Override
    public String[] getAliases(String name) {
        List<String> result = new ArrayList<>();
        synchronized (this.aliasMap) {
            retrieveAliases(name, result);
        }
        return StringUtils.toStringArray(result);
    }

    private void retrieveAliases(String name, List<String> result) {
        this.aliasMap.forEach((alias, registeredName) -> {
            if (registeredName.equals(name)) {
                result.add(alias);
                retrieveAliases(alias, result);
            }
        });
    }

    public void resolveAliases(StringValueResolver valueResolver) {
        Assert.notNull(valueResolver, "StringValueResolver must not be null");
        synchronized (this.aliasMap) {
            List<String> aliasNamesCopy = new ArrayList<>(this.aliasNames);
            aliasNamesCopy.forEach(alias -> {
                String registeredName = this.aliasMap.get(alias);
                if (registeredName != null) {
                    String resolvedAlias = valueResolver.resolveStringValue(alias);
                    String resolvedName = valueResolver.resolveStringValue(registeredName);
                    if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
                        this.aliasMap.remove(alias);
                        this.aliasNames.remove(alias);
                    } else if (!resolvedAlias.equals(alias)) {
                        String existingName = this.aliasMap.get(resolvedAlias);
                        if (existingName != null) {
                            if (existingName.equals(resolvedName)) {
                                // Pointing to existing alias - just remove placeholder
                                this.aliasMap.remove(alias);
                                this.aliasNames.remove(alias);
                                return;
                            }
                            throw new IllegalStateException(
                                    "Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
                                            "') for name '" + resolvedName + "': It is already registered for name '" +
                                            existingName + "'.");
                        }
                        checkForAliasCircle(resolvedName, resolvedAlias);
                        this.aliasMap.remove(alias);
                        this.aliasNames.remove(alias);
                        this.aliasMap.put(resolvedAlias, resolvedName);
                        this.aliasNames.add(resolvedAlias);
                    } else if (!registeredName.equals(resolvedName)) {
                        this.aliasMap.put(alias, resolvedName);
                        this.aliasNames.add(alias);
                    }
                }
            });
        }
    }

    protected void checkForAliasCircle(String name, String alias) {
        if (hasAlias(alias, name)) {
            throw new IllegalStateException("Cannot register alias '" + alias +
                    "' for name '" + name + "': Circular reference - '" +
                    name + "' is a direct or indirect alias for '" + alias + "' already");
        }
    }

    //
    public String canonicalName(String name) {
        String canonicalName = name;
        // Handle aliasing...
        String resolvedName;
        do {
            resolvedName = this.aliasMap.get(canonicalName);
            if (resolvedName != null) {
                canonicalName = resolvedName;
            }
        }
        while (resolvedName != null);
        return canonicalName;
    }
}
