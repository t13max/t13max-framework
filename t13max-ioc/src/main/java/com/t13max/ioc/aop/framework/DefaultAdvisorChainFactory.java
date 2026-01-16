package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.Advisor;
import com.t13max.ioc.aop.MethodMatcher;
import com.t13max.ioc.aop.PointcutAdvisor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author t13max
 * @since 17:28 2026/1/16
 */
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

    /**
     * Singleton instance of this class.
     *
     * @since 6.0.10
     */
    public static final DefaultAdvisorChainFactory INSTANCE = new DefaultAdvisorChainFactory();


    @Override
    public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass) {

        //获取注册器
        AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
        Advisor[] advisors = config.getAdvisors();
        List<Object> interceptorList = new ArrayList<>(advisors.length);
        Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
        Boolean hasIntroductions = null;

        for (Advisor advisor : advisors) {
            //如果是PointcutAdvisor的实例
            if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
                // Add it conditionally.
                if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
                    //从pointcutAdvisor中获取切面的方法匹配器
                    MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
                    boolean match;
                    if (mm instanceof IntroductionAwareMethodMatcher iamm) {
                        if (hasIntroductions == null) {
                            hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
                        }
                        match = iamm.matches(method, actualClass, hasIntroductions);
                    } else {
                        match = mm.matches(method, actualClass);
                    }
                    if (match) {
                        MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
                        if (mm.isRuntime()) {
                            // Creating a new object instance in the getInterceptors() method
                            // isn't a problem as we normally cache created chains.
                            for (MethodInterceptor interceptor : interceptors) {
                                interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
                            }
                        } else {
                            interceptorList.addAll(Arrays.asList(interceptors));
                        }
                    }
                }
                //advisor如果是IntroductionAdvisor的实例
            } else if (advisor instanceof IntroductionAdvisor ia) {
                if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
                    Interceptor[] interceptors = registry.getInterceptors(advisor);
                    interceptorList.addAll(Arrays.asList(interceptors));
                }
            } else {
                Interceptor[] interceptors = registry.getInterceptors(advisor);
                interceptorList.addAll(Arrays.asList(interceptors));
            }
        }

        return interceptorList;
    }

    //判断config中的Advisors是否符合配置要求
    private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
        for (Advisor advisor : advisors) {
            if (advisor instanceof IntroductionAdvisor ia && ia.getClassFilter().matches(actualClass)) {
                return true;
            }
        }
        return false;
    }
}
