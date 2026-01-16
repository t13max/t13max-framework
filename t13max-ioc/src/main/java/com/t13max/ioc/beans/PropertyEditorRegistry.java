package com.t13max.ioc.beans;

import java.beans.PropertyEditor;

/**
 * @author t13max
 * @since 11:23 2026/1/16
 */
public interface PropertyEditorRegistry {
    void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor);
    void registerCustomEditor( Class<?> requiredType,  String propertyPath, PropertyEditor propertyEditor);    
    PropertyEditor findCustomEditor( Class<?> requiredType,  String propertyPath);
}
