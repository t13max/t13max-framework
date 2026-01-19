package com.t13max.ioc.aop;

/**
 * @Author: t13max
 * @Since: 21:58 2026/1/16
 */
public interface TargetSource extends TargetClassAware {    
    @Override
    Class<?> getTargetClass();    
    default boolean isStatic() {
        return false;
    }    
    Object getTarget() throws Exception;    
    default void releaseTarget(Object target) throws Exception {
    }
}
