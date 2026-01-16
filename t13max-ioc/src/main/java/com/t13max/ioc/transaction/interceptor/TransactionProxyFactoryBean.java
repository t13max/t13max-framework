package com.t13max.ioc.transaction.interceptor;

import com.t13max.ioc.aop.Pointcut;
import com.t13max.ioc.aop.support.DefaultPointcutAdvisor;
import com.t13max.ioc.beans.factory.BeanFactory;

import java.util.Properties;

/**
 * @author t13max
 * @since 18:01 2026/1/16
 */
public class TransactionProxyFactoryBean extends AbstractSingletonProxyFactoryBean implements BeanFactoryAware {

    //事务拦截器, 基于AOP
    private final TransactionInterceptor transactionInterceptor = new TransactionInterceptor();

    //切面
    private Pointcut pointcut;

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionInterceptor.setTransactionManager(transactionManager);
    }

    public void setTransactionAttributes(Properties transactionAttributes) {
        this.transactionInterceptor.setTransactionAttributes(transactionAttributes);
    }

    public void setTransactionAttributeSource(TransactionAttributeSource transactionAttributeSource) {
        this.transactionInterceptor.setTransactionAttributeSource(transactionAttributeSource);
    }

    public void setPointcut(Pointcut pointcut) {
        this.pointcut = pointcut;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.transactionInterceptor.setBeanFactory(beanFactory);
    }

    //创建Advisor
    @Override
    protected Object createMainInterceptor() {
        this.transactionInterceptor.afterPropertiesSet();
        if (this.pointcut != null) {
            //如果自己定义了切面, 就使用默认的通知器, 并为其配置事务处理拦截器
            return new DefaultPointcutAdvisor(this.pointcut, this.transactionInterceptor);
        }
        else {
            // 没定义则使用默认切面
            return new TransactionAttributeSourceAdvisor(this.transactionInterceptor);
        }
    }

    @Override
    protected void postProcessProxyFactory(ProxyFactory proxyFactory) {
        proxyFactory.addInterface(TransactionalProxy.class);
    }

}
