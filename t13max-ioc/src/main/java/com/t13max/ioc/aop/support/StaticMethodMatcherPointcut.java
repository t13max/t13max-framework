package com.t13max.ioc.aop.support;

import com.t13max.ioc.aop.Pointcut;

/**
 * @author t13max
 * @since 16:18 2026/1/16
 */
public class StaticMethodMatcherPointcut extends StaticMethodMatcher implements Pointcut {

    private ClassFilter classFilter = ClassFilter.TRUE;


    /**
     * Set the {@link ClassFilter} to use for this pointcut.
     * Default is {@link ClassFilter#TRUE}.
     */
    public void setClassFilter(ClassFilter classFilter) {
        this.classFilter = classFilter;
    }

    @Override
    public ClassFilter getClassFilter() {
        return this.classFilter;
    }


    @Override
    public final MethodMatcher getMethodMatcher() {
        return this;
    }
}
