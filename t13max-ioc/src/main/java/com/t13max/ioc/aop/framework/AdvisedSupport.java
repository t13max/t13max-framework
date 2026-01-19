package com.t13max.ioc.aop.framework;

import com.t13max.ioc.aop.*;
import com.t13max.ioc.aop.support.DefaultIntroductionAdvisor;
import com.t13max.ioc.aop.support.DefaultPointcutAdvisor;
import com.t13max.ioc.aop.target.EmptyTargetSource;
import com.t13max.ioc.aop.target.SingletonTargetSource;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.CollectionUtils;
import com.t13max.ioc.util.ObjectUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author t13max
 * @since 16:28 2026/1/16
 */
public class AdvisedSupport extends ProxyConfig implements Advised {

    private static final long serialVersionUID = 2651364800145442165L;

    public static final TargetSource EMPTY_TARGET_SOURCE = EmptyTargetSource.INSTANCE;

    //包级私有的属性, 高效访问
    TargetSource targetSource = EMPTY_TARGET_SOURCE;
    private boolean preFiltered = false;
    private AdvisorChainFactory advisorChainFactory = DefaultAdvisorChainFactory.INSTANCE;
    private List<Class<?>> interfaces = new ArrayList<>();
    private List<Advisor> advisors = new ArrayList<>();
    private List<Advisor> advisorKey = this.advisors;

    //缓存Method对象和其对应的拦截器链列表List<Advisor>
    private transient Map<MethodCacheKey, List<Object>> methodCache;

    private transient volatile List<Object> cachedInterceptors;

    transient volatile Object proxyMetadataCache;

    public AdvisedSupport() {
    }

    public AdvisedSupport(Class<?>... interfaces) {
        setInterfaces(interfaces);
    }

    public void setTarget(Object target) {
        setTargetSource(new SingletonTargetSource(target));
    }

    @Override
    public void setTargetSource(TargetSource targetSource) {
        this.targetSource = (targetSource != null ? targetSource : EMPTY_TARGET_SOURCE);
    }

    @Override
    public TargetSource getTargetSource() {
        return this.targetSource;
    }

    public void setTargetClass(Class<?> targetClass) {
        this.targetSource = EmptyTargetSource.forClass(targetClass);
    }

    @Override
    public Class<?> getTargetClass() {
        return this.targetSource.getTargetClass();
    }

    @Override
    public void setPreFiltered(boolean preFiltered) {
        this.preFiltered = preFiltered;
    }

    @Override
    public boolean isPreFiltered() {
        return this.preFiltered;
    }

    public void setAdvisorChainFactory(AdvisorChainFactory advisorChainFactory) {
        Assert.notNull(advisorChainFactory, "AdvisorChainFactory must not be null");
        this.advisorChainFactory = advisorChainFactory;
    }

    public AdvisorChainFactory getAdvisorChainFactory() {
        return this.advisorChainFactory;
    }

    public void setInterfaces(Class<?>... interfaces) {
        Assert.notNull(interfaces, "Interfaces must not be null");
        this.interfaces.clear();
        for (Class<?> ifc : interfaces) {
            addInterface(ifc);
        }
    }

    public void addInterface(Class<?> ifc) {
        Assert.notNull(ifc, "Interface must not be null");
        if (!ifc.isInterface()) {
            throw new IllegalArgumentException("[" + ifc.getName() + "] is not an interface");
        }
        if (!this.interfaces.contains(ifc)) {
            this.interfaces.add(ifc);
            adviceChanged();
        }
    }

    public boolean removeInterface(Class<?> ifc) {
        return this.interfaces.remove(ifc);
    }

    @Override
    public Class<?>[] getProxiedInterfaces() {
        return ClassUtils.toClassArray(this.interfaces);
    }

