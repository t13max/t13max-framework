package com.t13max.ioc.beans.factory;

import com.t13max.ioc.beans.BeansException;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @Author: t13max
 * @Since: 22:04 2026/1/15
 */
public interface ObjectProvider<T> extends ObjectFactory<T>, Iterable<T> {

    Predicate<Class<?>> UNFILTERED = (clazz -> true);


    @Override
    default T getObject() throws BeansException {
        Iterator<T> it = iterator();
        if (!it.hasNext()) {
            throw new NoSuchBeanDefinitionException(Object.class);
        }
        T result = it.next();
        if (it.hasNext()) {
            throw new NoUniqueBeanDefinitionException(Object.class, 2, "more than 1 matching bean");
        }
        return result;
    }
    
    default T getObject(Object... args) throws BeansException {
        throw new UnsupportedOperationException("Retrieval with arguments not supported -" +
                "for custom ObjectProvider classes, implement getObject(Object...) for your purposes");
    }
    
    default T getIfAvailable() throws BeansException {
        try {
            return getObject();
        } catch (NoUniqueBeanDefinitionException ex) {
            throw ex;
        } catch (NoSuchBeanDefinitionException ex) {
            return null;
        }
    }
    
    default T getIfAvailable(Supplier<T> defaultSupplier) throws BeansException {
        T dependency = getIfAvailable();
        return (dependency != null ? dependency : defaultSupplier.get());
    }
    
    default void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
        T dependency = getIfAvailable();
        if (dependency != null) {
            dependencyConsumer.accept(dependency);
        }
    }
    
    default T getIfUnique() throws BeansException {
        try {
            return getObject();
        } catch (NoSuchBeanDefinitionException ex) {
            return null;
        }
    }
    
    default T getIfUnique(Supplier<T> defaultSupplier) throws BeansException {
        T dependency = getIfUnique();
        return (dependency != null ? dependency : defaultSupplier.get());
    }
    
    default void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
        T dependency = getIfUnique();
        if (dependency != null) {
            dependencyConsumer.accept(dependency);
        }
    }
    
    @Override
    default Iterator<T> iterator() {
        return stream().iterator();
    }
    
    default Stream<T> stream() {
        throw new UnsupportedOperationException("Element access not supported - " +
                "for custom ObjectProvider classes, implement stream() to enable all other methods");
    }
    
    default Stream<T> orderedStream() {
        return stream().sorted(OrderComparator.INSTANCE);
    }
    
    default Stream<T> stream(Predicate<Class<?>> customFilter) {
        return stream(customFilter, true);
    }
    
    default Stream<T> orderedStream(Predicate<Class<?>> customFilter) {
        return orderedStream(customFilter, true);
    }
    
    default Stream<T> stream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
        if (!includeNonSingletons) {
            throw new UnsupportedOperationException("Only supports includeNonSingletons=true by default");
        }
        return stream().filter(obj -> customFilter.test(obj.getClass()));
    }
    
    default Stream<T> orderedStream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
        if (!includeNonSingletons) {
            throw new UnsupportedOperationException("Only supports includeNonSingletons=true by default");
        }
        return orderedStream().filter(obj -> customFilter.test(obj.getClass()));
    }
}
