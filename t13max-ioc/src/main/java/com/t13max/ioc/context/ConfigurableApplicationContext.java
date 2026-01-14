package com.t13max.ioc.context;

import com.t13max.ioc.beans.BeansException;

/**
 * 可配置应用上下文接口
 *
 * @Author: t13max
 * @Since: 23:19 2026/1/14
 */
public interface ConfigurableApplicationContext extends ApplicationContext {

    //刷新
    void refresh() throws BeansException, IllegalStateException;
}
