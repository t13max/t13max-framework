package com.t13max.ioc.beans;

/**
 * @Author: t13max
 * @Since: 22:53 2026/1/15
 */
public interface BeanMetadataElement {

    default Object getSource() {
        return null;
    }
}
