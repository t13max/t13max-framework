package com.t13max.ioc.beans;

public class SimpleTypeConverter extends TypeConverterSupport {
	public SimpleTypeConverter() {
		this.typeConverterDelegate = new TypeConverterDelegate(this);
		registerDefaultEditors();
	}

}
