package com.t13max.ioc.aop.support;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.utils.ObjectUtils;

import java.io.Serializable;

/**
 * @author t13max
 * @since 16:25 2026/1/16
 */
public class AbstractPointcutAdvisor implements PointcutAdvisor, Ordered, Serializable {

    @Nullable
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
    public boolean equals(@Nullable Object other) {
        return (this == other || (other instanceof PointcutAdvisor otherAdvisor &&
                ObjectUtils.nullSafeEquals(getAdvice(), otherAdvisor.getAdvice()) &&
                ObjectUtils.nullSafeEquals(getPointcut(), otherAdvisor.getPointcut())));
    }

    @Override
    public int hashCode() {
        return PointcutAdvisor.class.hashCode();
    }
}
