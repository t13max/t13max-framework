package com.t13max.ioc.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;



public class DescriptiveResource extends AbstractResource {
	private final String description;
	
	public DescriptiveResource( String description) {
		this.description = (description != null ? description : "");
	}

	@Override
	public boolean exists() {
		return false;
	}
	@Override
	public boolean isReadable() {
		return false;
	}
	@Override
	public InputStream getInputStream() throws IOException {
		throw new FileNotFoundException(
				getDescription() + " cannot be opened because it does not point to a readable resource");
	}
	@Override
	public String getDescription() {
		return this.description;
	}
	
	@Override
	public boolean equals( Object other) {
		return (this == other || (other instanceof DescriptiveResource that &&
				this.description.equals(that.description)));
	}	
	@Override
	public int hashCode() {
		return this.description.hashCode();
	}

}
