package com.t13max.ioc.core.env;

import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * @Author: t13max
 * @Since: 7:38 2026/1/17
 */
public abstract class PropertySource <T> {

    protected final Logger logger = LogManager.getLogger(getClass());

    protected final String name;

    protected final T source;


    /**
     * Create a new {@code PropertySource} with the given name and source object.
     * @param name the associated name
     * @param source the source object
     */
    public PropertySource(String name, T source) {
        Assert.hasText(name, "Property source name must contain at least one character");
        Assert.notNull(source, "Property source must not be null");
        this.name = name;
        this.source = source;
    }

    /**
     * Create a new {@code PropertySource} with the given name and with a new
     * {@code Object} instance as the underlying source.
     * <p>Often useful in testing scenarios when creating anonymous implementations
     * that never query an actual source but rather return hard-coded values.
     */
    @SuppressWarnings("unchecked")
    public PropertySource(String name) {
        this(name, (T) new Object());
    }


    /**
     * Return the name of this {@code PropertySource}.
     * <p>See the {@linkplain PropertySource class-level Javadoc} for details
     * on property source identity and names.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Return the underlying source object for this {@code PropertySource}.
     */
    public T getSource() {
        return this.source;
    }

    /**
     * Return whether this {@code PropertySource} contains the given name.
     * <p>This implementation simply checks for a {@code null} return value
     * from {@link #getProperty(String)}. Subclasses may wish to implement
     * a more efficient algorithm if possible.
     * @param name the property name to find
     */
    public boolean containsProperty(String name) {
        return (getProperty(name) != null);
    }

    /**
     * Return the value associated with the given name,
     * or {@code null} if not found.
     * @param name the property to find
     * @see PropertyResolver#getRequiredProperty(String)
     */
    public abstract  Object getProperty(String name);


    /**
     * This {@code PropertySource} object is equal to the given object if:
     * <ul>
     * <li>they are the same instance
     * <li>the {@code name} properties for both objects are equal
     * </ul>
     * <p>No properties other than {@code name} are evaluated.
     */
    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof PropertySource<?> that &&
                ObjectUtils.nullSafeEquals(getName(), that.getName())));
    }

    /**
     * Return a hash code derived from the {@code name} property
     * of this {@code PropertySource} object.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(getName());
    }

    @Override
    public String toString() {
        if (logger.isDebugEnabled()) {
            return getClass().getSimpleName() + "@" + System.identityHashCode(this) +
                    " {name='" + getName() + "', properties=" + getSource() + "}";
        }
        else {
            return getClass().getSimpleName() + " {name='" + getName() + "'}";
        }
    }

    public static PropertySource<?> named(String name) {
        return new ComparisonPropertySource(name);
    }

    public static class StubPropertySource extends PropertySource<Object> {

        public StubPropertySource(String name) {
            super(name);
        }

        /**
         * Always returns {@code null}.
         */
        @Override
        public  String getProperty(String name) {
            return null;
        }
    }


    /**
     * A {@code PropertySource} implementation intended for collection comparison
     * purposes.
     *
     * @see PropertySource#named(String)
     */
    static class ComparisonPropertySource extends StubPropertySource {

        private static final String USAGE_ERROR =
                "ComparisonPropertySource instances are for use with collection comparison only";

        public ComparisonPropertySource(String name) {
            super(name);
        }

        @Override
        public Object getSource() {
            throw new UnsupportedOperationException(USAGE_ERROR);
        }

        @Override
        public boolean containsProperty(String name) {
            throw new UnsupportedOperationException(USAGE_ERROR);
        }

        @Override
        public  String getProperty(String name) {
            throw new UnsupportedOperationException(USAGE_ERROR);
        }
    }
}
