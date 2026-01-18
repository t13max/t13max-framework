package com.t13max.ioc.core.convert;



public interface ConversionService {

	boolean canConvert( Class<?> sourceType, Class<?> targetType);

	boolean canConvert( TypeDescriptor sourceType, TypeDescriptor targetType);

	<T>  T convert( Object source, Class<T> targetType);

	default  Object convert( Object source, TypeDescriptor targetType) {
		return convert(source, TypeDescriptor.forObject(source), targetType);
	}

	 Object convert( Object source,  TypeDescriptor sourceType, TypeDescriptor targetType);

}
