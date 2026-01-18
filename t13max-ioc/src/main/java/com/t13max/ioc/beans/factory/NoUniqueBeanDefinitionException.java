package com.t13max.ioc.beans.factory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;



import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.util.StringUtils;

@SuppressWarnings("serial")
public class NoUniqueBeanDefinitionException extends NoSuchBeanDefinitionException {
	private final int numberOfBeansFound;
	private final  Collection<String> beanNamesFound;


	public NoUniqueBeanDefinitionException(Class<?> type, Collection<String> beanNamesFound, String message) {
		super(type, message);
		this.numberOfBeansFound = beanNamesFound.size();
		this.beanNamesFound = new ArrayList<>(beanNamesFound);
	}

	public NoUniqueBeanDefinitionException(Class<?> type, int numberOfBeansFound, String message) {
		super(type, message);
		this.numberOfBeansFound = numberOfBeansFound;
		this.beanNamesFound = null;
	}

	public NoUniqueBeanDefinitionException(Class<?> type, Collection<String> beanNamesFound) {
		this(type, beanNamesFound, "expected single matching bean but found " + beanNamesFound.size() + ": " +
				StringUtils.collectionToCommaDelimitedString(beanNamesFound));
	}

	public NoUniqueBeanDefinitionException(Class<?> type, String... beanNamesFound) {
		this(type, Arrays.asList(beanNamesFound));
	}

	public NoUniqueBeanDefinitionException(ResolvableType type, Collection<String> beanNamesFound) {
		super(type, "expected single matching bean but found " + beanNamesFound.size() + ": " +
				StringUtils.collectionToCommaDelimitedString(beanNamesFound));
		this.numberOfBeansFound = beanNamesFound.size();
		this.beanNamesFound = new ArrayList<>(beanNamesFound);
	}

	public NoUniqueBeanDefinitionException(ResolvableType type, String... beanNamesFound) {
		this(type, Arrays.asList(beanNamesFound));
	}


	@Override
	public int getNumberOfBeansFound() {
		return this.numberOfBeansFound;
	}

	public  Collection<String> getBeanNamesFound() {
		return this.beanNamesFound;
	}

}
