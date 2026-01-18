package com.t13max.ioc.core.convert.converter;

public interface ConverterRegistry {
	
	void addConverter(Converter<?, ?> converter);
	
	<S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter);
	
	void addConverter(GenericConverter converter);
	
	void addConverterFactory(ConverterFactory<?, ?> factory);
	
	void removeConvertible(Class<?> sourceType, Class<?> targetType);

}
