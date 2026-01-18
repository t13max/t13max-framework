package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceEditor;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ResourceUtils;

public class PathEditor extends PropertyEditorSupport {
	private final ResourceEditor resourceEditor;


	public PathEditor() {
		this.resourceEditor = new ResourceEditor();
	}

	public PathEditor(ResourceEditor resourceEditor) {
		Assert.notNull(resourceEditor, "ResourceEditor must not be null");
		this.resourceEditor = resourceEditor;
	}

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		boolean nioPathCandidate = !text.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX);
		if (nioPathCandidate && !text.startsWith("/")) {
			try {
				URI uri = ResourceUtils.toURI(text);
				String scheme = uri.getScheme();
				if (scheme != null) {
					// No NIO candidate except for "C:" style drive letters
					nioPathCandidate = (scheme.length() == 1);
					// Let's try NIO file system providers via Paths.get(URI)
					setValue(Paths.get(uri).normalize());
					return;
				}
			}
			catch (URISyntaxException ex) {
				// Not a valid URI; potentially a Windows-style path after
				// a file prefix (let's try as Spring resource location)
				nioPathCandidate = !text.startsWith(ResourceUtils.FILE_URL_PREFIX);
			}
			catch (FileSystemNotFoundException | IllegalArgumentException ex) {
				// URI scheme not registered for NIO or not meeting Paths requirements:
				// let's try URL protocol handlers via Spring's resource mechanism.
			}
		}
		this.resourceEditor.setAsText(text);
		Resource resource = (Resource) this.resourceEditor.getValue();
		if (resource == null) {
			setValue(null);
		}
		else if (nioPathCandidate && !resource.exists()) {
			setValue(Paths.get(text).normalize());
		}
		else {
			try {
				setValue(resource.getFile().toPath());
			}
			catch (IOException ex) {
				String msg = "Could not resolve \"" + text + "\" to 'java.nio.file.Path' for " + resource + ": " +
						ex.getMessage();
				if (nioPathCandidate) {
					msg += " - In case of ambiguity, consider adding the 'file:' prefix for an explicit reference " +
							"to a file system resource of the same name: \"file:" + text + "\"";
				}
				throw new IllegalArgumentException(msg);
			}
		}
	}
	@Override
	public String getAsText() {
		Path value = (Path) getValue();
		return (value != null ? value.toString() : "");
	}

}
