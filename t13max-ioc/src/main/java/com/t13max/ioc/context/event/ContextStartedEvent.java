package com.t13max.ioc.context.event;

import com.t13max.ioc.context.ApplicationContext;

public class ContextStartedEvent extends ApplicationContextEvent {

    public ContextStartedEvent(ApplicationContext source) {
        super(source);
    }

}
