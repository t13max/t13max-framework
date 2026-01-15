package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeanMetadataAttributeAccessor;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ObjectUtils;
import com.t13max.ioc.utils.StringUtils;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 22:48 2026/1/15
 */
public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor implements BeanDefinition, Cloneable{

    
    public static final String SCOPE_DEFAULT = "";
    
    public static final int AUTOWIRE_NO = AutowireCapableBeanFactory.AUTOWIRE_NO;
    
    public static final int AUTOWIRE_BY_NAME = AutowireCapableBeanFactory.AUTOWIRE_BY_NAME;
    
    public static final int AUTOWIRE_BY_TYPE = AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE;
    
    public static final int AUTOWIRE_CONSTRUCTOR = AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;
    
    @Deprecated(since = "3.0")
    public static final int AUTOWIRE_AUTODETECT = AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT;
    
    public static final int DEPENDENCY_CHECK_NONE = 0;
    
    public static final int DEPENDENCY_CHECK_OBJECTS = 1;
    
    public static final int DEPENDENCY_CHECK_SIMPLE = 2;
    
    public static final int DEPENDENCY_CHECK_ALL = 3;
    
    public static final String PREFERRED_CONSTRUCTORS_ATTRIBUTE = "preferredConstructors";
    
    public static final String ORDER_ATTRIBUTE = "order";
    
    public static final String INFER_METHOD = "(inferred)";


    private volatile  Object beanClass;

    private  String scope = SCOPE_DEFAULT;

    private boolean abstractFlag = false;

    private boolean backgroundInit = false;

    private  Boolean lazyInit;

    private int autowireMode = AUTOWIRE_NO;

    private int dependencyCheck = DEPENDENCY_CHECK_NONE;

    private String  [] dependsOn;

    private boolean autowireCandidate = true;

    private boolean defaultCandidate = true;

    private boolean primary = false;

    private boolean fallback = false;

    private final Map<String, AutowireCandidateQualifier> qualifiers = new LinkedHashMap<>();

    private  Supplier<?> instanceSupplier;

    private boolean nonPublicAccessAllowed = true;

    private boolean lenientConstructorResolution = true;

    private  String factoryBeanName;

    private  String factoryMethodName;

    private  ConstructorArgumentValues constructorArgumentValues;

    private  MutablePropertyValues propertyValues;

    private MethodOverrides methodOverrides = new MethodOverrides();

    private String  [] initMethodNames;

    private String  [] destroyMethodNames;

    private boolean enforceInitMethod = true;

    private boolean enforceDestroyMethod = true;

    private boolean synthetic = false;

    private int role = BeanDefinition.ROLE_APPLICATION;

    private  String description;

    private  Resource resource;

    
    protected AbstractBeanDefinition() {
        this(null, null);
    }
    
    protected AbstractBeanDefinition( ConstructorArgumentValues cargs,  MutablePropertyValues pvs) {
        this.constructorArgumentValues = cargs;
        this.propertyValues = pvs;
    }
    
    protected AbstractBeanDefinition(BeanDefinition original) {
        setParentName(original.getParentName());
        setBeanClassName(original.getBeanClassName());
        setScope(original.getScope());
        setAbstract(original.isAbstract());
        setFactoryBeanName(original.getFactoryBeanName());
        setFactoryMethodName(original.getFactoryMethodName());
        setRole(original.getRole());
        setSource(original.getSource());
        copyAttributesFrom(original);

        if (original instanceof AbstractBeanDefinition originalAbd) {
            if (originalAbd.hasBeanClass()) {
                setBeanClass(originalAbd.getBeanClass());
            }
            if (originalAbd.hasConstructorArgumentValues()) {
                setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
            }
            if (originalAbd.hasPropertyValues()) {
                setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
            }
            if (originalAbd.hasMethodOverrides()) {
                setMethodOverrides(new MethodOverrides(originalAbd.getMethodOverrides()));
            }
            setBackgroundInit(originalAbd.isBackgroundInit());
            Boolean lazyInit = originalAbd.getLazyInit();
            if (lazyInit != null) {
                setLazyInit(lazyInit);
            }
            setAutowireMode(originalAbd.getAutowireMode());
            setDependencyCheck(originalAbd.getDependencyCheck());
            setDependsOn(originalAbd.getDependsOn());
            setAutowireCandidate(originalAbd.isAutowireCandidate());
            setDefaultCandidate(originalAbd.isDefaultCandidate());
            setPrimary(originalAbd.isPrimary());
            setFallback(originalAbd.isFallback());
            copyQualifiersFrom(originalAbd);
            setInstanceSupplier(originalAbd.getInstanceSupplier());
            setNonPublicAccessAllowed(originalAbd.isNonPublicAccessAllowed());
            setLenientConstructorResolution(originalAbd.isLenientConstructorResolution());
            setInitMethodNames(originalAbd.getInitMethodNames());
            setEnforceInitMethod(originalAbd.isEnforceInitMethod());
            setDestroyMethodNames(originalAbd.getDestroyMethodNames());
            setEnforceDestroyMethod(originalAbd.isEnforceDestroyMethod());
            setSynthetic(originalAbd.isSynthetic());
            setResource(originalAbd.getResource());
        }
        else {
            setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
            setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
            setLazyInit(original.isLazyInit());
            setResourceDescription(original.getResourceDescription());
        }
    }

    
    public void overrideFrom(BeanDefinition other) {
        if (StringUtils.hasLength(other.getBeanClassName())) {
            setBeanClassName(other.getBeanClassName());
        }
        if (StringUtils.hasLength(other.getScope())) {
            setScope(other.getScope());
        }
        setAbstract(other.isAbstract());
        if (StringUtils.hasLength(other.getFactoryBeanName())) {
            setFactoryBeanName(other.getFactoryBeanName());
        }
        if (StringUtils.hasLength(other.getFactoryMethodName())) {
            setFactoryMethodName(other.getFactoryMethodName());
        }
        setRole(other.getRole());
        setSource(other.getSource());
        copyAttributesFrom(other);

        if (other instanceof AbstractBeanDefinition otherAbd) {
            if (otherAbd.hasBeanClass()) {
                setBeanClass(otherAbd.getBeanClass());
            }
            if (otherAbd.hasConstructorArgumentValues()) {
                getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
            }
            if (otherAbd.hasPropertyValues()) {
                getPropertyValues().addPropertyValues(other.getPropertyValues());
            }
            if (otherAbd.hasMethodOverrides()) {
                getMethodOverrides().addOverrides(otherAbd.getMethodOverrides());
            }
            setBackgroundInit(otherAbd.isBackgroundInit());
            Boolean lazyInit = otherAbd.getLazyInit();
            if (lazyInit != null) {
                setLazyInit(lazyInit);
            }
            setAutowireMode(otherAbd.getAutowireMode());
            setDependencyCheck(otherAbd.getDependencyCheck());
            setDependsOn(otherAbd.getDependsOn());
            setAutowireCandidate(otherAbd.isAutowireCandidate());
            setDefaultCandidate(otherAbd.isDefaultCandidate());
            setPrimary(otherAbd.isPrimary());
            setFallback(otherAbd.isFallback());
            copyQualifiersFrom(otherAbd);
            setInstanceSupplier(otherAbd.getInstanceSupplier());
            setNonPublicAccessAllowed(otherAbd.isNonPublicAccessAllowed());
            setLenientConstructorResolution(otherAbd.isLenientConstructorResolution());
            if (otherAbd.getInitMethodNames() != null) {
                setInitMethodNames(otherAbd.getInitMethodNames());
                setEnforceInitMethod(otherAbd.isEnforceInitMethod());
            }
            if (otherAbd.getDestroyMethodNames() != null) {
                setDestroyMethodNames(otherAbd.getDestroyMethodNames());
                setEnforceDestroyMethod(otherAbd.isEnforceDestroyMethod());
            }
            setSynthetic(otherAbd.isSynthetic());
            setResource(otherAbd.getResource());
        }
        else {
            getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
            getPropertyValues().addPropertyValues(other.getPropertyValues());
            setLazyInit(other.isLazyInit());
            setResourceDescription(other.getResourceDescription());
        }
    }
    
    public void applyDefaults(BeanDefinitionDefaults defaults) {
        Boolean lazyInit = defaults.getLazyInit();
        if (lazyInit != null) {
            setLazyInit(lazyInit);
        }
        setAutowireMode(defaults.getAutowireMode());
        setDependencyCheck(defaults.getDependencyCheck());
        setInitMethodName(defaults.getInitMethodName());
        setEnforceInitMethod(false);
        setDestroyMethodName(defaults.getDestroyMethodName());
        setEnforceDestroyMethod(false);
    }

    
    @Override
    public void setBeanClassName( String beanClassName) {
        this.beanClass = beanClassName;
    }
    
    @Override
    public  String getBeanClassName() {
        Object beanClassObject = this.beanClass;  // defensive access to volatile beanClass field
        return (beanClassObject instanceof Class<?> clazz ? clazz.getName() : (String) beanClassObject);
    }
    
    public void setBeanClass( Class<?> beanClass) {
        this.beanClass = beanClass;
    }
    
    public Class<?> getBeanClass() throws IllegalStateException {
        Object beanClassObject = this.beanClass;  // defensive access to volatile beanClass field
        if (beanClassObject == null) {
            throw new IllegalStateException("No bean class specified on bean definition");
        }
        if (!(beanClassObject instanceof Class<?> clazz)) {
            throw new IllegalStateException(
                    "Bean class name [" + beanClassObject + "] has not been resolved into an actual Class");
        }
        return clazz;
    }
    
    public boolean hasBeanClass() {
        return (this.beanClass instanceof Class);
    }
    
    public  Class<?> resolveBeanClass( ClassLoader classLoader) throws ClassNotFoundException {
        String className = getBeanClassName();
        if (className == null) {
            return null;
        }
        Class<?> resolvedClass = ClassUtils.forName(className, classLoader);
        this.beanClass = resolvedClass;
        return resolvedClass;
    }
    
    @Override
    public ResolvableType getResolvableType() {
        return (hasBeanClass() ? ResolvableType.forClass(getBeanClass()) : ResolvableType.NONE);
    }
    
    @Override
    public void setScope( String scope) {
        this.scope = scope;
    }
    
    @Override
    public  String getScope() {
        return this.scope;
    }
    
    @Override
    public boolean isSingleton() {
        return SCOPE_SINGLETON.equals(this.scope) || SCOPE_DEFAULT.equals(this.scope);
    }
    
    @Override
    public boolean isPrototype() {
        return SCOPE_PROTOTYPE.equals(this.scope);
    }
    
    public void setAbstract(boolean abstractFlag) {
        this.abstractFlag = abstractFlag;
    }
    
    @Override
    public boolean isAbstract() {
        return this.abstractFlag;
    }
    
    public void setBackgroundInit(boolean backgroundInit) {
        this.backgroundInit = backgroundInit;
    }
    
    public boolean isBackgroundInit() {
        return this.backgroundInit;
    }
    
    @Override
    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }
    
    @Override
    public boolean isLazyInit() {
        return (this.lazyInit != null && this.lazyInit);
    }
    
    public  Boolean getLazyInit() {
        return this.lazyInit;
    }
    
    public void setAutowireMode(int autowireMode) {
        this.autowireMode = autowireMode;
    }
    
    public int getAutowireMode() {
        return this.autowireMode;
    }
    
    public int getResolvedAutowireMode() {
        if (this.autowireMode == AUTOWIRE_AUTODETECT) {
            // Work out whether to apply setter autowiring or constructor autowiring.
            // If it has a no-arg constructor it's deemed to be setter autowiring,
            // otherwise we'll try constructor autowiring.
            Constructor<?>[] constructors = getBeanClass().getConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    return AUTOWIRE_BY_TYPE;
                }
            }
            return AUTOWIRE_CONSTRUCTOR;
        }
        else {
            return this.autowireMode;
        }
    }
    
    public void setDependencyCheck(int dependencyCheck) {
        this.dependencyCheck = dependencyCheck;
    }
    
    public int getDependencyCheck() {
        return this.dependencyCheck;
    }
    
    @Override
    public void setDependsOn(String  ... dependsOn) {
        this.dependsOn = dependsOn;
    }
    
    @Override
    public String  [] getDependsOn() {
        return this.dependsOn;
    }
    
    @Override
    public void setAutowireCandidate(boolean autowireCandidate) {
        this.autowireCandidate = autowireCandidate;
    }
    
    @Override
    public boolean isAutowireCandidate() {
        return this.autowireCandidate;
    }
    
    public void setDefaultCandidate(boolean defaultCandidate) {
        this.defaultCandidate = defaultCandidate;
    }
    
    public boolean isDefaultCandidate() {
        return this.defaultCandidate;
    }
    
    @Override
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
    
    @Override
    public boolean isPrimary() {
        return this.primary;
    }
    
    @Override
    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }
    
    @Override
    public boolean isFallback() {
        return this.fallback;
    }
    
    public void addQualifier(AutowireCandidateQualifier qualifier) {
        this.qualifiers.put(qualifier.getTypeName(), qualifier);
    }
    
    public boolean hasQualifier(String typeName) {
        return this.qualifiers.containsKey(typeName);
    }
    
    public  AutowireCandidateQualifier getQualifier(String typeName) {
        return this.qualifiers.get(typeName);
    }
    
    public Set<AutowireCandidateQualifier> getQualifiers() {
        return new LinkedHashSet<>(this.qualifiers.values());
    }
    
    public void copyQualifiersFrom(AbstractBeanDefinition source) {
        Assert.notNull(source, "Source must not be null");
        this.qualifiers.putAll(source.qualifiers);
    }
    
    public void setInstanceSupplier( Supplier<?> instanceSupplier) {
        this.instanceSupplier = instanceSupplier;
    }
    
    public  Supplier<?> getInstanceSupplier() {
        return this.instanceSupplier;
    }
    
    public void setNonPublicAccessAllowed(boolean nonPublicAccessAllowed) {
        this.nonPublicAccessAllowed = nonPublicAccessAllowed;
    }
    
    public boolean isNonPublicAccessAllowed() {
        return this.nonPublicAccessAllowed;
    }
    
    public void setLenientConstructorResolution(boolean lenientConstructorResolution) {
        this.lenientConstructorResolution = lenientConstructorResolution;
    }
    
    public boolean isLenientConstructorResolution() {
        return this.lenientConstructorResolution;
    }
    
    @Override
    public void setFactoryBeanName( String factoryBeanName) {
        this.factoryBeanName = factoryBeanName;
    }
    
    @Override
    public  String getFactoryBeanName() {
        return this.factoryBeanName;
    }
    
    @Override
    public void setFactoryMethodName( String factoryMethodName) {
        this.factoryMethodName = factoryMethodName;
    }
    
    @Override
    public  String getFactoryMethodName() {
        return this.factoryMethodName;
    }
    
    public void setConstructorArgumentValues(ConstructorArgumentValues constructorArgumentValues) {
        this.constructorArgumentValues = constructorArgumentValues;
    }
    
    @Override
    public ConstructorArgumentValues getConstructorArgumentValues() {
        ConstructorArgumentValues cav = this.constructorArgumentValues;
        if (cav == null) {
            cav = new ConstructorArgumentValues();
            this.constructorArgumentValues = cav;
        }
        return cav;
    }
    
    @Override
    public boolean hasConstructorArgumentValues() {
        return (this.constructorArgumentValues != null && !this.constructorArgumentValues.isEmpty());
    }
    
    public void setPropertyValues(MutablePropertyValues propertyValues) {
        this.propertyValues = propertyValues;
    }
    
    @Override
    public MutablePropertyValues getPropertyValues() {
        MutablePropertyValues pvs = this.propertyValues;
        if (pvs == null) {
            pvs = new MutablePropertyValues();
            this.propertyValues = pvs;
        }
        return pvs;
    }
    
    @Override
    public boolean hasPropertyValues() {
        return (this.propertyValues != null && !this.propertyValues.isEmpty());
    }
    
    public void setMethodOverrides(MethodOverrides methodOverrides) {
        this.methodOverrides = methodOverrides;
    }
    
    public MethodOverrides getMethodOverrides() {
        return this.methodOverrides;
    }
    
    public boolean hasMethodOverrides() {
        return !this.methodOverrides.isEmpty();
    }
    
    public void setInitMethodNames(String  ... initMethodNames) {
        this.initMethodNames = initMethodNames;
    }
    
    public String  [] getInitMethodNames() {
        return this.initMethodNames;
    }
    
    @Override
    public void setInitMethodName( String initMethodName) {
        this.initMethodNames = (initMethodName != null ? new String[] {initMethodName} : null);
    }
    
    @Override
    public  String getInitMethodName() {
        return (!ObjectUtils.isEmpty(this.initMethodNames) ? this.initMethodNames[0] : null);
    }
    
    public void setEnforceInitMethod(boolean enforceInitMethod) {
        this.enforceInitMethod = enforceInitMethod;
    }
    
    public boolean isEnforceInitMethod() {
        return this.enforceInitMethod;
    }
    
    public void setDestroyMethodNames(String  ... destroyMethodNames) {
        this.destroyMethodNames = destroyMethodNames;
    }
    
    public String  [] getDestroyMethodNames() {
        return this.destroyMethodNames;
    }
    
    @Override
    public void setDestroyMethodName( String destroyMethodName) {
        this.destroyMethodNames = (destroyMethodName != null ? new String[] {destroyMethodName} : null);
    }
    
    @Override
    public  String getDestroyMethodName() {
        return (!ObjectUtils.isEmpty(this.destroyMethodNames) ? this.destroyMethodNames[0] : null);
    }
    
    public void setEnforceDestroyMethod(boolean enforceDestroyMethod) {
        this.enforceDestroyMethod = enforceDestroyMethod;
    }
    
    public boolean isEnforceDestroyMethod() {
        return this.enforceDestroyMethod;
    }
    
    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }
    
    public boolean isSynthetic() {
        return this.synthetic;
    }
    
    @Override
    public void setRole(int role) {
        this.role = role;
    }
    
    @Override
    public int getRole() {
        return this.role;
    }
    
    @Override
    public void setDescription( String description) {
        this.description = description;
    }
    
    @Override
    public  String getDescription() {
        return this.description;
    }
    
    public void setResource( Resource resource) {
        this.resource = resource;
    }
    
    public  Resource getResource() {
        return this.resource;
    }
    
    public void setResourceDescription( String resourceDescription) {
        this.resource = (resourceDescription != null ? new DescriptiveResource(resourceDescription) : null);
    }
    
    @Override
    public  String getResourceDescription() {
        return (this.resource != null ? this.resource.getDescription() : null);
    }
    
    public void setOriginatingBeanDefinition(BeanDefinition originatingBd) {
        this.resource = new BeanDefinitionResource(originatingBd);
    }
    
    @Override
    public  BeanDefinition getOriginatingBeanDefinition() {
        return (this.resource instanceof BeanDefinitionResource bdr ? bdr.getBeanDefinition() : null);
    }
    
    public void validate() throws BeanDefinitionValidationException {
        if (hasMethodOverrides() && getFactoryMethodName() != null) {
            throw new BeanDefinitionValidationException(
                    "Cannot combine factory method with container-generated method overrides: " +
                            "the factory method must create the concrete bean instance.");
        }
        if (hasBeanClass()) {
            prepareMethodOverrides();
        }
    }
    
    public void prepareMethodOverrides() throws BeanDefinitionValidationException {
        // Check that lookup methods exist and determine their overloaded status.
        if (hasMethodOverrides()) {
            getMethodOverrides().getOverrides().forEach(this::prepareMethodOverride);
        }
    }
    
    protected void prepareMethodOverride(MethodOverride mo) throws BeanDefinitionValidationException {
        int count = ClassUtils.getMethodCountForName(getBeanClass(), mo.getMethodName());
        if (count == 0) {
            throw new BeanDefinitionValidationException(
                    "Invalid method override: no method with name '" + mo.getMethodName() +
                            "' on class [" + getBeanClassName() + "]");
        }
        else if (count == 1) {
            // Mark override as not overloaded, to avoid the overhead of arg type checking.
            mo.setOverloaded(false);
        }
    }

    
    @Override
    public Object clone() {
        return cloneBeanDefinition();
    }
    
    public abstract AbstractBeanDefinition cloneBeanDefinition();

    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof AbstractBeanDefinition that &&
                ObjectUtils.nullSafeEquals(getBeanClassName(), that.getBeanClassName()) &&
                ObjectUtils.nullSafeEquals(this.scope, that.scope) &&
                this.abstractFlag == that.abstractFlag &&
                this.lazyInit == that.lazyInit &&
                this.autowireMode == that.autowireMode &&
                this.dependencyCheck == that.dependencyCheck &&
                Arrays.equals(this.dependsOn, that.dependsOn) &&
                this.autowireCandidate == that.autowireCandidate &&
                ObjectUtils.nullSafeEquals(this.qualifiers, that.qualifiers) &&
                this.primary == that.primary &&
                this.nonPublicAccessAllowed == that.nonPublicAccessAllowed &&
                this.lenientConstructorResolution == that.lenientConstructorResolution &&
                equalsConstructorArgumentValues(that) &&
                equalsPropertyValues(that) &&
                ObjectUtils.nullSafeEquals(this.methodOverrides, that.methodOverrides) &&
                ObjectUtils.nullSafeEquals(this.factoryBeanName, that.factoryBeanName) &&
                ObjectUtils.nullSafeEquals(this.factoryMethodName, that.factoryMethodName) &&
                ObjectUtils.nullSafeEquals(this.initMethodNames, that.initMethodNames) &&
                this.enforceInitMethod == that.enforceInitMethod &&
                ObjectUtils.nullSafeEquals(this.destroyMethodNames, that.destroyMethodNames) &&
                this.enforceDestroyMethod == that.enforceDestroyMethod &&
                this.synthetic == that.synthetic &&
                this.role == that.role &&
                super.equals(other)));
    }

    private boolean equalsConstructorArgumentValues(AbstractBeanDefinition other) {
        if (!hasConstructorArgumentValues()) {
            return !other.hasConstructorArgumentValues();
        }
        return ObjectUtils.nullSafeEquals(this.constructorArgumentValues, other.constructorArgumentValues);
    }

    private boolean equalsPropertyValues(AbstractBeanDefinition other) {
        if (!hasPropertyValues()) {
            return !other.hasPropertyValues();
        }
        return ObjectUtils.nullSafeEquals(this.propertyValues, other.propertyValues);
    }

    @Override
    public int hashCode() {
        int hashCode = ObjectUtils.nullSafeHashCode(getBeanClassName());
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.scope);
        if (hasConstructorArgumentValues()) {
            hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.constructorArgumentValues);
        }
        if (hasPropertyValues()) {
            hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.propertyValues);
        }
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryBeanName);
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryMethodName);
        hashCode = 29 * hashCode + super.hashCode();
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("class=").append(getBeanClassName());
        sb.append("; scope=").append(this.scope);
        sb.append("; abstract=").append(this.abstractFlag);
        sb.append("; lazyInit=").append(this.lazyInit);
        sb.append("; autowireMode=").append(this.autowireMode);
        sb.append("; dependencyCheck=").append(this.dependencyCheck);
        sb.append("; autowireCandidate=").append(this.autowireCandidate);
        sb.append("; primary=").append(this.primary);
        sb.append("; fallback=").append(this.fallback);
        sb.append("; factoryBeanName=").append(this.factoryBeanName);
        sb.append("; factoryMethodName=").append(this.factoryMethodName);
        sb.append("; initMethodNames=").append(Arrays.toString(this.initMethodNames));
        sb.append("; destroyMethodNames=").append(Arrays.toString(this.destroyMethodNames));
        if (this.resource != null) {
            sb.append("; defined in ").append(this.resource.getDescription());
        }
        return sb.toString();
    }
}