    @Override
    public boolean isInterfaceProxied(Class<?> ifc) {
        for (Class<?> proxyIntf : this.interfaces) {
            if (ifc.isAssignableFrom(proxyIntf)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public final Advisor[] getAdvisors() {
        return this.advisors.toArray(new Advisor[0]);
    }

    @Override
    public int getAdvisorCount() {
        return this.advisors.size();
    }

    @Override
    public void addAdvisor(Advisor advisor) {
        int pos = this.advisors.size();
        addAdvisor(pos, advisor);
    }

    @Override
    public void addAdvisor(int pos, Advisor advisor) throws AopConfigException {
        if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
            validateIntroductionAdvisor(introductionAdvisor);
        }
        addAdvisorInternal(pos, advisor);
    }

    @Override
    public boolean removeAdvisor(Advisor advisor) {
        int index = indexOf(advisor);
        if (index == -1) {
            return false;
        } else {
            removeAdvisor(index);
            return true;
        }
    }

    @Override
    public void removeAdvisor(int index) throws AopConfigException {
        if (isFrozen()) {
            throw new AopConfigException("Cannot remove Advisor: Configuration is frozen.");
        }
        if (index < 0 || index > this.advisors.size() - 1) {
            throw new AopConfigException("Advisor index " + index + " is out of bounds: " +
                    "This configuration only has " + this.advisors.size() + " advisors.");
        }

        Advisor advisor = this.advisors.remove(index);
        if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
            // We need to remove introduction interfaces.
            for (Class<?> ifc : introductionAdvisor.getInterfaces()) {
                removeInterface(ifc);
            }
        }

        adviceChanged();
    }

    @Override
    public int indexOf(Advisor advisor) {
        Assert.notNull(advisor, "Advisor must not be null");
        return this.advisors.indexOf(advisor);
    }

    @Override
    public boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException {
        Assert.notNull(a, "Advisor a must not be null");
        Assert.notNull(b, "Advisor b must not be null");
        int index = indexOf(a);
        if (index == -1) {
            return false;
        }
        removeAdvisor(index);
        addAdvisor(index, b);
        return true;
    }

    public void addAdvisors(Advisor... advisors) {
        addAdvisors(Arrays.asList(advisors));
    }

    public void addAdvisors(Collection<Advisor> advisors) {
        if (isFrozen()) {
            throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
        }
        if (!CollectionUtils.isEmpty(advisors)) {
            for (Advisor advisor : advisors) {
                if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
                    validateIntroductionAdvisor(introductionAdvisor);
                }
                Assert.notNull(advisor, "Advisor must not be null");
                this.advisors.add(advisor);
            }
            adviceChanged();
        }
    }

    private void validateIntroductionAdvisor(IntroductionAdvisor advisor) {
        advisor.validateInterfaces();
        // If the advisor passed validation, we can make the change.
        for (Class<?> ifc : advisor.getInterfaces()) {
            addInterface(ifc);
        }
    }

    private void addAdvisorInternal(int pos, Advisor advisor) throws AopConfigException {
        Assert.notNull(advisor, "Advisor must not be null");
        if (isFrozen()) {
            throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
        }
        if (pos > this.advisors.size()) {
            throw new IllegalArgumentException(
                    "Illegal position " + pos + " in advisor list with size " + this.advisors.size());
        }
        this.advisors.add(pos, advisor);
        adviceChanged();
    }

    protected final List<Advisor> getAdvisorsInternal() {
        return this.advisors;
    }

    @Override
    public void addAdvice(Advice advice) throws AopConfigException {
        int pos = this.advisors.size();
        addAdvice(pos, advice);
    }

    @Override
    public void addAdvice(int pos, Advice advice) throws AopConfigException {
        Assert.notNull(advice, "Advice must not be null");
        if (advice instanceof IntroductionInfo introductionInfo) {
            // We don't need an IntroductionAdvisor for this kind of introduction:
            // It's fully self-describing.
            addAdvisor(pos, new DefaultIntroductionAdvisor(advice, introductionInfo));
        } else if (advice instanceof DynamicIntroductionAdvice) {
            // We need an IntroductionAdvisor for this kind of introduction.
            throw new AopConfigException("DynamicIntroductionAdvice may only be added as part of IntroductionAdvisor");
        } else {
            addAdvisor(pos, new DefaultPointcutAdvisor(advice));
        }
    }

    @Override
    public boolean removeAdvice(Advice advice) throws AopConfigException {
        int index = indexOf(advice);
        if (index == -1) {
            return false;
        } else {
            removeAdvisor(index);
            return true;
        }
    }

    @Override
    public int indexOf(Advice advice) {
        Assert.notNull(advice, "Advice must not be null");
        for (int i = 0; i < this.advisors.size(); i++) {
            Advisor advisor = this.advisors.get(i);
            if (advisor.getAdvice() == advice) {
                return i;
            }
        }
        return -1;
    }

    public boolean adviceIncluded(Advice advice) {
        if (advice != null) {
            for (Advisor advisor : this.advisors) {
                if (advisor.getAdvice() == advice) {
                    return true;
                }
            }
        }
        return false;
    }

    public int countAdvicesOfType(Class<?> adviceClass) {
        int count = 0;
        if (adviceClass != null) {
            for (Advisor advisor : this.advisors) {
                if (adviceClass.isInstance(advisor.getAdvice())) {
                    count++;
                }
            }
        }
        return count;
    }

