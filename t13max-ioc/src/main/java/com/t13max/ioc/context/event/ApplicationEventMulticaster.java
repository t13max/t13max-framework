package com.t13max.ioc.context.event;

import com.t13max.ioc.context.ApplicationEvent;
import com.t13max.ioc.context.ApplicationListener;
import com.t13max.ioc.core.ResolvableType;

import java.util.function.Predicate;

/**
 * @Author: t13max
 * @Since: 21:40 2026/1/15
 */
public interface ApplicationEventMulticaster {

    void addApplicationListener(ApplicationListener<?> listener);

    void addApplicationListenerBean(String listenerBeanName);

    void removeApplicationListener(ApplicationListener<?> listener);

    void removeApplicationListenerBean(String listenerBeanName);

    void removeApplicationListeners(Predicate<ApplicationListener<?>> predicate);

    void removeApplicationListenerBeans(Predicate<String> predicate);

    void removeAllListeners();

    void multicastEvent(ApplicationEvent event);

    void multicastEvent(ApplicationEvent event, ResolvableType eventType);
}
