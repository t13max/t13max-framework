package com.t13max.ioc.beans.factory.support;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @Author: t13max
 * @Since: 21:06 2026/1/16
 */
public class MethodOverrides {

    private final Set<MethodOverride> overrides = new CopyOnWriteArraySet<>();    
    public MethodOverrides() {
    }
    public MethodOverrides(MethodOverrides other) {
        addOverrides(other);
    }

    public void addOverrides( MethodOverrides other) {
        if (other != null) {
            this.overrides.addAll(other.overrides);
        }
    }
    public void addOverride(MethodOverride override) {
        this.overrides.add(override);
    }
    public Set<MethodOverride> getOverrides() {
        return this.overrides;
    }
    public boolean isEmpty() {
        return this.overrides.isEmpty();
    }
    public MethodOverride getOverride(Method method) {
        MethodOverride match = null;
        for (MethodOverride candidate : this.overrides) {
            if (candidate.matches(method)) {
                match = candidate;
            }
        }
        return match;
    }


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof MethodOverrides that &&
                this.overrides.equals(that.overrides)));
    }

    @Override
    public int hashCode() {
        return this.overrides.hashCode();
    }
}
