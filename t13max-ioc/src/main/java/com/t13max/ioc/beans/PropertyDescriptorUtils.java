package com.t13max.ioc.beans;

import com.t13max.ioc.util.ObjectUtils;
import com.t13max.ioc.util.StringUtils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @Author: t13max
 * @Since: 23:24 2026/1/16
 */
public class PropertyDescriptorUtils {

    public static final PropertyDescriptor[] EMPTY_PROPERTY_DESCRIPTOR_ARRAY = {};

    public static Collection<? extends PropertyDescriptor> determineBasicProperties(Class<?> beanClass) throws IntrospectionException {

        Map<String, BasicPropertyDescriptor> pdMap = new TreeMap<>();

        for (Method method : beanClass.getMethods()) {
            String methodName = method.getName();

            boolean setter;
            int nameIndex;
            if (methodName.startsWith("set") && method.getParameterCount() == 1) {
                setter = true;
                nameIndex = 3;
            } else if (methodName.startsWith("get") && method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                setter = false;
                nameIndex = 3;
            } else if (methodName.startsWith("is") && method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {
                setter = false;
                nameIndex = 2;
            } else {
                continue;
            }

            String propertyName = StringUtils.uncapitalizeAsProperty(methodName.substring(nameIndex));
            if (propertyName.isEmpty()) {
                continue;
            }

            BasicPropertyDescriptor pd = pdMap.get(propertyName);
            if (pd != null) {
                if (setter) {
                    Method writeMethod = pd.getWriteMethod();
                    if (writeMethod == null ||
                            writeMethod.getParameterTypes()[0].isAssignableFrom(method.getParameterTypes()[0])) {
                        pd.setWriteMethod(method);
                    } else {
                        pd.addWriteMethod(method);
                    }
                } else {
                    Method readMethod = pd.getReadMethod();
                    if (readMethod == null ||
                            (readMethod.getReturnType() == method.getReturnType() && method.getName().startsWith("is"))) {
                        pd.setReadMethod(method);
                    }
                }
            } else {
                pd = new BasicPropertyDescriptor(propertyName, (!setter ? method : null), (setter ? method : null));
                pdMap.put(propertyName, pd);
            }
        }

        return pdMap.values();
    }

    
    public static void copyNonMethodProperties(PropertyDescriptor source, PropertyDescriptor target) {
        target.setExpert(source.isExpert());
        target.setHidden(source.isHidden());
        target.setPreferred(source.isPreferred());
        target.setName(source.getName());
        target.setShortDescription(source.getShortDescription());
        target.setDisplayName(source.getDisplayName());

        // Copy all attributes (emulating behavior of private FeatureDescriptor#addTable)
        Enumeration<String> keys = source.attributeNames();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            target.setValue(key, source.getValue(key));
        }

        // See java.beans.PropertyDescriptor#PropertyDescriptor(PropertyDescriptor)
        target.setPropertyEditorClass(source.getPropertyEditorClass());
        target.setBound(source.isBound());
        target.setConstrained(source.isConstrained());
    }

    public static Class<?> findPropertyType(Method readMethod, Method writeMethod)
            throws IntrospectionException {

        Class<?> propertyType = null;

        if (readMethod != null) {
            if (readMethod.getParameterCount() != 0) {
                throw new IntrospectionException("Bad read method arg count: " + readMethod);
            }
            propertyType = readMethod.getReturnType();
            if (propertyType == void.class) {
                throw new IntrospectionException("Read method returns void: " + readMethod);
            }
        }

        if (writeMethod != null) {
            Class<?>[] params = writeMethod.getParameterTypes();
            if (params.length != 1) {
                throw new IntrospectionException("Bad write method arg count: " + writeMethod);
            }
            if (propertyType != null) {
                if (propertyType.isAssignableFrom(params[0])) {
                    // Write method's property type potentially more specific
                    propertyType = params[0];
                } else if (params[0].isAssignableFrom(propertyType)) {
                    // Proceed with read method's property type
                } else {
                    throw new IntrospectionException(
                            "Type mismatch between read and write methods: " + readMethod + " - " + writeMethod);
                }
            } else {
                propertyType = params[0];
            }
        }

        return propertyType;
    }

