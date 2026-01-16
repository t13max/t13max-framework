package com.t13max.ioc.aop.support;

import com.t13max.ioc.aop.*;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author t13max
 * @since 17:00 2026/1/16
 */
public class AopUtils {
    private static final boolean coroutinesReactorPresent = ClassUtils.isPresent(
            "kotlinx.coroutines.reactor.MonoKt", AopUtils.class.getClassLoader());

    public static boolean isAopProxy( Object object) {
        return (object instanceof SpringProxy && (Proxy.isProxyClass(object.getClass()) ||
                object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)));
    }
    public static boolean isJdkDynamicProxy( Object object) {
        return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
    }
    public static boolean isCglibProxy( Object object) {
        return (object instanceof SpringProxy &&
                object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR));
    }
    public static Class<?> getTargetClass(Object candidate) {
        Assert.notNull(candidate, "Candidate object must not be null");
        Class<?> result = null;
        if (candidate instanceof TargetClassAware targetClassAware) {
            result = targetClassAware.getTargetClass();
        }
        if (result == null) {
            result = (isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
        }
        return result;
    }
    public static Method selectInvocableMethod(Method method,  Class<?> targetType) {
        if (targetType == null) {
            return method;
        }
        Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
        if (Modifier.isPrivate(methodToUse.getModifiers()) && !Modifier.isStatic(methodToUse.getModifiers()) &&
                SpringProxy.class.isAssignableFrom(targetType)) {
            throw new IllegalStateException(String.format(
                    "Need to invoke method '%s' found on proxy for target class '%s' but cannot " +
                            "be delegated to target bean. Switch its visibility to package or protected.",
                    method.getName(), method.getDeclaringClass().getSimpleName()));
        }
        return methodToUse;
    }
    public static boolean isEqualsMethod( Method method) {
        return ReflectionUtils.isEqualsMethod(method);
    }
    public static boolean isHashCodeMethod( Method method) {
        return ReflectionUtils.isHashCodeMethod(method);
    }
    public static boolean isToStringMethod( Method method) {
        return ReflectionUtils.isToStringMethod(method);
    }
    public static boolean isFinalizeMethod( Method method) {
        return (method != null && method.getName().equals("finalize") &&
                method.getParameterCount() == 0);
    }
    public static Method getMostSpecificMethod(Method method,  Class<?> targetClass) {
        Class<?> specificTargetClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
        return BridgeMethodResolver.getMostSpecificMethod(method, specificTargetClass);
    }
    public static boolean canApply(Pointcut pc, Class<?> targetClass) {
        return canApply(pc, targetClass, false);
    }
    public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
        Assert.notNull(pc, "Pointcut must not be null");
        if (!pc.getClassFilter().matches(targetClass)) {
            return false;
        }

        MethodMatcher methodMatcher = pc.getMethodMatcher();
        if (methodMatcher == MethodMatcher.TRUE) {
            // No need to iterate the methods if we're matching any method anyway...
            return true;
        }

        IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
        if (methodMatcher instanceof IntroductionAwareMethodMatcher iamm) {
            introductionAwareMethodMatcher = iamm;
        }

        Set<Class<?>> classes = new LinkedHashSet<>();
        if (!Proxy.isProxyClass(targetClass)) {
            classes.add(ClassUtils.getUserClass(targetClass));
        }
        classes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetClass));

        for (Class<?> clazz : classes) {
            Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
            for (Method method : methods) {
                if (introductionAwareMethodMatcher != null ?
                        introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions) :
                        methodMatcher.matches(method, targetClass)) {
                    return true;
                }
            }
        }

        return false;
    }
    public static boolean canApply(Advisor advisor, Class<?> targetClass) {
        return canApply(advisor, targetClass, false);
    }
    public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
        if (advisor instanceof IntroductionAdvisor ia) {
            return ia.getClassFilter().matches(targetClass);
        } else if (advisor instanceof PointcutAdvisor pca) {
            return canApply(pca.getPointcut(), targetClass, hasIntroductions);
        } else {
            // It doesn't have a pointcut so we assume it applies.
            return true;
        }
    }
    public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
        if (candidateAdvisors.isEmpty()) {
            return candidateAdvisors;
        }
        List<Advisor> eligibleAdvisors = new ArrayList<>();
        for (Advisor candidate : candidateAdvisors) {
            if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
                eligibleAdvisors.add(candidate);
            }
        }
        boolean hasIntroductions = !eligibleAdvisors.isEmpty();
        for (Advisor candidate : candidateAdvisors) {
            if (candidate instanceof IntroductionAdvisor) {
                // already processed
                continue;
            }
            if (canApply(candidate, clazz, hasIntroductions)) {
                eligibleAdvisors.add(candidate);
            }
        }
        return eligibleAdvisors;
    }

    //使用spring的反射机制, 调用目标方法method的invoke方法
    public static Object invokeJoinpointUsingReflection( Object target, Method method, Object[] args) throws Throwable {

        // Use reflection to invoke the method.
        try {
            Method originalMethod = BridgeMethodResolver.findBridgedMethod(method);
            //如果该method是private的,则将其访问权限设为public的
            ReflectionUtils.makeAccessible(originalMethod);
            //反射调用
            return (coroutinesReactorPresent && KotlinDetector.isSuspendingFunction(originalMethod) ?
                    KotlinDelegate.invokeSuspendingFunction(originalMethod, target, args) : originalMethod.invoke(target, args));
        } catch (InvocationTargetException ex) {
            // Invoked method threw a checked exception.
            // We must rethrow it. The client won't see the interceptor.
            throw ex.getTargetException();
        } catch (IllegalArgumentException ex) {
            throw new AopInvocationException("AOP configuration seems to be invalid: tried calling method [" + method + "] on target [" + target + "]", ex);
        } catch (IllegalAccessException ex) {
            throw new AopInvocationException("Could not access method [" + method + "]", ex);
        }
    }

    private static class KotlinDelegate {

        public static Object invokeSuspendingFunction(Method method,  Object target, Object... args) {
            Continuation<?> continuation = (Continuation<?>) args[args.length - 1];
            Assert.state(continuation != null, "No Continuation available");
            CoroutineContext context = continuation.getContext().minusKey(Job.Key);
            return CoroutinesUtils.invokeSuspendingFunction(context, method, target, args);
        }
    }

}
