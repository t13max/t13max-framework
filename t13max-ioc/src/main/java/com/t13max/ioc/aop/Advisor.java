package com.t13max.ioc.aop;

/**
 * @author t13max
 * @since 14:49 2026/1/16
 */
public interface Advisor {
    Advice EMPTY_ADVICE = new Advice() {};


    /**
     * Return the advice part of this aspect. An advice may be an
     * interceptor, a before advice, a throws advice, etc.
     * @return the advice that should apply if the pointcut matches
     * @see org.aopalliance.intercept.MethodInterceptor
     * @see BeforeAdvice
     * @see ThrowsAdvice
     * @see AfterReturningAdvice
     */
    Advice getAdvice();

    /**
     * Return whether this advice is associated with a particular instance
     * (for example, creating a mixin) or shared with all instances of
     * the advised class obtained from the same Spring bean factory.
     * <p><b>Note that this method is not currently used by the framework.</b>
     * Typical Advisor implementations always return {@code true}.
     * Use singleton/prototype bean definitions or appropriate programmatic
     * proxy creation to ensure that Advisors have the correct lifecycle model.
     * <p>As of 6.0.10, the default implementation returns {@code true}.
     * @return whether this advice is associated with a particular target instance
     */
    default boolean isPerInstance() {
        return true;
    }
}
