package com.t13max.ioc.context.support;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.config.ConfigurableListableBeanFactory;
import com.t13max.ioc.context.ConfigurableApplicationContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 应用上下文抽象父类
 *
 * @Author: t13max
 * @Since: 22:49 2026/1/14
 */
public abstract class AbstractApplicationContext implements ConfigurableApplicationContext {

    private final Logger logger = LogManager.getLogger(getClass());

    private final Lock startupShutdownLock = new ReentrantLock();

    private volatile Thread startupShutdownThread;

    @Override
    public void refresh() throws BeansException, IllegalStateException {

        this.startupShutdownLock.lock();

        try {
            this.startupShutdownThread = Thread.currentThread();

            //StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");

            // 调用容器准备刷新, 获取容器的当前时间, 同时给容器设置同步标识
            prepareRefresh();

            // 告诉子类启动refreshBeanFactory()方法, BeanDefinition资源文件的载入从子类的refreshBeanFactory()方法启动开始
            ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

            // 为BeanFactory配置容器特性, 例如类加载器,事件处理器等
            prepareBeanFactory(beanFactory);

            try {
                // 为容器的某些子类指定特殊的BeanPost事件处理器
                postProcessBeanFactory(beanFactory);

                //StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");

                // 调用所有注册的 BeanFactoryPostProcessor 的 Bean
                invokeBeanFactoryPostProcessors(beanFactory);

                // BeanPostProcessor是Bean后置处理器, 用于监听容器触发的事件
                registerBeanPostProcessors(beanFactory);

                //beanPostProcess.end();

                // 初始化信息源，和国际化相关.
                initMessageSource();

                // 初始化容器事件传播器
                initApplicationEventMulticaster();

                // 调用子类的某些特殊 Bean 初始化方法
                onRefresh();

                // 为事件传播器注册事件监听器.
                registerListeners();

                // 初始化 Bean，并对 lazy-init 属性进行处理
                finishBeanFactoryInitialization(beanFactory);

                // 初始化容器的生命周期事件处理器，并发布容器的生命周期事件
                finishRefresh();
            } catch (RuntimeException | Error ex) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Exception encountered during context initialization - cancelling refresh attempt:{}", ex.getMessage(), ex);
                }

                // 销毁以创建的单态Bean
                destroyBeans();

                // 取消refresh操作, 重置容器的同步标识
                cancelRefresh(ex);

                throw ex;
            } finally {
                //contextRefresh.end();
            }
        } finally {
            this.startupShutdownThread = null;
            this.startupShutdownLock.unlock();
        }
    }

    protected void prepareRefresh() {
        // Switch to active.
        this.startupDate = System.currentTimeMillis();
        this.closed.set(false);
        this.active.set(true);

        if (logger.isDebugEnabled()) {
            if (logger.isTraceEnabled()) {
                logger.trace("Refreshing " + this);
            }
            else {
                logger.debug("Refreshing " + getDisplayName());
            }
        }

        // Initialize any placeholder property sources in the context environment.
        initPropertySources();

        // Validate that all properties marked as required are resolvable:
        // see ConfigurablePropertyResolver#setRequiredProperties
        getEnvironment().validateRequiredProperties();

        // Store pre-refresh ApplicationListeners...
        if (this.earlyApplicationListeners == null) {
            this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
        }
        else {
            // Reset local application listeners to pre-refresh state.
            this.applicationListeners.clear();
            this.applicationListeners.addAll(this.earlyApplicationListeners);
        }

        // Allow for the collection of early ApplicationEvents,
        // to be published once the multicaster is available...
        this.earlyApplicationEvents = new LinkedHashSet<>();
    }

    protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
        refreshBeanFactory();
        return getBeanFactory();
    }

    protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

    public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;
}
