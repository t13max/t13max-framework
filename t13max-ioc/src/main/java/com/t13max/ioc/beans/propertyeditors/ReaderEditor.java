package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;



import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceEditor;
import com.t13max.ioc.core.io.support.EncodedResource;
import com.t13max.ioc.util.Assert;

public class ReaderEditor extends PropertyEditorSupport {
	private final ResourceEditor resourceEditor;
	
	public ReaderEditor() {
		this.resourceEditor = new ResourceEditor();
	}	
	public ReaderEditor(ResourceEditor resourceEditor) {
		Assert.notNull(resourceEditor, "ResourceEditor must not be null");
		this.resourceEditor = resourceEditor;
	}

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		this.resourceEditor.setAsText(text);
		Resource resource = (Resource) this.resourceEditor.getValue();
		try {
			setValue(resource != null ? new EncodedResource(resource).getReader() : null);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Failed to retrieve Reader for " + resource, ex);
		}
	}	
	@Override
	public  String getAsText() {
		return null;
	}

}
