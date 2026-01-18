package com.t13max.ioc.context.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import com.t13max.ioc.aop.framework.autoproxy.AutoProxyUtils;
import com.t13max.ioc.aop.scope.ScopedObject;
import com.t13max.ioc.aop.scope.ScopedProxyUtils;
import com.t13max.ioc.aop.support.AopUtils;
import com.t13max.ioc.beans.factory.BeanInitializationException;
import com.t13max.ioc.beans.factory.SmartInitializingSingleton;
import com.t13max.ioc.beans.factory.config.BeanFactoryPostProcessor;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.context.ApplicationContext;
import com.t13max.ioc.context.ApplicationContextAware;
import com.t13max.ioc.context.ApplicationListener;
import com.t13max.ioc.context.ConfigurableApplicationContext;
import com.t13max.ioc.context.expression.BeanFactoryResolver;
import com.t13max.ioc.core.MethodIntrospector;
import com.t13max.ioc.core.annotation.AnnotatedElementUtils;
import com.t13max.ioc.core.annotation.AnnotationAwareOrderComparator;
import com.t13max.ioc.core.annotation.AnnotationUtils;
import com.t13max.ioc.expression.spel.support.StandardEvaluationContext;
import com.t13max.ioc.stereotype.Component;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.CollectionUtils;

public class EventListenerMethodProcessor
		implements SmartInitializingSingleton, ApplicationContextAware, BeanFactoryPostProcessor {

	protected final Logger logger = LogManager.getLogger(getClass());

	private  ConfigurableApplicationContext applicationContext;

	private  ConfigurableListableBeanFactory beanFactory;

	private  List<EventListenerFactory> eventListenerFactories;

	private final StandardEvaluationContext originalEvaluationContext;

	private final  EventExpressionEvaluator evaluator;

	private final Set<Class<?>> nonAnnotatedClasses = ConcurrentHashMap.newKeySet(64);


	public EventListenerMethodProcessor() {
		this.originalEvaluationContext = new StandardEvaluationContext();
		this.evaluator = new EventExpressionEvaluator(this.originalEvaluationContext);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
				"ApplicationContext does not implement ConfigurableApplicationContext");
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.originalEvaluationContext.setBeanResolver(new BeanFactoryResolver(this.beanFactory));

		Map<String, EventListenerFactory> beans = beanFactory.getBeansOfType(EventListenerFactory.class, false, false);
		List<EventListenerFactory> factories = new ArrayList<>(beans.values());
		AnnotationAwareOrderComparator.sort(factories);
		this.eventListenerFactories = factories;
	}


	@Override
	public void afterSingletonsInstantiated() {
		ConfigurableListableBeanFactory beanFactory = this.beanFactory;
		Assert.state(beanFactory != null, "No ConfigurableListableBeanFactory set");
		String[] beanNames = beanFactory.getBeanNamesForType(Object.class);
		for (String beanName : beanNames) {
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
				Class<?> type = null;
				try {
					type = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
				}
				catch (Throwable ex) {
					// An unresolvable bean type, probably from a lazy bean - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
					}
				}
				if (type != null) {
					if (ScopedObject.class.isAssignableFrom(type)) {
						try {
							Class<?> targetClass = AutoProxyUtils.determineTargetClass(
									beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
							if (targetClass != null) {
								type = targetClass;
							}
						}
						catch (Throwable ex) {
							// An invalid scoped proxy arrangement - let's ignore it.
							if (logger.isDebugEnabled()) {
								logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
							}
						}
					}
					try {
						processBean(beanName, type);
					}
					catch (Throwable ex) {
						throw new BeanInitializationException("Failed to process @EventListener " +
								"annotation on bean with name '" + beanName + "': " + ex.getMessage(), ex);
					}
				}
			}
		}
	}

	private void processBean(final String beanName, final Class<?> targetType) {
		if (!this.nonAnnotatedClasses.contains(targetType) &&
				AnnotationUtils.isCandidateClass(targetType, EventListener.class) &&
				!isSpringContainerClass(targetType)) {

			Map<Method, EventListener> annotatedMethods = null;
			try {
				annotatedMethods = MethodIntrospector.selectMethods(targetType,
						(MethodIntrospector.MetadataLookup<EventListener>) method ->
								AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class));
			}
			catch (Throwable ex) {
				// An unresolvable type in a method signature, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
				}
			}

			if (CollectionUtils.isEmpty(annotatedMethods)) {
				this.nonAnnotatedClasses.add(targetType);
				if (logger.isTraceEnabled()) {
					logger.trace("No @EventListener annotations found on bean class: " + targetType.getName());
				}
			}
			else {
				// Non-empty set of methods
				ConfigurableApplicationContext context = this.applicationContext;
				Assert.state(context != null, "No ApplicationContext set");
				List<EventListenerFactory> factories = this.eventListenerFactories;
				Assert.state(factories != null, "EventListenerFactory List not initialized");
				for (Method method : annotatedMethods.keySet()) {
					for (EventListenerFactory factory : factories) {
						if (factory.supportsMethod(method)) {
							Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
							ApplicationListener<?> applicationListener =
									factory.createApplicationListener(beanName, targetType, methodToUse);
							if (applicationListener instanceof ApplicationListenerMethodAdapter alma) {
								alma.init(context, this.evaluator);
							}
							context.addApplicationListener(applicationListener);
							break;
						}
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @EventListener methods processed on bean '" +
							beanName + "': " + annotatedMethods);
				}
			}
		}
	}

	
	private static boolean isSpringContainerClass(Class<?> clazz) {
		return (clazz.getName().startsWith("org.springframework.") &&
				!AnnotatedElementUtils.isAnnotated(ClassUtils.getUserClass(clazz), Component.class));
	}

}
