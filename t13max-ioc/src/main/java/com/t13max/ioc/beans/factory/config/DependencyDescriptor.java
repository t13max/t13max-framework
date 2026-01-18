package com.t13max.ioc.beans.factory.config;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.BeanNotOfRequiredTypeException;
import com.t13max.ioc.beans.factory.InjectionPoint;
import com.t13max.ioc.beans.factory.NoUniqueBeanDefinitionException;
import com.t13max.ioc.core.MethodParameter;
import com.t13max.ioc.core.Nullness;
import com.t13max.ioc.core.ParameterNameDiscoverer;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.convert.TypeDescriptor;
import com.t13max.ioc.util.ObjectUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * @Author: t13max
 * @Since: 22:08 2026/1/15
 */
public class DependencyDescriptor extends InjectionPoint implements Serializable {
    
    private final Class<?> declaringClass;

    private  String methodName;

    private Class<?>  [] parameterTypes;

    private int parameterIndex;

    private  String fieldName;

    private final boolean required;

    private final boolean eager;

    private int nestingLevel = 1;

    private  Class<?> containingClass;

    private transient volatile  ResolvableType resolvableType;

    private transient volatile TypeDescriptor typeDescriptor;


    
    public DependencyDescriptor(MethodParameter methodParameter, boolean required) {
        this(methodParameter, required, true);
    }

    
    public DependencyDescriptor(MethodParameter methodParameter, boolean required, boolean eager) {
        super(methodParameter);

        this.declaringClass = methodParameter.getDeclaringClass();
        if (methodParameter.getMethod() != null) {
            this.methodName = methodParameter.getMethod().getName();
        }
        this.parameterTypes = methodParameter.getExecutable().getParameterTypes();
        this.parameterIndex = methodParameter.getParameterIndex();
        this.containingClass = methodParameter.getContainingClass();
        this.required = required;
        this.eager = eager;
    }

    
    public DependencyDescriptor(Field field, boolean required) {
        this(field, required, true);
    }

    
    public DependencyDescriptor(Field field, boolean required, boolean eager) {
        super(field);

        this.declaringClass = field.getDeclaringClass();
        this.fieldName = field.getName();
        this.required = required;
        this.eager = eager;
    }

    
    public DependencyDescriptor(DependencyDescriptor original) {
        super(original);

        this.declaringClass = original.declaringClass;
        this.methodName = original.methodName;
        this.parameterTypes = original.parameterTypes;
        this.parameterIndex = original.parameterIndex;
        this.fieldName = original.fieldName;
        this.required = original.required;
        this.eager = original.eager;
        this.nestingLevel = original.nestingLevel;
        this.containingClass = original.containingClass;
    }


    
    public boolean isRequired() {
        if (!this.required) {
            return false;
        }

        if (this.field != null) {
            return !(this.field.getType() == Optional.class || Nullness.forField(this.field) == Nullness.NULLABLE);
        }
        else {
            return !obtainMethodParameter().isOptional();
        }
    }

    
    public boolean isEager() {
        return this.eager;
    }

    
    public  Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) throws BeansException {
        throw new NoUniqueBeanDefinitionException(type, matchingBeans.keySet());
    }

    
    public  Object resolveShortcut(BeanFactory beanFactory) throws BeansException {
        return null;
    }

    
    public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory)
            throws BeansException {

        try {
            // Need to provide required type for SmartFactoryBean
            return beanFactory.getBean(beanName, requiredType);
        }
        catch (BeanNotOfRequiredTypeException ex) {
            // Probably a null bean...
            return beanFactory.getBean(beanName);
        }
    }


    
    public void increaseNestingLevel() {
        this.nestingLevel++;
        this.resolvableType = null;
        if (this.methodParameter != null) {
            this.methodParameter = this.methodParameter.nested();
        }
    }

    
    public void setContainingClass(Class<?> containingClass) {
        this.containingClass = containingClass;
        this.resolvableType = null;
        if (this.methodParameter != null) {
            this.methodParameter = this.methodParameter.withContainingClass(containingClass);
        }
    }

    
    public ResolvableType getResolvableType() {
        ResolvableType resolvableType = this.resolvableType;
        if (resolvableType == null) {
            resolvableType = (this.field != null ?
                    ResolvableType.forField(this.field, this.nestingLevel, this.containingClass) :
                    ResolvableType.forMethodParameter(obtainMethodParameter()));
            this.resolvableType = resolvableType;
        }
        return resolvableType;
    }

    public TypeDescriptor getTypeDescriptor() {
        TypeDescriptor typeDescriptor = this.typeDescriptor;
        if (typeDescriptor == null) {
            typeDescriptor = (this.field != null ?
                    new TypeDescriptor(getResolvableType(), getDependencyType(), getAnnotations()) :
                    new TypeDescriptor(obtainMethodParameter()));
            this.typeDescriptor = typeDescriptor;
        }
        return typeDescriptor;
    }

    public boolean fallbackMatchAllowed() {
        return false;
    }

    
    public DependencyDescriptor forFallbackMatch() {
        return new DependencyDescriptor(this) {
            @Override
            public boolean fallbackMatchAllowed() {
                return true;
            }
            @Override
            public boolean usesStandardBeanLookup() {
                return true;
            }
        };
    }

    
    public void initParameterNameDiscovery( ParameterNameDiscoverer parameterNameDiscoverer) {
        if (this.methodParameter != null) {
            this.methodParameter.initParameterNameDiscovery(parameterNameDiscoverer);
        }
    }

    
    public  String getDependencyName() {
        return (this.field != null ? this.field.getName() : obtainMethodParameter().getParameterName());
    }

    
    public Class<?> getDependencyType() {
        if (this.field != null) {
            if (this.nestingLevel > 1) {
                Class<?> clazz = getResolvableType().getRawClass();
                return (clazz != null ? clazz : Object.class);
            }
            else {
                return this.field.getType();
            }
        }
        else {
            return obtainMethodParameter().getNestedParameterType();
        }
    }

    public boolean supportsLazyResolution() {
        return true;
    }

    
    public boolean usesStandardBeanLookup() {
        return (getClass() == DependencyDescriptor.class);
    }


    @Override
    public boolean equals( Object other) {
        if (this == other) {
            return true;
        }
        if (!super.equals(other)) {
            return false;
        }
        return (other instanceof DependencyDescriptor otherDesc && this.required == otherDesc.required &&
                this.eager == otherDesc.eager && this.nestingLevel == otherDesc.nestingLevel &&
                this.containingClass == otherDesc.containingClass);
    }

    @Override
    public int hashCode() {
        return (31 * super.hashCode() + ObjectUtils.nullSafeHashCode(this.containingClass));
    }


    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Rely on default serialization; just initialize state after deserialization.
        ois.defaultReadObject();

        // Restore reflective handles (which are unfortunately not serializable)
        try {
            if (this.fieldName != null) {
                this.field = this.declaringClass.getDeclaredField(this.fieldName);
            }
            else {
                if (this.methodName != null) {
                    this.methodParameter = new MethodParameter(
                            this.declaringClass.getDeclaredMethod(this.methodName, this.parameterTypes), this.parameterIndex);
                }
                else {
                    this.methodParameter = new MethodParameter(
                            this.declaringClass.getDeclaredConstructor(this.parameterTypes), this.parameterIndex);
                }
                for (int i = 1; i < this.nestingLevel; i++) {
                    this.methodParameter = this.methodParameter.nested();
                }
            }
        }
        catch (Throwable ex) {
            throw new IllegalStateException("Could not find original class structure", ex);
        }
    }
}
