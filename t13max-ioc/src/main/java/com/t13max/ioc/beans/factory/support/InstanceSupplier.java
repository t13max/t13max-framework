package com.t13max.ioc.beans.factory.support;

import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.function.ThrowingBiFunction;
import com.t13max.ioc.util.function.ThrowingSupplier;

import java.lang.reflect.Method;

/**
 * @Author: t13max
 * @Since: 20:59 2026/1/16
 */
@FunctionalInterface
public interface InstanceSupplier<T> extends ThrowingSupplier<T> {

    @Override
    default T getWithException() {
        throw new IllegalStateException("No RegisteredBean parameter provided");
    }

    T get(RegisteredBean registeredBean) throws Exception;

    default Method getFactoryMethod() {
        return null;
    }

    default <V> InstanceSupplier<V> andThen(ThrowingBiFunction<RegisteredBean, ? super T, ? extends V> after) {

        Assert.notNull(after, "'after' function must not be null");
        return new InstanceSupplier<>() {
            @Override
            public V get(RegisteredBean registeredBean) throws Exception {
                return after.applyWithException(registeredBean, InstanceSupplier.this.get(registeredBean));
            }

            @Override
            public Method getFactoryMethod() {
                return InstanceSupplier.this.getFactoryMethod();
            }
        };
    }

    static <T> InstanceSupplier<T> using(ThrowingSupplier<T> supplier) {
        Assert.notNull(supplier, "Supplier must not be null");
        if (supplier instanceof InstanceSupplier<T> instanceSupplier) {
            return instanceSupplier;
        }
        return registeredBean -> supplier.getWithException();
    }

    static <T> InstanceSupplier<T> using(Method factoryMethod, ThrowingSupplier<T> supplier) {
        Assert.notNull(supplier, "Supplier must not be null");

        if (supplier instanceof InstanceSupplier<T> instanceSupplier &&
                instanceSupplier.getFactoryMethod() == factoryMethod) {
            return instanceSupplier;
        }

        return new InstanceSupplier<>() {
            @Override
            public T get(RegisteredBean registeredBean) throws Exception {
                return supplier.getWithException();
            }

            @Override
            public Method getFactoryMethod() {
                return factoryMethod;
            }
        };
    }

    static <T> InstanceSupplier<T> of(InstanceSupplier<T> instanceSupplier) {
        Assert.notNull(instanceSupplier, "InstanceSupplier must not be null");
        return instanceSupplier;
    }

}
