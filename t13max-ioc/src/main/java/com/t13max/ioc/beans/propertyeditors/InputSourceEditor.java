package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;

import org.xml.sax.InputSource;

import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceEditor;
import com.t13max.ioc.util.Assert;

public class InputSourceEditor extends PropertyEditorSupport {
	private final ResourceEditor resourceEditor;


	public InputSourceEditor() {
		this.resourceEditor = new ResourceEditor();
	}

	public InputSourceEditor(ResourceEditor resourceEditor) {
		Assert.notNull(resourceEditor, "ResourceEditor must not be null");
		this.resourceEditor = resourceEditor;
	}

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		this.resourceEditor.setAsText(text);
		Resource resource = (Resource) this.resourceEditor.getValue();
		try {
			setValue(resource != null ? new InputSource(resource.getURL().toString()) : null);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(
					"Could not retrieve URL for " + resource + ": " + ex.getMessage());
		}
	}
	@Override
	public String getAsText() {
		InputSource value = (InputSource) getValue();
		return (value != null ? value.getSystemId() : "");
	}

}
