package com.t13max.ioc.context.event;

import com.t13max.ioc.context.ApplicationContext;
import com.t13max.ioc.context.ApplicationEvent;

/**
 * 应用上下文事件抽象父类
 *
 * @Author: t13max
 * @Since: 22:40 2026/1/14
 */
public abstract class ApplicationContextEvent extends ApplicationEvent {

    public ApplicationContextEvent(Object source) {
        super(source);
    }

    // 获取应用上下文
    public final ApplicationContext getApplicationContext() {
        return (ApplicationContext) getSource();
    }
}
