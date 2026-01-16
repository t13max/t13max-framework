package com.t13max.ioc.context.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.Aware;
import com.t13max.ioc.beans.factory.config.BeanPostProcessor;
import com.t13max.ioc.context.ConfigurableApplicationContext;
import com.t13max.ioc.context.EmbeddedValueResolverAware;
import com.t13max.ioc.context.ResourceLoaderAware;
import com.t13max.ioc.utils.StringValueResolver;

/**
 * @Author: t13max
 * @Since: 21:37 2026/1/16
 */
public class ApplicationContextAwareProcessor implements BeanPostProcessor {

    private final ConfigurableApplicationContext applicationContext;

    private final StringValueResolver embeddedValueResolver;
    
    public ApplicationContextAwareProcessor(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.embeddedValueResolver = new EmbeddedValueResolver(applicationContext.getBeanFactory());
    }


    @Override
    public  Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Aware) {
            invokeAwareInterfaces(bean);
        }
        return bean;
    }

    private void invokeAwareInterfaces(Object bean) {
        if (bean instanceof EnvironmentAware environmentAware) {
            environmentAware.setEnvironment(this.applicationContext.getEnvironment());
        }
        if (bean instanceof EmbeddedValueResolverAware embeddedValueResolverAware) {
            embeddedValueResolverAware.setEmbeddedValueResolver(this.embeddedValueResolver);
        }
        if (bean instanceof ResourceLoaderAware resourceLoaderAware) {
            resourceLoaderAware.setResourceLoader(this.applicationContext);
        }
        if (bean instanceof ApplicationEventPublisherAware applicationEventPublisherAware) {
            applicationEventPublisherAware.setApplicationEventPublisher(this.applicationContext);
        }
        if (bean instanceof MessageSourceAware messageSourceAware) {
            messageSourceAware.setMessageSource(this.applicationContext);
        }
        if (bean instanceof ApplicationStartupAware applicationStartupAware) {
            applicationStartupAware.setApplicationStartup(this.applicationContext.getApplicationStartup());
        }
        if (bean instanceof ApplicationContextAware applicationContextAware) {
            applicationContextAware.setApplicationContext(this.applicationContext);
        }
    }
}
