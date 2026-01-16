package com.t13max.ioc.core;

import com.t13max.ioc.utils.Assert;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: t13max
 * @Since: 21:44 2026/1/16
 */
public abstract class DecoratingClassLoader extends ClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }


    private final Set<String> excludedPackages = ConcurrentHashMap.newKeySet(8);

    private final Set<String> excludedClasses = ConcurrentHashMap.newKeySet(8);
    
    public DecoratingClassLoader() {
    }    
    public DecoratingClassLoader( ClassLoader parent) {
        super(parent);
    }
    
    public void excludePackage(String packageName) {
        Assert.notNull(packageName, "Package name must not be null");
        this.excludedPackages.add(packageName);
    }    
    public void excludeClass(String className) {
        Assert.notNull(className, "Class name must not be null");
        this.excludedClasses.add(className);
    }    
    protected boolean isExcluded(String className) {
        if (this.excludedClasses.contains(className)) {
            return true;
        }
        for (String packageName : this.excludedPackages) {
            if (className.startsWith(packageName)) {
                return true;
            }
        }
        return false;
    }

}
