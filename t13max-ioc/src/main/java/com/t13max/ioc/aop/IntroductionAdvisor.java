package com.t13max.ioc.aop;

/**
 * @Author: t13max
 * @Since: 22:08 2026/1/16
 */
public interface IntroductionAdvisor extends Advisor, IntroductionInfo {    
    ClassFilter getClassFilter();    
    void validateInterfaces() throws IllegalArgumentException;
}
