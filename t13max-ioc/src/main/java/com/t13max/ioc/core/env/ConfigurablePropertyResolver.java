package com.t13max.ioc.core.env;

import com.t13max.ioc.core.convert.support.ConfigurableConversionService;

/**
 * @Author: t13max
 * @Since: 21:28 2026/1/16
 */
public interface ConfigurablePropertyResolver extends PropertyResolver {

    ConfigurableConversionService getConversionService();

    void setConversionService(ConfigurableConversionService conversionService);

    void setPlaceholderPrefix(String placeholderPrefix);

    void setPlaceholderSuffix(String placeholderSuffix);

    void setValueSeparator(String valueSeparator);

    void setEscapeCharacter(Character escapeCharacter);

    void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders);

    void setRequiredProperties(String... requiredProperties);

    void validateRequiredProperties() throws MissingRequiredPropertiesException;
}
