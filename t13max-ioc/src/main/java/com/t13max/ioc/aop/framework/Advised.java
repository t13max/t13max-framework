package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.Advice;
import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.TargetClassAware;
import com.t13max.ioc.aop.TargetSource;

/**
 * @author t13max
 * @since 16:29 2026/1/16
 */
public interface Advised extends TargetClassAware {

    boolean isFrozen();

    boolean isProxyTargetClass();

    Class<?>[] getProxiedInterfaces();

    boolean isInterfaceProxied(Class<?> ifc);

    void setTargetSource(TargetSource targetSource);

    TargetSource getTargetSource();

    void setExposeProxy(boolean exposeProxy);

    boolean isExposeProxy();

    void setPreFiltered(boolean preFiltered);

    boolean isPreFiltered();

    Advisor[] getAdvisors();

    default int getAdvisorCount() {
        return getAdvisors().length;
    }

    void addAdvisor(Advisor advisor) throws AopConfigException;

    void addAdvisor(int pos, Advisor advisor) throws AopConfigException;

    boolean removeAdvisor(Advisor advisor);

    void removeAdvisor(int index) throws AopConfigException;

    int indexOf(Advisor advisor);

    boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException;

    void addAdvice(Advice advice) throws AopConfigException;

    void addAdvice(int pos, Advice advice) throws AopConfigException;

    boolean removeAdvice(Advice advice);

    int indexOf(Advice advice);

    String toProxyConfigString();
}
