package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.config.ConfigurableBeanFactory;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.StringUtils;

import javax.security.auth.callback.Callback;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author t13max
 * @since 13:37 2026/1/16
 */
public class CglibSubclassingInstantiationStrategy extends SimpleInstantiationStrategy {

    private static final int PASSTHROUGH = 0;

    private static final int LOOKUP_OVERRIDE = 1;

    private static final int METHOD_REPLACER = 2;

    //实例化私有静态内部类, 调用内部类的instantiate()完成实例化
    @Override
    protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
        return instantiateWithMethodInjection(bd, beanName, owner, null);
    }

    @Override
    protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, @Nullable Constructor<?> ctor, Object... args) {
        return new CglibSubclassCreator(bd, owner).instantiate(ctor, args);
    }

    @Override
    public Class<?> getActualBeanClass(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
        if (!bd.hasMethodOverrides()) {
            return super.getActualBeanClass(bd, beanName, owner);
        }
        return new CglibSubclassCreator(bd, owner).createEnhancedSubclass(bd);
    }

    //为避免3.2之前的Spring版本中的外部cglib依赖而创建的内部类
    private static class CglibSubclassCreator {

        private static final Class<?>[] CALLBACK_TYPES = new Class<?>[]{NoOp.class, LookupOverrideMethodInterceptor.class, ReplaceOverrideMethodInterceptor.class};

        private final RootBeanDefinition beanDefinition;

        private final BeanFactory owner;

        CglibSubclassCreator(RootBeanDefinition beanDefinition, BeanFactory owner) {
            this.beanDefinition = beanDefinition;
            this.owner = owner;
        }

        public Object instantiate(@Nullable Constructor<?> ctor, Object... args) {
            Class<?> subclass = createEnhancedSubclass(this.beanDefinition);
            Object instance;
            if (ctor == null) {
                instance = BeanUtils.instantiateClass(subclass);
            }
            else {
                try {
                    Constructor<?> enhancedSubclassConstructor = subclass.getConstructor(ctor.getParameterTypes());
                    instance = enhancedSubclassConstructor.newInstance(args);
                }
                catch (Exception ex) {
                    throw new BeanInstantiationException(this.beanDefinition.getBeanClass(), "Failed to invoke constructor for CGLIB enhanced subclass [" + subclass.getName() + "]", ex);
                }
            }
            // SPR-10785: set callbacks directly on the instance instead of in the
            // enhanced class (via the Enhancer) in order to avoid memory leaks.
            Factory factory = (Factory) instance;
            factory.setCallbacks(new Callback[] {NoOp.INSTANCE,
                    new LookupOverrideMethodInterceptor(this.beanDefinition, this.owner),
                    new ReplaceOverrideMethodInterceptor(this.beanDefinition, this.owner)});
            return instance;
        }

        public Class<?> createEnhancedSubclass(RootBeanDefinition beanDefinition) {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(beanDefinition.getBeanClass());
            enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
            enhancer.setAttemptLoad(AotDetector.useGeneratedArtifacts());
            if (this.owner instanceof ConfigurableBeanFactory cbf) {
                ClassLoader cl = cbf.getBeanClassLoader();
                enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(cl));
            }
            enhancer.setCallbackFilter(new MethodOverrideCallbackFilter(beanDefinition));
            enhancer.setCallbackTypes(CALLBACK_TYPES);
            return enhancer.createClass();
        }
    }

    private static class CglibIdentitySupport {

        private final RootBeanDefinition beanDefinition;

        public CglibIdentitySupport(RootBeanDefinition beanDefinition) {
            this.beanDefinition = beanDefinition;
        }

        public RootBeanDefinition getBeanDefinition() {
            return this.beanDefinition;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return (other != null && getClass() == other.getClass() &&
                    this.beanDefinition.equals(((CglibIdentitySupport) other).beanDefinition));
        }

        @Override
        public int hashCode() {
            return this.beanDefinition.hashCode();
        }
    }

    private static class MethodOverrideCallbackFilter extends CglibIdentitySupport implements CallbackFilter {

        private static final Log logger = LogFactory.getLog(MethodOverrideCallbackFilter.class);

        public MethodOverrideCallbackFilter(RootBeanDefinition beanDefinition) {
            super(beanDefinition);
        }

        @Override
        public int accept(Method method) {
            MethodOverride methodOverride = getBeanDefinition().getMethodOverrides().getOverride(method);
            if (logger.isTraceEnabled()) {
                logger.trace("MethodOverride for " + method + ": " + methodOverride);
            }
            if (methodOverride == null) {
                return PASSTHROUGH;
            }
            else if (methodOverride instanceof LookupOverride) {
                return LOOKUP_OVERRIDE;
            }
            else if (methodOverride instanceof ReplaceOverride) {
                return METHOD_REPLACER;
            }
            throw new UnsupportedOperationException("Unexpected MethodOverride subclass: " +
                    methodOverride.getClass().getName());
        }
    }

    private static class LookupOverrideMethodInterceptor extends CglibIdentitySupport implements MethodInterceptor {

        private final BeanFactory owner;

        public LookupOverrideMethodInterceptor(RootBeanDefinition beanDefinition, BeanFactory owner) {
            super(beanDefinition);
            this.owner = owner;
        }

        @Override
        @Nullable
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
            // Cast is safe, as CallbackFilter filters are used selectively.
            LookupOverride lo = (LookupOverride) getBeanDefinition().getMethodOverrides().getOverride(method);
            Assert.state(lo != null, "LookupOverride not found");
            Object[] argsToUse = (args.length > 0 ? args : null);  // if no-arg, don't insist on args at all
            if (StringUtils.hasText(lo.getBeanName())) {
                Object bean = (argsToUse != null ? this.owner.getBean(lo.getBeanName(), argsToUse) :
                        this.owner.getBean(lo.getBeanName()));
                // Detect package-protected NullBean instance through equals(null) check
                return (bean.equals(null) ? null : bean);
            }
            else {
                // Find target bean matching the (potentially generic) method return type
                ResolvableType genericReturnType = ResolvableType.forMethodReturnType(method);
                return (argsToUse != null ? this.owner.getBeanProvider(genericReturnType).getObject(argsToUse) :
                        this.owner.getBeanProvider(genericReturnType).getObject());
            }
        }
    }

    private static class ReplaceOverrideMethodInterceptor extends CglibIdentitySupport implements MethodInterceptor {

        private final BeanFactory owner;

        public ReplaceOverrideMethodInterceptor(RootBeanDefinition beanDefinition, BeanFactory owner) {
            super(beanDefinition);
            this.owner = owner;
        }

        @Override
        @Nullable
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
            ReplaceOverride ro = (ReplaceOverride) getBeanDefinition().getMethodOverrides().getOverride(method);
            Assert.state(ro != null, "ReplaceOverride not found");
            MethodReplacer mr = this.owner.getBean(ro.getMethodReplacerBeanName(), MethodReplacer.class);
            return mr.reimplement(obj, method, args);
        }
    }
}
