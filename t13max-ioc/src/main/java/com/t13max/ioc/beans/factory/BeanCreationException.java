package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.FatalBeanException;
import com.t13max.ioc.core.NestedRuntimeException;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: t13max
 * @Since: 23:24 2026/1/15
 */
public class BeanCreationException extends FatalBeanException {

    private final String beanName;

    private final String resourceDescription;

    private List<Throwable> relatedCauses;

    public BeanCreationException(String msg) {
        super(msg);
        this.beanName = null;
        this.resourceDescription = null;
    }

    public BeanCreationException(String msg, Throwable cause) {
        super(msg, cause);
        this.beanName = null;
        this.resourceDescription = null;
    }

    public BeanCreationException(String beanName, String msg) {
        super("Error creating bean with name '" + beanName + "': " + msg);
        this.beanName = beanName;
        this.resourceDescription = null;
    }

    public BeanCreationException(String beanName, String msg, Throwable cause) {
        this(beanName, msg);
        initCause(cause);
    }

    public BeanCreationException(String resourceDescription, String beanName, String msg) {
        super("Error creating bean with name '" + beanName + "'" +
                (resourceDescription != null ? " defined in " + resourceDescription : "") + ": " + msg);
        this.resourceDescription = resourceDescription;
        this.beanName = beanName;
        this.relatedCauses = null;
    }

    public BeanCreationException(String resourceDescription, String beanName, String msg, Throwable cause) {
        this(resourceDescription, beanName, msg);
        initCause(cause);
    }

    public String getResourceDescription() {
        return this.resourceDescription;
    }

    public String getBeanName() {
        return this.beanName;
    }

    public void addRelatedCause(Throwable ex) {
        if (this.relatedCauses == null) {
            this.relatedCauses = new ArrayList<>();
        }
        this.relatedCauses.add(ex);
    }

    public Throwable[] getRelatedCauses() {
        if (this.relatedCauses == null) {
            return null;
        }
        return this.relatedCauses.toArray(new Throwable[0]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (this.relatedCauses != null) {
            for (Throwable relatedCause : this.relatedCauses) {
                sb.append("\nRelated cause: ");
                sb.append(relatedCause);
            }
        }
        return sb.toString();
    }

    @Override
    public void printStackTrace(PrintStream ps) {
        synchronized (ps) {
            super.printStackTrace(ps);
            if (this.relatedCauses != null) {
                for (Throwable relatedCause : this.relatedCauses) {
                    ps.println("Related cause:");
                    relatedCause.printStackTrace(ps);
                }
            }
        }
    }

    @Override
    public void printStackTrace(PrintWriter pw) {
        synchronized (pw) {
            super.printStackTrace(pw);
            if (this.relatedCauses != null) {
                for (Throwable relatedCause : this.relatedCauses) {
                    pw.println("Related cause:");
                    relatedCause.printStackTrace(pw);
                }
            }
        }
    }

    @Override
    public boolean contains(Class<?> exClass) {
        if (super.contains(exClass)) {
            return true;
        }
        if (this.relatedCauses != null) {
            for (Throwable relatedCause : this.relatedCauses) {
                if (relatedCause instanceof NestedRuntimeException nested && nested.contains(exClass)) {
                    return true;
                }
            }
        }
        return false;
    }
}
