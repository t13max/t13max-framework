package com.t13max.ioc.context.event;

import com.t13max.ioc.context.ApplicationContext;

public class ContextStoppedEvent extends ApplicationContextEvent {

    public ContextStoppedEvent(ApplicationContext source) {
        super(source);
    }

}
