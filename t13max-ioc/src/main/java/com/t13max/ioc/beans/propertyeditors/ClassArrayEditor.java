package com.t13max.ioc.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.StringJoiner;



import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ObjectUtils;
import com.t13max.ioc.util.StringUtils;

public class ClassArrayEditor extends PropertyEditorSupport {
	private final  ClassLoader classLoader;
	
	public ClassArrayEditor() {
		this(null);
	}	
	public ClassArrayEditor( ClassLoader classLoader) {
		this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		if (StringUtils.hasText(text)) {
			String[] classNames = StringUtils.commaDelimitedListToStringArray(text);
			Class<?>[] classes = new Class<?>[classNames.length];
			for (int i = 0; i < classNames.length; i++) {
				String className = classNames[i].trim();
				classes[i] = ClassUtils.resolveClassName(className, this.classLoader);
			}
			setValue(classes);
		}
		else {
			setValue(null);
		}
	}
	@Override
	public String getAsText() {
		Class<?>[] classes = (Class[]) getValue();
		if (ObjectUtils.isEmpty(classes)) {
			return "";
		}
		StringJoiner sj = new StringJoiner(",");
		for (Class<?> klass : classes) {
			sj.add(ClassUtils.getQualifiedName(klass));
		}
		return sj.toString();
	}

}
