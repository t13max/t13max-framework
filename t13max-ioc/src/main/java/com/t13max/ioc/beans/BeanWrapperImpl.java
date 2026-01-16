package com.t13max.ioc.beans;

import com.t13max.ioc.core.MethodParameter;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ReflectionUtils;

import java.beans.PropertyDescriptor;
import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 14:06 2026/1/16
 */
public class BeanWrapperImpl extends AbstractNestablePropertyAccessor implements BeanWrapper {

    private CachedIntrospectionResults cachedIntrospectionResults;

    public BeanWrapperImpl() {
        this(true);
    }

    public BeanWrapperImpl(boolean registerDefaultEditors) {
        super(registerDefaultEditors);
    }

    public BeanWrapperImpl(Object object) {
        super(object);
    }

    public BeanWrapperImpl(Class<?> clazz) {
        super(clazz);
    }

    public BeanWrapperImpl(Object object, String nestedPath, Object rootObject) {
        super(object, nestedPath, rootObject);
    }

    private BeanWrapperImpl(Object object, String nestedPath, BeanWrapperImpl parent) {
        super(object, nestedPath, parent);
    }

    public void setBeanInstance(Object object) {
        this.wrappedObject = object;
        this.rootObject = object;
        this.typeConverterDelegate = new TypeConverterDelegate(this, this.wrappedObject);
        setIntrospectionClass(object.getClass());
    }

    @Override
    public void setWrappedInstance(Object object,  String nestedPath,  Object rootObject) {
        super.setWrappedInstance(object, nestedPath, rootObject);
        setIntrospectionClass(getWrappedClass());
    }

    protected void setIntrospectionClass(Class<?> clazz) {
        if (this.cachedIntrospectionResults != null && this.cachedIntrospectionResults.getBeanClass() != clazz) {
            this.cachedIntrospectionResults = null;
        }
    }

    private CachedIntrospectionResults getCachedIntrospectionResults() {
        if (this.cachedIntrospectionResults == null) {
            this.cachedIntrospectionResults = CachedIntrospectionResults.forClass(getWrappedClass());
        }
        return this.cachedIntrospectionResults;
    }

    public Object convertForProperty( Object value, String propertyName) throws TypeMismatchException {
        CachedIntrospectionResults cachedIntrospectionResults = getCachedIntrospectionResults();
        PropertyDescriptor pd = cachedIntrospectionResults.getPropertyDescriptor(propertyName);
        if (pd == null) {
            throw new InvalidPropertyException(getRootClass(), getNestedPath() + propertyName, "No property '" + propertyName + "' found");
        }
        TypeDescriptor td = ((GenericTypeAwarePropertyDescriptor) pd).getTypeDescriptor();
        return convertForProperty(propertyName, null, value, td);
    }

    @Override
    protected BeanPropertyHandler getLocalPropertyHandler(String propertyName) {
        PropertyDescriptor pd = getCachedIntrospectionResults().getPropertyDescriptor(propertyName);
        return (pd != null ? new BeanPropertyHandler((GenericTypeAwarePropertyDescriptor) pd) : null);
    }

    @Override
    protected BeanWrapperImpl newNestedPropertyAccessor(Object object, String nestedPath) {
        return new BeanWrapperImpl(object, nestedPath, this);
    }

    @Override
    protected NotWritablePropertyException createNotWritablePropertyException(String propertyName) {
        PropertyMatches matches = PropertyMatches.forProperty(propertyName, getRootClass());
        throw new NotWritablePropertyException(getRootClass(), getNestedPath() + propertyName, matches.buildErrorMessage(), matches.getPossibleMatches());
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        return getCachedIntrospectionResults().getPropertyDescriptors();
    }

    @Override
    public PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException {
        BeanWrapperImpl nestedBw = (BeanWrapperImpl) getPropertyAccessorForPropertyPath(propertyName);
        String finalPath = getFinalPath(nestedBw, propertyName);
        PropertyDescriptor pd = nestedBw.getCachedIntrospectionResults().getPropertyDescriptor(finalPath);
        if (pd == null) {
            throw new InvalidPropertyException(getRootClass(), getNestedPath() + propertyName, "No property '" + propertyName + "' found");
        }
        return pd;
    }


    private class BeanPropertyHandler extends PropertyHandler {

        private final GenericTypeAwarePropertyDescriptor pd;

        public BeanPropertyHandler(GenericTypeAwarePropertyDescriptor pd) {
            super(pd.getPropertyType(), pd.getReadMethod() != null, pd.getWriteMethod() != null);
            this.pd = pd;
        }

        @Override
        public TypeDescriptor toTypeDescriptor() {
            return this.pd.getTypeDescriptor();
        }

        @Override
        public ResolvableType getResolvableType() {
            return this.pd.getReadMethodType();
        }

        @Override
        public TypeDescriptor getMapValueType(int nestingLevel) {
            return new TypeDescriptor(this.pd.getReadMethodType().getNested(nestingLevel).asMap().getGeneric(1), null, this.pd.getTypeDescriptor().getAnnotations());
        }

        @Override
        public TypeDescriptor getCollectionType(int nestingLevel) {
            return new TypeDescriptor(this.pd.getReadMethodType().getNested(nestingLevel).asCollection().getGeneric(), null, this.pd.getTypeDescriptor().getAnnotations());
        }

        @Override
        
        public TypeDescriptor nested(int level) {
            return this.pd.getTypeDescriptor().nested(level);
        }

        @Override
        
        public Object getValue() throws Exception {
            Method readMethod = this.pd.getReadMethod();
            Assert.state(readMethod != null, "No read method available");
            ReflectionUtils.makeAccessible(readMethod);
            return readMethod.invoke(getWrappedInstance(), (Object[]) null);
        }

        @Override
        public void setValue( Object value) throws Exception {
            Method writeMethod = this.pd.getWriteMethodForActualAccess();
            ReflectionUtils.makeAccessible(writeMethod);
            writeMethod.invoke(getWrappedInstance(), value);
        }

        @Override
        public boolean setValueFallbackIfPossible( Object value) {
            try {
                Method writeMethod = this.pd.getWriteMethodFallback(value != null ? value.getClass() : null);
                if (writeMethod == null) {
                    writeMethod = this.pd.getUniqueWriteMethodFallback();
                    if (writeMethod != null) {
                        // Conversion necessary as we would otherwise have received the method
                        // from the type-matching getWriteMethodFallback call above already
                        value = convertForProperty(this.pd.getName(), null, value,
                                new TypeDescriptor(new MethodParameter(writeMethod, 0)));
                    }
                }
                if (writeMethod != null) {
                    ReflectionUtils.makeAccessible(writeMethod);
                    writeMethod.invoke(getWrappedInstance(), value);
                    return true;
                }
            }
            catch (Exception ex) {
                LogFactory.getLog(BeanPropertyHandler.class).debug("Write method fallback failed", ex);
            }
            return false;
        }
    }
}
