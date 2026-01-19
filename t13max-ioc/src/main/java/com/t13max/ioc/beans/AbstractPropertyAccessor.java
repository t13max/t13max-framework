package com.t13max.ioc.beans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author t13max
 * @since 14:03 2026/1/16
 */
public abstract class AbstractPropertyAccessor extends TypeConverterSupport implements ConfigurablePropertyAccessor {

    private boolean extractOldValueForEditor = false;

    private boolean autoGrowNestedPaths = false;

    boolean suppressNotWritablePropertyException = false;

    @Override
    public void setExtractOldValueForEditor(boolean extractOldValueForEditor) {
        this.extractOldValueForEditor = extractOldValueForEditor;
    }

    @Override
    public boolean isExtractOldValueForEditor() {
        return this.extractOldValueForEditor;
    }

    @Override
    public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
        this.autoGrowNestedPaths = autoGrowNestedPaths;
    }

    @Override
    public boolean isAutoGrowNestedPaths() {
        return this.autoGrowNestedPaths;
    }


    @Override
    public void setPropertyValue(PropertyValue pv) throws BeansException {
        setPropertyValue(pv.getName(), pv.getValue());
    }

    @Override
    public void setPropertyValues(Map<?, ?> map) throws BeansException {
        setPropertyValues(new MutablePropertyValues(map));
    }

    @Override
    public void setPropertyValues(PropertyValues pvs) throws BeansException {
        setPropertyValues(pvs, false, false);
    }

    @Override
    public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown) throws BeansException {
        setPropertyValues(pvs, ignoreUnknown, false);
    }

    @Override
    public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid) throws BeansException {

        List<PropertyAccessException> propertyAccessExceptions = null;
        List<PropertyValue> propertyValues = (pvs instanceof MutablePropertyValues mpvs ? mpvs.getPropertyValueList() : Arrays.asList(pvs.getPropertyValues()));

        if (ignoreUnknown) {
            this.suppressNotWritablePropertyException = true;
        }
        try {
            for (PropertyValue pv : propertyValues) {
                try {
                    //该方法走BeanWrapperImpl中的实现, 这bean属性值注入,具体实现的入口
                    setPropertyValue(pv);
                } catch (NotWritablePropertyException ex) {
                    if (!ignoreUnknown) {
                        throw ex;
                    }
                    // Otherwise, just ignore it and continue...
                } catch (NullValueInNestedPathException ex) {
                    if (!ignoreInvalid) {
                        throw ex;
                    }
                    // Otherwise, just ignore it and continue...
                } catch (PropertyAccessException ex) {
                    if (propertyAccessExceptions == null) {
                        propertyAccessExceptions = new ArrayList<>();
                    }
                    propertyAccessExceptions.add(ex);
                }
            }
        } finally {
            if (ignoreUnknown) {
                this.suppressNotWritablePropertyException = false;
            }
        }

        // 如果出现PropertyAccessException异常, 则将这些异常积累起来放到一个集合中, 然后一次性抛出
        if (propertyAccessExceptions != null) {
            PropertyAccessException[] paeArray = propertyAccessExceptions.toArray(new PropertyAccessException[0]);
            throw new PropertyBatchUpdateException(paeArray);
        }
    }

    @Override
    public Class<?> getPropertyType(String propertyPath) {
        return null;
    }

    @Override
    public abstract Object getPropertyValue(String propertyName) throws BeansException;

    @Override
    public abstract void setPropertyValue(String propertyName,  Object value) throws BeansException;
}