    //获取拦截器链, 为提高效率, 同时设置了缓存
    public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, Class<?> targetClass) {
        List<Object> cachedInterceptors;
        if (this.methodCache != null) {
            // Method-specific cache for method-specific pointcuts
            MethodCacheKey cacheKey = new MethodCacheKey(method);
            cachedInterceptors = this.methodCache.get(cacheKey);
            if (cachedInterceptors == null) {
                //缓存中没有,则从AdvisorChainFactory中获取,然后放进缓存
                cachedInterceptors = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(this, method, targetClass);
                this.methodCache.put(cacheKey, cachedInterceptors);
            }
        } else {
            // Shared cache since there are no method-specific advisors (see below).
            cachedInterceptors = this.cachedInterceptors;
            if (cachedInterceptors == null) {
                cachedInterceptors = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(this, method, targetClass);

                this.cachedInterceptors = cachedInterceptors;
            }
        }
        return cachedInterceptors;
    }

    protected void adviceChanged() {
        this.methodCache = null;
        this.cachedInterceptors = null;
        this.proxyMetadataCache = null;

        // Initialize method cache if necessary; otherwise,
        // cachedInterceptors is going to be shared (see above).
        for (Advisor advisor : this.advisors) {
            if (advisor instanceof PointcutAdvisor) {
                this.methodCache = new ConcurrentHashMap<>();
                break;
            }
        }
    }

    protected void copyConfigurationFrom(AdvisedSupport other) {
        copyConfigurationFrom(other, other.targetSource, new ArrayList<>(other.advisors));
    }

    protected void copyConfigurationFrom(AdvisedSupport other, TargetSource targetSource, List<Advisor> advisors) {
        copyFrom(other);
        this.targetSource = targetSource;
        this.advisorChainFactory = other.advisorChainFactory;
        this.interfaces = new ArrayList<>(other.interfaces);
        for (Advisor advisor : advisors) {
            if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
                validateIntroductionAdvisor(introductionAdvisor);
            }
            Assert.notNull(advisor, "Advisor must not be null");
            this.advisors.add(advisor);
        }
        adviceChanged();
    }

    AdvisedSupport getConfigurationOnlyCopy() {
        AdvisedSupport copy = new AdvisedSupport();
        copy.copyFrom(this);
        copy.targetSource = EmptyTargetSource.forClass(getTargetClass(), getTargetSource().isStatic());
        copy.preFiltered = this.preFiltered;
        copy.advisorChainFactory = this.advisorChainFactory;
        copy.interfaces = new ArrayList<>(this.interfaces);
        copy.advisors = new ArrayList<>(this.advisors);
        copy.advisorKey = new ArrayList<>(this.advisors.size());
        for (Advisor advisor : this.advisors) {
            copy.advisorKey.add(new AdvisorKeyEntry(advisor));
        }
        copy.methodCache = this.methodCache;
        copy.cachedInterceptors = this.cachedInterceptors;
        copy.proxyMetadataCache = this.proxyMetadataCache;
        return copy;
    }

    void reduceToAdvisorKey() {
        this.advisors = this.advisorKey;
        this.methodCache = null;
        this.cachedInterceptors = null;
        this.proxyMetadataCache = null;
    }

    Object getAdvisorKey() {
        return this.advisorKey;
    }


    @Override
    public String toProxyConfigString() {
        return toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        sb.append(": ").append(this.interfaces.size()).append(" interfaces ");
        sb.append(ClassUtils.classNamesToString(this.interfaces)).append("; ");
        sb.append(this.advisors.size()).append(" advisors ");
        sb.append(this.advisors).append("; ");
        sb.append("targetSource [").append(this.targetSource).append("]; ");
        sb.append(super.toString());
        return sb.toString();
    }

    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Rely on default serialization; just initialize state after deserialization.
        ois.defaultReadObject();

        // Initialize method cache if necessary.
        adviceChanged();
    }

    private static final class MethodCacheKey implements Comparable<MethodCacheKey> {

        private final Method method;

        private final int hashCode;

        public MethodCacheKey(Method method) {
            this.method = method;
            this.hashCode = method.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return (this == other || (other instanceof MethodCacheKey that && this.method == that.method));
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }

        @Override
        public String toString() {
            return this.method.toString();
        }

        @Override
        public int compareTo(MethodCacheKey other) {
            int result = this.method.getName().compareTo(other.method.getName());
            if (result == 0) {
                result = this.method.toString().compareTo(other.method.toString());
            }
            return result;
        }
    }

    private static final class AdvisorKeyEntry implements Advisor {

        private final Class<?> adviceType;


        private final String classFilterKey;


        private final String methodMatcherKey;

        public AdvisorKeyEntry(Advisor advisor) {
            this.adviceType = advisor.getAdvice().getClass();
            if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
                Pointcut pointcut = pointcutAdvisor.getPointcut();
                this.classFilterKey = pointcut.getClassFilter().toString();
                this.methodMatcherKey = pointcut.getMethodMatcher().toString();
            } else {
                this.classFilterKey = null;
                this.methodMatcherKey = null;
            }
        }

        @Override
        public Advice getAdvice() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object other) {
            return (this == other || (other instanceof AdvisorKeyEntry that &&
                    this.adviceType == that.adviceType &&
                    ObjectUtils.nullSafeEquals(this.classFilterKey, that.classFilterKey) &&
                    ObjectUtils.nullSafeEquals(this.methodMatcherKey, that.methodMatcherKey)));
        }

        @Override
        public int hashCode() {
            return this.adviceType.hashCode();
        }
    }
}