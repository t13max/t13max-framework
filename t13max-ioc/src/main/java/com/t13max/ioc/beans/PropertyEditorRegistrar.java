package com.t13max.ioc.beans;

/**
 * @author t13max
 * @since 11:23 2026/1/16
 */
public interface PropertyEditorRegistrar {

    void registerCustomEditors(PropertyEditorRegistry registry);

    default boolean overridesDefaultEditors() {
        return false;
    }
}
