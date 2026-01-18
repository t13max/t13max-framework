package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.beans.BeanMetadataElement;
import com.t13max.ioc.beans.Mergeable;


import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author: t13max
 * @Since: 0:02 2026/1/17
 */
public class ManagedMap<K, V> extends LinkedHashMap<K, V> implements Mergeable, BeanMetadataElement {

    private  Object source;

    private  String keyTypeName;

    private  String valueTypeName;

    private boolean mergeEnabled;


    public ManagedMap() {
    }

    public ManagedMap(int initialCapacity) {
        super(initialCapacity);
    }


    
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <K,V> ManagedMap<K,V> ofEntries(Map.Entry<? extends K, ? extends V>... entries) {
        ManagedMap<K,V > map = new ManagedMap<>();
        for (Map.Entry<? extends K, ? extends V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    
    public void setSource( Object source) {
        this.source = source;
    }

    @Override
    public  Object getSource() {
        return this.source;
    }

    
    public void setKeyTypeName( String keyTypeName) {
        this.keyTypeName = keyTypeName;
    }

    
    public  String getKeyTypeName() {
        return this.keyTypeName;
    }

    
    public void setValueTypeName( String valueTypeName) {
        this.valueTypeName = valueTypeName;
    }

    
    public  String getValueTypeName() {
        return this.valueTypeName;
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
    public Object merge( Object parent) {
        if (!this.mergeEnabled) {
            throw new IllegalStateException("Not allowed to merge when the 'mergeEnabled' property is set to 'false'");
        }
        if (parent == null) {
            return this;
        }
        if (!(parent instanceof Map)) {
            throw new IllegalArgumentException("Cannot merge with object of type [" + parent.getClass() + "]");
        }
        Map<K, V> merged = new ManagedMap<>();
        merged.putAll((Map<K, V>) parent);
        merged.putAll(this);
        return merged;
    }
}
