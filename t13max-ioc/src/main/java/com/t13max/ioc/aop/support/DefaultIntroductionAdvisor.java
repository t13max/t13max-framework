package com.t13max.ioc.aop.support;

import com.t13max.ioc.aop.*;
import com.t13max.ioc.core.Ordered;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @Author: t13max
 * @Since: 22:09 2026/1/16
 */
public class DefaultIntroductionAdvisor implements IntroductionAdvisor, ClassFilter, Ordered, Serializable {

    private final Advice advice;

    private final Set<Class<?>> interfaces = new LinkedHashSet<>();

    private int order = Ordered.LOWEST_PRECEDENCE;
    
    public DefaultIntroductionAdvisor(Advice advice) {
        this(advice, (advice instanceof IntroductionInfo introductionInfo ? introductionInfo : null));
    }    
    public DefaultIntroductionAdvisor(Advice advice,  IntroductionInfo introductionInfo) {
        Assert.notNull(advice, "Advice must not be null");
        this.advice = advice;
        if (introductionInfo != null) {
            Class<?>[] introducedInterfaces = introductionInfo.getInterfaces();
            if (introducedInterfaces.length == 0) {
                throw new IllegalArgumentException(
                        "IntroductionInfo defines no interfaces to introduce: " + introductionInfo);
            }
            for (Class<?> ifc : introducedInterfaces) {
                addInterface(ifc);
            }
        }
    }    
    public DefaultIntroductionAdvisor(DynamicIntroductionAdvice advice, Class<?> ifc) {
        Assert.notNull(advice, "Advice must not be null");
        this.advice = advice;
        addInterface(ifc);
    }
    
    public void addInterface(Class<?> ifc) {
        Assert.notNull(ifc, "Interface must not be null");
        if (!ifc.isInterface()) {
            throw new IllegalArgumentException("Specified class [" + ifc.getName() + "] must be an interface");
        }
        this.interfaces.add(ifc);
    }

    @Override
    public Class<?>[] getInterfaces() {
        return ClassUtils.toClassArray(this.interfaces);
    }

    @Override
    public void validateInterfaces() throws IllegalArgumentException {
        for (Class<?> ifc : this.interfaces) {
            if (this.advice instanceof DynamicIntroductionAdvice dynamicIntroductionAdvice &&
                    !dynamicIntroductionAdvice.implementsInterface(ifc)) {
                throw new IllegalArgumentException("DynamicIntroductionAdvice [" + this.advice + "] " +
                        "does not implement interface [" + ifc.getName() + "] specified for introduction");
            }
        }
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public Advice getAdvice() {
        return this.advice;
    }

    @Override
    public ClassFilter getClassFilter() {
        return this;
    }

    @Override
    public boolean matches(Class<?> clazz) {
        return true;
    }


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof DefaultIntroductionAdvisor otherAdvisor &&
                this.advice.equals(otherAdvisor.advice) &&
                this.interfaces.equals(otherAdvisor.interfaces)));
    }

    @Override
    public int hashCode() {
        return this.advice.hashCode() * 13 + this.interfaces.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName() + ": advice [" + this.advice + "]; interfaces " +
                ClassUtils.classNamesToString(this.interfaces);
    }
}
