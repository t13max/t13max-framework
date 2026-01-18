package com.t13max.ioc.aop.support;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.PointcutAdvisor;
import com.t13max.ioc.core.Ordered;
import com.t13max.ioc.util.ObjectUtils;

import java.io.Serializable;

/**
 * @author t13max
 * @since 16:25 2026/1/16
 */
public abstract class AbstractPointcutAdvisor implements PointcutAdvisor, Ordered, Serializable {

    private Integer order;


    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        if (this.order != null) {
            return this.order;
        }
        Advice advice = getAdvice();
        if (advice instanceof Ordered ordered) {
            return ordered.getOrder();
        }
        return Ordered.LOWEST_PRECEDENCE;
    }


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof PointcutAdvisor otherAdvisor &&
                ObjectUtils.nullSafeEquals(getAdvice(), otherAdvisor.getAdvice()) &&
                ObjectUtils.nullSafeEquals(getPointcut(), otherAdvisor.getPointcut())));
    }

    @Override
    public int hashCode() {
        return PointcutAdvisor.class.hashCode();
    }
}
