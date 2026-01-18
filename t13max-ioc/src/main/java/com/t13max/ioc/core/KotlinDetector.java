package com.t13max.ioc.core;

import com.t13max.ioc.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * @Author: t13max
 * @Since: 22:20 2026/1/16
 */
public class KotlinDetector {


    private static final  Class<? extends Annotation> kotlinMetadata;

    private static final  Class<? extends Annotation> kotlinJvmInline;

    private static final  Class<?> kotlinCoroutineContinuation;

    // For ConstantFieldFeature compliance, otherwise could be deduced from kotlinMetadata
    private static final boolean kotlinPresent;

    private static final boolean kotlinReflectPresent;

    static {
        ClassLoader classLoader = KotlinDetector.class.getClassLoader();
        Class<?> metadata = null;
        Class<?> jvmInline = null;
        Class<?> coroutineContinuation = null;
        try {
            metadata = ClassUtils.forName("kotlin.Metadata", classLoader);
            try {
                jvmInline = ClassUtils.forName("kotlin.jvm.JvmInline", classLoader);
            }
            catch (ClassNotFoundException ex) {
                // JVM inline support not available
            }
            try {
                coroutineContinuation = ClassUtils.forName("kotlin.coroutines.Continuation", classLoader);
            }
            catch (ClassNotFoundException ex) {
                // Coroutines support not available
            }
        }
        catch (ClassNotFoundException ex) {
            // Kotlin API not available - no Kotlin support
        }
        kotlinMetadata = (Class<? extends Annotation>) metadata;
        kotlinPresent = (kotlinMetadata != null);
        kotlinReflectPresent = ClassUtils.isPresent("kotlin.reflect.full.KClasses", classLoader);
        kotlinJvmInline = (Class<? extends Annotation>) jvmInline;
        kotlinCoroutineContinuation = coroutineContinuation;
    }
    
    public static boolean isKotlinPresent() {
        return kotlinPresent;
    }    
    public static boolean isKotlinReflectPresent() {
        return kotlinReflectPresent;
    }    
    public static boolean isKotlinType(Class<?> clazz) {
        return (kotlinPresent && clazz.getDeclaredAnnotation(kotlinMetadata) != null);
    }    
    public static boolean isSuspendingFunction(Method method) {
        if (kotlinCoroutineContinuation == null) {
            return false;
        }
        int parameterCount = method.getParameterCount();
        return (parameterCount > 0 && method.getParameterTypes()[parameterCount - 1] == kotlinCoroutineContinuation);
    }
public static boolean isInlineClass(Class<?> clazz) {
        return (kotlinJvmInline != null && clazz.getDeclaredAnnotation(kotlinJvmInline) != null);
    }
}
