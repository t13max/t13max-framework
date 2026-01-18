package com.t13max.ioc.core;

import com.t13max.ioc.util.Assert;

import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 22:19 2026/1/16
 */
public class NamedThreadLocal <T> extends ThreadLocal<T> {

    private final String name;    
    public NamedThreadLocal(String name) {
        Assert.hasText(name, "Name must not be empty");
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
    
    public static <S> ThreadLocal<S> withInitial(String name, Supplier<? extends S> supplier) {
        return new SuppliedNamedThreadLocal<>(name, supplier);
    }
    
    private static final class SuppliedNamedThreadLocal<T> extends NamedThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedNamedThreadLocal(String name, Supplier<? extends T> supplier) {
            super(name);
            Assert.notNull(supplier, "Supplier must not be null");
            this.supplier = supplier;
        }

        @Override
        protected T initialValue() {
            return this.supplier.get();
        }
    }
}
