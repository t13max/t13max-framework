package com.t13max.ioc.context;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.config.BeanFactoryPostProcessor;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.core.env.ConfigurableEnvironment;
import com.t13max.ioc.core.metrics.ApplicationStartup;

import java.io.Closeable;

/**
 * 可配置应用上下文接口
 *
 * @Author: t13max
 * @Since: 23:19 2026/1/14
 */
public interface ConfigurableApplicationContext extends ApplicationContext, Lifecycle, Closeable {

    String CONFIG_LOCATION_DELIMITERS = ",; \t\n";

    String BOOTSTRAP_EXECUTOR_BEAN_NAME = "bootstrapExecutor";

    String CONVERSION_SERVICE_BEAN_NAME = "conversionService";

    String LOAD_TIME_WEAVER_BEAN_NAME = "loadTimeWeaver";

    String ENVIRONMENT_BEAN_NAME = "environment";

    String SYSTEM_PROPERTIES_BEAN_NAME = "systemProperties";

    String SYSTEM_ENVIRONMENT_BEAN_NAME = "systemEnvironment";

    String APPLICATION_STARTUP_BEAN_NAME = "applicationStartup";

    String SHUTDOWN_HOOK_THREAD_NAME = "SpringContextShutdownHook";

    void setId(String id);

    void setParent(ApplicationContext parent);

    void setEnvironment(ConfigurableEnvironment environment);

    @Override
    ConfigurableEnvironment getEnvironment();

    void setApplicationStartup(ApplicationStartup applicationStartup);

    ApplicationStartup getApplicationStartup();

    void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor);

    void addApplicationListener(ApplicationListener<?> listener);

    void removeApplicationListener(ApplicationListener<?> listener);

    void setClassLoader(ClassLoader classLoader);

    //刷新
    void refresh() throws BeansException, IllegalStateException;

    void registerShutdownHook();

    @Override
    void close();

    boolean isClosed();

    boolean isActive();

    ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;

}