    public static Class<?> findIndexedPropertyType(String name, Class<?> propertyType, Method indexedReadMethod, Method indexedWriteMethod) throws IntrospectionException {

        Class<?> indexedPropertyType = null;

        if (indexedReadMethod != null) {
            Class<?>[] params = indexedReadMethod.getParameterTypes();
            if (params.length != 1) {
                throw new IntrospectionException("Bad indexed read method arg count: " + indexedReadMethod);
            }
            if (params[0] != int.class) {
                throw new IntrospectionException("Non int index to indexed read method: " + indexedReadMethod);
            }
            indexedPropertyType = indexedReadMethod.getReturnType();
            if (indexedPropertyType == void.class) {
                throw new IntrospectionException("Indexed read method returns void: " + indexedReadMethod);
            }
        }

        if (indexedWriteMethod != null) {
            Class<?>[] params = indexedWriteMethod.getParameterTypes();
            if (params.length != 2) {
                throw new IntrospectionException("Bad indexed write method arg count: " + indexedWriteMethod);
            }
            if (params[0] != int.class) {
                throw new IntrospectionException("Non int index to indexed write method: " + indexedWriteMethod);
            }
            if (indexedPropertyType != null) {
                if (indexedPropertyType.isAssignableFrom(params[1])) {
                    // Write method's property type potentially more specific
                    indexedPropertyType = params[1];
                } else if (params[1].isAssignableFrom(indexedPropertyType)) {
                    // Proceed with read method's property type
                } else {
                    throw new IntrospectionException("Type mismatch between indexed read and write methods: " +
                            indexedReadMethod + " - " + indexedWriteMethod);
                }
            } else {
                indexedPropertyType = params[1];
            }
        }

        if (propertyType != null && (!propertyType.isArray() ||
                propertyType.componentType() != indexedPropertyType)) {
            throw new IntrospectionException("Type mismatch between indexed and non-indexed methods: " +
                    indexedReadMethod + " - " + indexedWriteMethod);
        }

        return indexedPropertyType;
    }

    
    public static boolean equals(PropertyDescriptor pd, PropertyDescriptor otherPd) {
        return (ObjectUtils.nullSafeEquals(pd.getReadMethod(), otherPd.getReadMethod()) &&
                ObjectUtils.nullSafeEquals(pd.getWriteMethod(), otherPd.getWriteMethod()) &&
                ObjectUtils.nullSafeEquals(pd.getPropertyType(), otherPd.getPropertyType()) &&
                ObjectUtils.nullSafeEquals(pd.getPropertyEditorClass(), otherPd.getPropertyEditorClass()) &&
                pd.isBound() == otherPd.isBound() && pd.isConstrained() == otherPd.isConstrained());
    }


    
    private static class BasicPropertyDescriptor extends PropertyDescriptor {

        private Method readMethod;

        private Method writeMethod;

        private final List<Method> alternativeWriteMethods = new ArrayList<>();

        public BasicPropertyDescriptor(String propertyName, Method readMethod, Method writeMethod)
                throws IntrospectionException {

            super(propertyName, readMethod, writeMethod);
        }

        @Override
        public void setReadMethod(Method readMethod) {
            this.readMethod = readMethod;
        }

        @Override
        public Method getReadMethod() {
            return this.readMethod;
        }

        @Override
        public void setWriteMethod(Method writeMethod) {
            this.writeMethod = writeMethod;
        }

        public void addWriteMethod(Method writeMethod) {
            if (this.writeMethod != null) {
                this.alternativeWriteMethods.add(this.writeMethod);
                this.writeMethod = null;
            }
            this.alternativeWriteMethods.add(writeMethod);
        }

        @Override
        public Method getWriteMethod() {
            if (this.writeMethod == null && !this.alternativeWriteMethods.isEmpty()) {
                if (this.readMethod == null) {
                    return this.alternativeWriteMethods.get(0);
                } else {
                    for (Method method : this.alternativeWriteMethods) {
                        if (this.readMethod.getReturnType().isAssignableFrom(method.getParameterTypes()[0])) {
                            this.writeMethod = method;
                            break;
                        }
                    }
                }
            }
            return this.writeMethod;
        }
    }

}
