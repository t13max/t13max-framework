package com.t13max.ioc.aop.support;

import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ObjectUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author t13max
 * @since 16:17 2026/1/16
 */
public class AbstractRegexpMethodPointcut extends StaticMethodMatcherPointcut
        implements Serializable {
    private String[] patterns = new String[0];
    private String[] excludedPatterns = new String[0];

    public void setPattern(String pattern) {
        setPatterns(pattern);
    }
    public void setPatterns(String... patterns) {
        Assert.notEmpty(patterns, "'patterns' must not be empty");
        this.patterns = new String[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            this.patterns[i] = patterns[i].strip();
        }
        initPatternRepresentation(this.patterns);
    }
    public String[] getPatterns() {
        return this.patterns;
    }
    public void setExcludedPattern(String excludedPattern) {
        setExcludedPatterns(excludedPattern);
    }
    public void setExcludedPatterns(String... excludedPatterns) {
        Assert.notEmpty(excludedPatterns, "'excludedPatterns' must not be empty");
        this.excludedPatterns = new String[excludedPatterns.length];
        for (int i = 0; i < excludedPatterns.length; i++) {
            this.excludedPatterns[i] = excludedPatterns[i].strip();
        }
        initExcludedPatternRepresentation(this.excludedPatterns);
    }
    public String[] getExcludedPatterns() {
        return this.excludedPatterns;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return (matchesPattern(ClassUtils.getQualifiedMethodName(method, targetClass)) ||
                (targetClass != method.getDeclaringClass() &&
                        matchesPattern(ClassUtils.getQualifiedMethodName(method, method.getDeclaringClass()))));
    }
    protected boolean matchesPattern(String signatureString) {
        for (int i = 0; i < this.patterns.length; i++) {
            boolean matched = matches(signatureString, i);
            if (matched) {
                for (int j = 0; j < this.excludedPatterns.length; j++) {
                    boolean excluded = matchesExclusion(signatureString, j);
                    if (excluded) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    protected abstract void initPatternRepresentation(String[] patterns) throws IllegalArgumentException;
    protected abstract void initExcludedPatternRepresentation(String[] patterns) throws IllegalArgumentException;
    protected abstract boolean matches(String pattern, int patternIndex);
    protected abstract boolean matchesExclusion(String pattern, int patternIndex);


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof AbstractRegexpMethodPointcut otherPointcut &&
                Arrays.equals(this.patterns, otherPointcut.patterns) &&
                Arrays.equals(this.excludedPatterns, otherPointcut.excludedPatterns)));
    }

    @Override
    public int hashCode() {
        int result = 27;
        for (String pattern : this.patterns) {
            result = 13 * result + pattern.hashCode();
        }
        for (String excludedPattern : this.excludedPatterns) {
            result = 13 * result + excludedPattern.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": patterns " + ObjectUtils.nullSafeToString(this.patterns) +
                ", excluded patterns " + ObjectUtils.nullSafeToString(this.excludedPatterns);
    }
}
