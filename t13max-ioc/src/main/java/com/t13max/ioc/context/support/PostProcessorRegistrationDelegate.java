package com.t13max.ioc.context.support;

import com.t13max.ioc.beans.PropertyValue;
import com.t13max.ioc.beans.factory.config.*;
import com.t13max.ioc.beans.factory.support.*;
import com.t13max.ioc.core.OrderComparator;
import com.t13max.ioc.core.Ordered;
import com.t13max.ioc.core.PriorityOrdered;
import com.t13max.ioc.core.metrics.ApplicationStartup;
import com.t13max.ioc.core.metrics.StartupStep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * @author t13max
 * @since 14:19 2026/1/16
 */
public class PostProcessorRegistrationDelegate {

    private PostProcessorRegistrationDelegate() {
    }


    public static void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

        Set<String> processedBeans = new HashSet<>();

        //由于我们的beanFactory是DefaultListableBeanFactory实例是BeanDefinitionRegistry的子类所以可以进来
        if (beanFactory instanceof BeanDefinitionRegistry registry) {
            List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
            List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

            for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
                if (postProcessor instanceof BeanDefinitionRegistryPostProcessor registryProcessor) {
                    registryProcessor.postProcessBeanDefinitionRegistry(registry);
                    registryProcessors.add(registryProcessor);
                } else {
                    regularPostProcessors.add(postProcessor);
                }
            }

            // Do not initialize FactoryBeans here: We need to leave all regular beans
            // uninitialized to let the bean factory post-processors apply to them!
            // Separate between BeanDefinitionRegistryPostProcessors that implement
            // PriorityOrdered, Ordered, and the rest.
            List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

