package com.t13max.ioc.context;

import com.t13max.ioc.beans.factory.HierarchicalBeanFactory;
import com.t13max.ioc.beans.factory.ListableBeanFactory;
import com.t13max.ioc.beans.factory.config.AutowireCapableBeanFactory;
import com.t13max.ioc.core.env.EnvironmentCapable;
import com.t13max.ioc.core.io.support.ResourcePatternResolver;

/**
 * 应用上下文接口
 *
 * @Author: t13max
 * @Since: 21:43 2026/1/14
 */
public interface ApplicationContext extends EnvironmentCapable, ListableBeanFactory, HierarchicalBeanFactory, MessageSource, ApplicationEventPublisher, ResourcePatternResolver {

    String getId();

    String getApplicationName();

    String getDisplayName();

    long getStartupDate();

    ApplicationContext getParent();

    AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException;
}
