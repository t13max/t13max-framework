package com.t13max.ioc.aop.framework.adapter;

/**
 * @Author: t13max
 * @Since: 22:01 2026/1/16
 */
public class UnknownAdviceTypeException extends IllegalArgumentException {

    public UnknownAdviceTypeException(Object advice) {
        super("Advice object [" + advice + "] is neither a supported subinterface of [org.aopalliance.aop.Advice] nor an [org.springframework.aop.Advisor]");
    }

    public UnknownAdviceTypeException(String message) {
        super(message);
    }
}
