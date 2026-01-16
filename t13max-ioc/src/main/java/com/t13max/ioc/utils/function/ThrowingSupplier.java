package com.t13max.ioc.utils.function;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @Author: t13max
 * @Since: 20:59 2026/1/16
 */
public interface ThrowingSupplier <T> extends Supplier<T> {
    T getWithException() throws Exception;
    @Override
    default T get() {
        return get(RuntimeException::new);
    }
    default T get(BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
        try {
            return getWithException();
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw exceptionWrapper.apply(ex.getMessage(), ex);
        }
    }
    default ThrowingSupplier<T> throwing(BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
        return new ThrowingSupplier<>() {
            @Override
            public T getWithException() throws Exception {
                return ThrowingSupplier.this.getWithException();
            }
            @Override
            public T get() {
                return get(exceptionWrapper);
            }
        };
    }
    static <T> ThrowingSupplier<T> of(ThrowingSupplier<T> supplier) {
        return supplier;
    }
    static <T> ThrowingSupplier<T> of(ThrowingSupplier<T> supplier, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
        return supplier.throwing(exceptionWrapper);
    }
}
