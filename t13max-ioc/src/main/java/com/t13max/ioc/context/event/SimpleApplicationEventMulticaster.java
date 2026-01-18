package com.t13max.ioc.context.event;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.context.ApplicationEvent;
import com.t13max.ioc.context.ApplicationListener;
import com.t13max.ioc.context.PayloadApplicationEvent;
import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.util.ErrorHandler;
import org.apache.logging.log4j.Logger;

public class SimpleApplicationEventMulticaster extends AbstractApplicationEventMulticaster {
    private Executor taskExecutor;
    private ErrorHandler errorHandler;
    private volatile Logger lazyLogger;


    public SimpleApplicationEventMulticaster() {
    }

    public SimpleApplicationEventMulticaster(BeanFactory beanFactory) {
        setBeanFactory(beanFactory);
    }


    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    protected Executor getTaskExecutor() {
        return this.taskExecutor;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    protected ErrorHandler getErrorHandler() {
        return this.errorHandler;
    }

    @Override
    public void multicastEvent(ApplicationEvent event) {
        multicastEvent(event, null);
    }

    @Override
    public void multicastEvent(ApplicationEvent event, ResolvableType eventType) {
        ResolvableType type = (eventType != null ? eventType : ResolvableType.forInstance(event));
        Executor executor = getTaskExecutor();
        for (ApplicationListener<?> listener : getApplicationListeners(event, type)) {
            if (executor != null && listener.supportsAsyncExecution()) {
                try {
                    executor.execute(() -> invokeListener(listener, event));
                } catch (RejectedExecutionException ex) {
                    // Probably on shutdown -> invoke listener locally instead
                    invokeListener(listener, event);
                }
            } else {
                invokeListener(listener, event);
            }
        }
    }

    protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
        ErrorHandler errorHandler = getErrorHandler();
        if (errorHandler != null) {
            try {
                doInvokeListener(listener, event);
            } catch (Throwable err) {
                errorHandler.handleError(err);
            }
        } else {
            doInvokeListener(listener, event);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void doInvokeListener(ApplicationListener listener, ApplicationEvent event) {
        try {
            listener.onApplicationEvent(event);
        } catch (ClassCastException ex) {
            String msg = ex.getMessage();
            if (msg == null || matchesClassCastMessage(msg, event.getClass()) ||
                    (event instanceof PayloadApplicationEvent payloadEvent &&
                            matchesClassCastMessage(msg, payloadEvent.getPayload().getClass()))) {
                // Possibly a lambda-defined listener which we could not resolve the generic event type for
                // -> let's suppress the exception.
                Log loggerToUse = this.lazyLogger;
                if (loggerToUse == null) {
                    loggerToUse = LogFactory.getLog(getClass());
                    this.lazyLogger = loggerToUse;
                }
                if (loggerToUse.isTraceEnabled()) {
                    loggerToUse.trace("Non-matching event type for listener: " + listener, ex);
                }
            } else {
                throw ex;
            }
        }
    }

    private boolean matchesClassCastMessage(String classCastMessage, Class<?> eventClass) {
        // On Java 8, the message starts with the class name: "java.lang.String cannot be cast..."
        if (classCastMessage.startsWith(eventClass.getName())) {
            return true;
        }
        // On Java 11, the message starts with "class ..." a.k.a. Class.toString()
        if (classCastMessage.startsWith(eventClass.toString())) {
            return true;
        }
        // On Java 9, the message used to contain the module name: "java.base/java.lang.String cannot be cast..."
        int moduleSeparatorIndex = classCastMessage.indexOf('/');
        if (moduleSeparatorIndex != -1 && classCastMessage.startsWith(eventClass.getName(), moduleSeparatorIndex + 1)) {
            return true;
        }
        // Assuming an unrelated class cast failure...
        return false;
    }

}
