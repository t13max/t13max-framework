package com.t13max.ioc.transaction.interceptor;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Pointcut;
import com.t13max.ioc.aop.support.AbstractPointcutAdvisor;
import com.t13max.ioc.utils.Assert;

/**
 * @author t13max
 * @since 18:32 2026/1/16
 */
public class TransactionAttributeSourceAdvisor extends AbstractPointcutAdvisor {

    private TransactionInterceptor transactionInterceptor;

    private final TransactionAttributeSourcePointcut pointcut = new TransactionAttributeSourcePointcut();

    public TransactionAttributeSourceAdvisor() {
    }

    /**
     * Create a new TransactionAttributeSourceAdvisor.
     * @param interceptor the transaction interceptor to use for this advisor
     */
    public TransactionAttributeSourceAdvisor(TransactionInterceptor interceptor) {
        setTransactionInterceptor(interceptor);
    }


    /**
     * Set the transaction interceptor to use for this advisor.
     */
    public void setTransactionInterceptor(TransactionInterceptor interceptor) {
        Assert.notNull(interceptor, "TransactionInterceptor must not be null");
        this.transactionInterceptor = interceptor;
        this.pointcut.setTransactionAttributeSource(interceptor.getTransactionAttributeSource());
    }

    /**
     * Set the {@link ClassFilter} to use for this pointcut.
     * Default is {@link ClassFilter#TRUE}.
     */
    public void setClassFilter(ClassFilter classFilter) {
        this.pointcut.setClassFilter(classFilter);
    }


    @Override
    public Advice getAdvice() {
        Assert.state(this.transactionInterceptor != null, "No TransactionInterceptor set");
        return this.transactionInterceptor;
    }

    @Override
    public Pointcut getPointcut() {
        return this.pointcut;
    }
}
