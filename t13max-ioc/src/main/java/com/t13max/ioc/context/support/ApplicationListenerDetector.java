package com.t13max.ioc.context.support;

import com.t13max.ioc.beans.factory.config.DestructionAwareBeanPostProcessor;
import com.t13max.ioc.beans.factory.support.MergedBeanDefinitionPostProcessor;
import com.t13max.ioc.beans.factory.support.RootBeanDefinition;
import com.t13max.ioc.context.ApplicationListener;
import com.t13max.ioc.context.event.ApplicationEventMulticaster;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: t13max
 * @Since: 21:40 2026/1/16
 */
public class ApplicationListenerDetector implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor {

    private static final Logger logger = LogManager.getLogger(ApplicationListenerDetector.class);

    private final transient AbstractApplicationContext applicationContext;

    private final transient Map<String, Boolean> singletonNames = new ConcurrentHashMap<>(256);

    public ApplicationListenerDetector(AbstractApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (ApplicationListener.class.isAssignableFrom(beanType)) {
            this.singletonNames.put(beanName, beanDefinition.isSingleton());
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ApplicationListener<?> applicationListener) {
            // potentially not detected as a listener by getBeanNamesForType retrieval
            Boolean flag = this.singletonNames.get(beanName);
            if (Boolean.TRUE.equals(flag)) {
                // singleton bean (top-level or inner): register on the fly
                this.applicationContext.addApplicationListener(applicationListener);
            }
            else if (Boolean.FALSE.equals(flag)) {
                if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
                    // inner bean with other scope - can't reliably process events
                    logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
                            "but is not reachable for event multicasting by its containing ApplicationContext " +
                            "because it does not have singleton scope. Only top-level listener beans are allowed " +
                            "to be of non-singleton scope.");
                }
                this.singletonNames.remove(beanName);
            }
        }
        return bean;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) {
        if (bean instanceof ApplicationListener<?> applicationListener) {
            try {
                ApplicationEventMulticaster multicaster = this.applicationContext.getApplicationEventMulticaster();
                multicaster.removeApplicationListener(applicationListener);
                multicaster.removeApplicationListenerBean(beanName);
            }
            catch (IllegalStateException ex) {
                // ApplicationEventMulticaster not initialized yet - no need to remove a listener
            }
        }
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        return (bean instanceof ApplicationListener);
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof ApplicationListenerDetector that &&
                this.applicationContext == that.applicationContext));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.applicationContext);
    }
}
