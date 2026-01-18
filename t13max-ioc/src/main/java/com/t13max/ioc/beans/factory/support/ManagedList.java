package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeanMetadataElement;
import com.t13max.ioc.beans.Mergeable;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Author: t13max
 * @Since: 0:01 2026/1/17
 */
public class ManagedList<E> extends ArrayList<E> implements Mergeable, BeanMetadataElement {

    private  Object source;

    private  String elementTypeName;

    private boolean mergeEnabled;


    public ManagedList() {
    }

    public ManagedList(int initialCapacity) {
        super(initialCapacity);
    }


    
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> ManagedList<E> of(E... elements) {
        ManagedList<E> list = new ManagedList<>();
        Collections.addAll(list, elements);
        return list;
    }

    
    public void setSource( Object source) {
        this.source = source;
    }

    @Override
    public  Object getSource() {
        return this.source;
    }

    
    public void setElementTypeName(String elementTypeName) {
        this.elementTypeName = elementTypeName;
    }

    
    public  String getElementTypeName() {
        return this.elementTypeName;
    }

    
    public void setMergeEnabled(boolean mergeEnabled) {
        this.mergeEnabled = mergeEnabled;
    }

    @Override
    public boolean isMergeEnabled() {
        return this.mergeEnabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<E> merge( Object parent) {
        if (!this.mergeEnabled) {
            throw new IllegalStateException("Not allowed to merge when the 'mergeEnabled' property is set to 'false'");
        }
        if (parent == null) {
            return this;
        }
        if (!(parent instanceof List)) {
            throw new IllegalArgumentException("Cannot merge with object of type [" + parent.getClass() + "]");
        }
        List<E> merged = new ManagedList<>();
        merged.addAll((List<E>) parent);
        merged.addAll(this);
        return merged;
    }
}
