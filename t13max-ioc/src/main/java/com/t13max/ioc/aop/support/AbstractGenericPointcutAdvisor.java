package com.t13max.ioc.aop.support;

import com.t13max.ioc.aop.Advice;

/**
 * @author t13max
 * @since 16:25 2026/1/16
 */
public class AbstractGenericPointcutAdvisor extends AbstractPointcutAdvisor {

    private Advice advice = EMPTY_ADVICE;

    public void setAdvice(Advice advice) {
        this.advice = advice;
    }

    @Override
    public Advice getAdvice() {
        return this.advice;
    }


    @Override
    public String toString() {
        return getClass().getName() + ": advice [" + getAdvice() + "]";
    }
}
