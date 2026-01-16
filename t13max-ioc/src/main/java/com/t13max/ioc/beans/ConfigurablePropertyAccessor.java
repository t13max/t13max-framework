package com.t13max.ioc.beans;

/**
 * @Author: t13max
 * @Since: 22:29 2026/1/16
 */
public interface ConfigurablePropertyAccessor extends PropertyAccessor, PropertyEditorRegistry, TypeConverter {
    
    void setConversionService( ConversionService conversionService);
    
     ConversionService getConversionService();
    
    void setExtractOldValueForEditor(boolean extractOldValueForEditor);
    
    boolean isExtractOldValueForEditor();
    
    void setAutoGrowNestedPaths(boolean autoGrowNestedPaths);
    
    boolean isAutoGrowNestedPaths();

}
