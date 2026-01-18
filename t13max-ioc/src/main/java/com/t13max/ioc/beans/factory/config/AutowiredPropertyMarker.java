package com.t13max.ioc.beans.factory.config;

import java.io.Serializable;



@SuppressWarnings("serial")
public final class AutowiredPropertyMarker implements Serializable {

	public static final Object INSTANCE = new AutowiredPropertyMarker();

	private AutowiredPropertyMarker() {
	}
	private Object readResolve() {
		return INSTANCE;
	}

	@Override
	public boolean equals( Object other) {
		return (this == other);
	}
	@Override
	public int hashCode() {
		return AutowiredPropertyMarker.class.hashCode();
	}
	@Override
	public String toString() {
		return "(autowired)";
	}

}