            // First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
            String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                }
            }
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            registryProcessors.addAll(currentRegistryProcessors);
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
            currentRegistryProcessors.clear();

            // Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                }
            }
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            registryProcessors.addAll(currentRegistryProcessors);
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
            currentRegistryProcessors.clear();

            // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
            boolean reiterate = true;
            while (reiterate) {
                reiterate = false;
                postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
                for (String ppName : postProcessorNames) {
                    if (!processedBeans.contains(ppName)) {
                        currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                        processedBeans.add(ppName);
                        reiterate = true;
                    }
                }
                sortPostProcessors(currentRegistryProcessors, beanFactory);
                registryProcessors.addAll(currentRegistryProcessors);
                invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
                currentRegistryProcessors.clear();
            }

            // Now, invoke the postProcessBeanFactory callback of all processors handled so far.
            invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
            invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
        } else {
            // Invoke factory processors registered with the context instance.
            invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
        }

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let the bean factory post-processors apply to them!
        String[] postProcessorNames =
                beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

        // Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        List<String> orderedPostProcessorNames = new ArrayList<>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        for (String ppName : postProcessorNames) {
            if (processedBeans.contains(ppName)) {
                // skip - already processed in first phase above
            } else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

        // Next, invoke the BeanFactoryPostProcessors that implement Ordered.
        List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
        for (String postProcessorName : orderedPostProcessorNames) {
            orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

        // Finally, invoke all other BeanFactoryPostProcessors.
        List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
        for (String postProcessorName : nonOrderedPostProcessorNames) {
            nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

        // Clear cached merged bean definitions since the post-processors might have
        // modified the original metadata, e.g. replacing placeholders in values...
        beanFactory.clearMetadataCache();
    }

    public static void registerBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

        // WARNING: Although it may appear that the body of this method can be easily
        // refactored to avoid the use of multiple loops and multiple lists, the use
        // of multiple lists and multiple passes over the names of processors is
        // intentional. We must ensure that we honor the contracts for PriorityOrdered
        // and Ordered processors. Specifically, we must NOT cause processors to be
        // instantiated (via getBean() invocations) or registered in the ApplicationContext
        // in the wrong order.
        //
        // Before submitting a pull request (PR) to change this method, please review the
        // list of all declined PRs involving changes to PostProcessorRegistrationDelegate
        // to ensure that your proposal does not result in a breaking change:
        // https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

        String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

        // Register BeanPostProcessorChecker that logs a warn message when
        // a bean is created during BeanPostProcessor instantiation, i.e. when
        // a bean is not eligible for getting processed by all BeanPostProcessors.
        int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
        beanFactory.addBeanPostProcessor(
                new BeanPostProcessorChecker(beanFactory, postProcessorNames, beanProcessorTargetCount));

        // Separate between BeanPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
        List<String> orderedPostProcessorNames = new ArrayList<>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        for (String ppName : postProcessorNames) {
            if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
                priorityOrderedPostProcessors.add(pp);
                if (pp instanceof MergedBeanDefinitionPostProcessor) {
                    internalPostProcessors.add(pp);
                }
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // First, register the BeanPostProcessors that implement PriorityOrdered.
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

        // Next, register the BeanPostProcessors that implement Ordered.
        List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
        for (String ppName : orderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            orderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, orderedPostProcessors);

        // Now, register all regular BeanPostProcessors.
        List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
        for (String ppName : nonOrderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            nonOrderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

        // Finally, re-register all internal BeanPostProcessors.
        sortPostProcessors(internalPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, internalPostProcessors);

        // Re-register post-processor for detecting inner beans as ApplicationListeners,
        // moving it to the end of the processor chain (for picking up proxies etc).
        beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
    }

    static <T extends BeanPostProcessor> List<T> loadBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, Class<T> beanPostProcessorType) {

        String[] postProcessorNames = beanFactory.getBeanNamesForType(beanPostProcessorType, true, false);
        List<T> postProcessors = new ArrayList<>();
        for (String ppName : postProcessorNames) {
            postProcessors.add(beanFactory.getBean(ppName, beanPostProcessorType));
        }
        sortPostProcessors(postProcessors, beanFactory);
        return postProcessors;

    }

    static void invokeMergedBeanDefinitionPostProcessors(DefaultListableBeanFactory beanFactory) {
        new MergedBeanDefinitionPostProcessorInvoker(beanFactory).invokeMergedBeanDefinitionPostProcessors();
    }

    private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
        // Nothing to sort?
        if (postProcessors.size() <= 1) {
            return;
        }
        Comparator<Object> comparatorToUse = null;
        if (beanFactory instanceof DefaultListableBeanFactory dlbf) {
            comparatorToUse = dlbf.getDependencyComparator();
        }
        if (comparatorToUse == null) {
            comparatorToUse = OrderComparator.INSTANCE;
        }
        postProcessors.sort(comparatorToUse);
    }

    private static void invokeBeanDefinitionRegistryPostProcessors(
            Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

        for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
            StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
                    .tag("postProcessor", postProcessor::toString);
            postProcessor.postProcessBeanDefinitionRegistry(registry);
            postProcessBeanDefRegistry.end();
        }
    }

    private static void invokeBeanFactoryPostProcessors(Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

        for (BeanFactoryPostProcessor postProcessor : postProcessors) {
            StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process")
                    .tag("postProcessor", postProcessor::toString);
            postProcessor.postProcessBeanFactory(beanFactory);
            postProcessBeanFactory.end();
        }
    }

    private static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, List<? extends BeanPostProcessor> postProcessors) {

        if (beanFactory instanceof AbstractBeanFactory abstractBeanFactory) {
            // Bulk addition is more efficient against our CopyOnWriteArrayList there
            abstractBeanFactory.addBeanPostProcessors(postProcessors);
        } else {
            for (BeanPostProcessor postProcessor : postProcessors) {
                beanFactory.addBeanPostProcessor(postProcessor);
            }
        }
    }

    private static final class BeanPostProcessorChecker implements BeanPostProcessor {

        private static final Logger logger = LogManager.getLogger(BeanPostProcessorChecker.class);

        private final ConfigurableListableBeanFactory beanFactory;

        private final String[] postProcessorNames;

        private final int beanPostProcessorTargetCount;

        public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory,
                                        String[] postProcessorNames, int beanPostProcessorTargetCount) {

            this.beanFactory = beanFactory;
            this.postProcessorNames = postProcessorNames;
            this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
                    this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
                if (logger.isWarnEnabled()) {
                    Set<String> bppsInCreation = new LinkedHashSet<>(2);
                    for (String bppName : this.postProcessorNames) {
                        if (this.beanFactory.isCurrentlyInCreation(bppName)) {
                            bppsInCreation.add(bppName);
                        }
                    }
                    if (bppsInCreation.size() == 1) {
                        String bppName = bppsInCreation.iterator().next();
                        if (this.beanFactory.containsBeanDefinition(bppName) &&
                                beanName.equals(this.beanFactory.getBeanDefinition(bppName).getFactoryBeanName())) {
                            logger.warn("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
                                    "] is not eligible for getting processed by all BeanPostProcessors " +
                                    "(for example: not eligible for auto-proxying). The currently created " +
                                    "BeanPostProcessor " + bppsInCreation + " is declared through a non-static " +
                                    "factory method on that class; consider declaring it as static instead.");
                            return bean;
                        }
                    }
                    logger.warn("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
                            "] is not eligible for getting processed by all BeanPostProcessors " +
                            "(for example: not eligible for auto-proxying). Is this bean getting eagerly " +
                            "injected/applied to a currently created BeanPostProcessor " + bppsInCreation + "? " +
                            "Check the corresponding BeanPostProcessor declaration and its dependencies/advisors. " +
                            "If this bean does not have to be post-processed, declare it with ROLE_INFRASTRUCTURE.");
                }
            }
            return bean;
        }

        private boolean isInfrastructureBean(String beanName) {
            if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
                return (bd.getRole() == BeanDefinition.ROLE_INFRASTRUCTURE);
            }
            return false;
        }
    }


    private static final class MergedBeanDefinitionPostProcessorInvoker {

        private final DefaultListableBeanFactory beanFactory;

        private MergedBeanDefinitionPostProcessorInvoker(DefaultListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        private void invokeMergedBeanDefinitionPostProcessors() {
            List<MergedBeanDefinitionPostProcessor> postProcessors = PostProcessorRegistrationDelegate.loadBeanPostProcessors(
                    this.beanFactory, MergedBeanDefinitionPostProcessor.class);
            for (String beanName : this.beanFactory.getBeanDefinitionNames()) {
                RootBeanDefinition bd = (RootBeanDefinition) this.beanFactory.getMergedBeanDefinition(beanName);
                Class<?> beanType = resolveBeanType(bd);
                postProcessRootBeanDefinition(postProcessors, beanName, beanType, bd);
                bd.markAsPostProcessed();
            }
            registerBeanPostProcessors(this.beanFactory, postProcessors);
        }

        private void postProcessRootBeanDefinition(List<MergedBeanDefinitionPostProcessor> postProcessors,
                                                   String beanName, Class<?> beanType, RootBeanDefinition bd) {

            BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this.beanFactory, beanName, bd);
            postProcessors.forEach(postProcessor -> postProcessor.postProcessMergedBeanDefinition(bd, beanType, beanName));
            for (PropertyValue propertyValue : bd.getPropertyValues().getPropertyValueList()) {
                postProcessValue(postProcessors, valueResolver, propertyValue.getValue());
            }
            for (ConstructorArgumentValues.ValueHolder valueHolder : bd.getConstructorArgumentValues().getIndexedArgumentValues().values()) {
                postProcessValue(postProcessors, valueResolver, valueHolder.getValue());
            }
            for (ConstructorArgumentValues.ValueHolder valueHolder : bd.getConstructorArgumentValues().getGenericArgumentValues()) {
                postProcessValue(postProcessors, valueResolver, valueHolder.getValue());
            }
        }

        private void postProcessValue(List<MergedBeanDefinitionPostProcessor> postProcessors,
                                      BeanDefinitionValueResolver valueResolver, Object value) {
            if (value instanceof BeanDefinitionHolder bdh
                    && bdh.getBeanDefinition() instanceof AbstractBeanDefinition innerBd) {

                Class<?> innerBeanType = resolveBeanType(innerBd);
                resolveInnerBeanDefinition(valueResolver, innerBd, (innerBeanName, innerBeanDefinition)
                        -> postProcessRootBeanDefinition(postProcessors, innerBeanName, innerBeanType, innerBeanDefinition));
            } else if (value instanceof AbstractBeanDefinition innerBd) {
                Class<?> innerBeanType = resolveBeanType(innerBd);
                resolveInnerBeanDefinition(valueResolver, innerBd, (innerBeanName, innerBeanDefinition)
                        -> postProcessRootBeanDefinition(postProcessors, innerBeanName, innerBeanType, innerBeanDefinition));
            } else if (value instanceof TypedStringValue typedStringValue) {
                resolveTypeStringValue(typedStringValue);
            }
        }

        private void resolveInnerBeanDefinition(BeanDefinitionValueResolver valueResolver, BeanDefinition innerBeanDefinition,
                                                BiConsumer<String, RootBeanDefinition> resolver) {

            valueResolver.resolveInnerBean(null, innerBeanDefinition, (name, rbd) -> {
                resolver.accept(name, rbd);
                return Void.class;
            });
        }

        private void resolveTypeStringValue(TypedStringValue typedStringValue) {
            try {
                typedStringValue.resolveTargetType(this.beanFactory.getBeanClassLoader());
            } catch (ClassNotFoundException ex) {
                // ignore
            }
        }

        private Class<?> resolveBeanType(AbstractBeanDefinition bd) {
            if (!bd.hasBeanClass()) {
                try {
                    bd.resolveBeanClass(this.beanFactory.getBeanClassLoader());
                } catch (ClassNotFoundException ex) {
                    // ignore
                }
            }
            return bd.getResolvableType().toClass();
        }
    }
}
