package com.t13max.ioc.context;

import java.util.EventObject;

/**
 * 应用事件顶级父类
 *
 * @Author: t13max
 * @Since: 22:37 2026/1/14
 */
public abstract class ApplicationEvent extends EventObject {

    //事件发生的时间
    private final long timestamp;

    public ApplicationEvent(Object source) {
        super(source);
        this.timestamp = System.currentTimeMillis();
    }

    //获取事件发生的时间戳
    public final long getTimestamp() {
        return timestamp;
    }
}
