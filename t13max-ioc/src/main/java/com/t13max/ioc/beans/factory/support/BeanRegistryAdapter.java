package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanRegistrar;
import com.t13max.ioc.beans.factory.BeanRegistry;
import com.t13max.ioc.beans.factory.ListableBeanFactory;
import com.t13max.ioc.beans.factory.ObjectProvider;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.BeanDefinitionCustomizer;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.core.env.Environment;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.MultiValueMap;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @Author: t13max
 * @Since: 20:57 2026/1/16
 */
public class BeanRegistryAdapter implements BeanRegistry {

    private final BeanDefinitionRegistry beanRegistry;

    private final ListableBeanFactory beanFactory;

    private final Environment environment;

    private final Class<? extends BeanRegistrar> beanRegistrarClass;

    private final MultiValueMap<String, BeanDefinitionCustomizer> customizers;

    public BeanRegistryAdapter(DefaultListableBeanFactory beanFactory, Environment environment, Class<? extends BeanRegistrar> beanRegistrarClass) {
        this(beanFactory, beanFactory, environment, beanRegistrarClass, null);
    }

    public BeanRegistryAdapter(BeanDefinitionRegistry beanRegistry, ListableBeanFactory beanFactory, Environment environment, Class<? extends BeanRegistrar> beanRegistrarClass) {
        this(beanRegistry, beanFactory, environment, beanRegistrarClass, null);
    }

    public BeanRegistryAdapter(BeanDefinitionRegistry beanRegistry, ListableBeanFactory beanFactory, Environment environment, Class<? extends BeanRegistrar> beanRegistrarClass, MultiValueMap<String, BeanDefinitionCustomizer> customizers) {
        this.beanRegistry = beanRegistry;
        this.beanFactory = beanFactory;
        this.environment = environment;
        this.beanRegistrarClass = beanRegistrarClass;
        this.customizers = customizers;
    }


    @Override
    public void registerAlias(String name, String alias) {
        this.beanRegistry.registerAlias(name, alias);
    }

    @Override
    public <T> String registerBean(Class<T> beanClass) {
        String beanName = BeanDefinitionReaderUtils.uniqueBeanName(beanClass.getName(), this.beanRegistry);
        registerBean(beanName, beanClass);
        return beanName;
    }

    @Override
    public <T> String registerBean(Class<T> beanClass, Consumer<Spec<T>> customizer) {
        String beanName = BeanDefinitionReaderUtils.uniqueBeanName(beanClass.getName(), this.beanRegistry);
        registerBean(beanName, beanClass, customizer);
        return beanName;
    }

    @Override
    public <T> void registerBean(String name, Class<T> beanClass) {
        BeanRegistrarBeanDefinition beanDefinition = new BeanRegistrarBeanDefinition(beanClass, this.beanRegistrarClass);
        if (this.customizers != null && this.customizers.containsKey(name)) {
            for (BeanDefinitionCustomizer customizer : this.customizers.get(name)) {
                customizer.customize(beanDefinition);
            }
        }
        this.beanRegistry.registerBeanDefinition(name, beanDefinition);
    }

    @Override
    public <T> void registerBean(String name, Class<T> beanClass, Consumer<Spec<T>> spec) {
        BeanRegistrarBeanDefinition beanDefinition = new BeanRegistrarBeanDefinition(beanClass, this.beanRegistrarClass);
        spec.accept(new BeanSpecAdapter<>(beanDefinition, this.beanFactory));
        if (this.customizers != null && this.customizers.containsKey(name)) {
            for (BeanDefinitionCustomizer customizer : this.customizers.get(name)) {
                customizer.customize(beanDefinition);
            }
        }
        this.beanRegistry.registerBeanDefinition(name, beanDefinition);
    }

    @Override
    public void register(BeanRegistrar registrar) {
        Assert.notNull(registrar, "'registrar' must not be null");
        registrar.register(this, this.environment);
    }

    @SuppressWarnings("serial")
    private static class BeanRegistrarBeanDefinition extends RootBeanDefinition {

        public BeanRegistrarBeanDefinition(Class<?> beanClass, Class<? extends BeanRegistrar> beanRegistrarClass) {
            super(beanClass);
            this.setSource(beanRegistrarClass);
            this.setAttribute("aotProcessingIgnoreRegistration", true);
        }

        public BeanRegistrarBeanDefinition(BeanRegistrarBeanDefinition original) {
            super(original);
        }

        @Override
        public Constructor<?>[] getPreferredConstructors() {
            if (this.getInstanceSupplier() != null) {
                return null;
            }
            try {
                return new Constructor<?>[]{BeanUtils.getResolvableConstructor(getBeanClass())};
            } catch (IllegalStateException ex) {
                return null;
            }
        }

        @Override
        public RootBeanDefinition cloneBeanDefinition() {
            return new BeanRegistrarBeanDefinition(this);
        }
    }

    private static class BeanSpecAdapter<T> implements Spec<T> {

        private final RootBeanDefinition beanDefinition;

        private final ListableBeanFactory beanFactory;

        public BeanSpecAdapter(RootBeanDefinition beanDefinition, ListableBeanFactory beanFactory) {
            this.beanDefinition = beanDefinition;
            this.beanFactory = beanFactory;
        }

        @Override
        public Spec<T> backgroundInit() {
            this.beanDefinition.setBackgroundInit(true);
            return this;
        }

        @Override
        public Spec<T> fallback() {
            this.beanDefinition.setFallback(true);
            return this;
        }

        @Override
        public Spec<T> primary() {
            this.beanDefinition.setPrimary(true);
            return this;
        }

        @Override
        public Spec<T> description(String description) {
            this.beanDefinition.setDescription(description);
            return this;
        }

        @Override
        public Spec<T> infrastructure() {
            this.beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            return this;
        }

        @Override
        public Spec<T> lazyInit() {
            this.beanDefinition.setLazyInit(true);
            return this;
        }

        @Override
        public Spec<T> notAutowirable() {
            this.beanDefinition.setAutowireCandidate(false);
            return this;
        }

        @Override
        public Spec<T> order(int order) {
            this.beanDefinition.setAttribute(AbstractBeanDefinition.ORDER_ATTRIBUTE, order);
            return this;
        }

        @Override
        public Spec<T> prototype() {
            this.beanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            return this;
        }

        @Override
        public Spec<T> supplier(Function<SupplierContext, T> supplier) {
            this.beanDefinition.setInstanceSupplier(() ->
                    supplier.apply(new SupplierContextAdapter(this.beanFactory)));
            return this;
        }

        @Override
        public Spec<T> targetType(ParameterizedTypeReference<? extends T> targetType) {
            this.beanDefinition.setTargetType(ResolvableType.forType(targetType));
            return this;
        }

        @Override
        public Spec<T> targetType(ResolvableType targetType) {
            this.beanDefinition.setTargetType(targetType);
            return this;
        }
    }


    private static class SupplierContextAdapter implements SupplierContext {

        private final ListableBeanFactory beanFactory;

        public SupplierContextAdapter(ListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public <T> T bean(Class<T> requiredType) throws BeansException {
            return this.beanFactory.getBean(requiredType);
        }

        @Override
        public <T> T bean(String name, Class<T> requiredType) throws BeansException {
            return this.beanFactory.getBean(name, requiredType);
        }

        @Override
        public <T> ObjectProvider<T> beanProvider(Class<T> requiredType) {
            return this.beanFactory.getBeanProvider(requiredType);
        }

        @Override
        public <T> ObjectProvider<T> beanProvider(ResolvableType requiredType) {
            return this.beanFactory.getBeanProvider(requiredType);
        }
    }
}
