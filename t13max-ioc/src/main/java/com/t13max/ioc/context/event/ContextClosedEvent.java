package com.t13max.ioc.context.event;

import com.t13max.ioc.context.ApplicationContext;

public class ContextClosedEvent extends ApplicationContextEvent {

    public ContextClosedEvent(ApplicationContext source) {
        super(source);
    }

}
