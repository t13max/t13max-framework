package com.t13max.ioc.context;

/**
 * @Author: t13max
 * @Since: 21:43 2026/1/15
 */
public interface MessageSourceResolvable {

    String [] getCodes();

    default Object [] getArguments() {
        return null;
    }

    default String getDefaultMessage() {
        return null;
    }
}
