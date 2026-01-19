package com.t13max.ioc.beans.factory.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;



import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.BeanFactoryUtils;
import com.t13max.ioc.beans.factory.ListableBeanFactory;
import com.t13max.ioc.beans.factory.NoSuchBeanDefinitionException;
import com.t13max.ioc.beans.factory.NoUniqueBeanDefinitionException;
import com.t13max.ioc.beans.factory.config.BeanDefinition;
import com.t13max.ioc.beans.factory.config.ConfigurableBeanFactory;
import com.t13max.ioc.beans.factory.support.AbstractBeanDefinition;
import com.t13max.ioc.beans.factory.support.AutowireCandidateQualifier;
import com.t13max.ioc.beans.factory.support.RootBeanDefinition;
import com.t13max.ioc.core.annotation.AnnotationUtils;
import com.t13max.ioc.util.Assert;

public abstract class BeanFactoryAnnotationUtils {

	public static <T> Map<String, T> qualifiedBeansOfType(
			ListableBeanFactory beanFactory, Class<T> beanType, String qualifier) throws BeansException {
		String[] candidateBeans = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, beanType);
		Map<String, T> result = new LinkedHashMap<>(4);
		for (String beanName : candidateBeans) {
			if (isQualifierMatch(qualifier::equals, beanName, beanFactory)) {
				result.put(beanName, beanFactory.getBean(beanName, beanType));
			}
		}
		return result;
	}

	public static <T> T qualifiedBeanOfType(BeanFactory beanFactory, Class<T> beanType, String qualifier)
			throws BeansException {
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		if (beanFactory instanceof ListableBeanFactory lbf) {
			// Full qualifier matching supported.
			return qualifiedBeanOfType(lbf, beanType, qualifier);
		}
		else if (beanFactory.containsBean(qualifier) && beanFactory.isTypeMatch(qualifier, beanType)) {
			// Fallback: target bean at least found by bean name.
			return beanFactory.getBean(qualifier, beanType);
		}
		else {
			throw new NoSuchBeanDefinitionException(qualifier, "No matching " + beanType.getSimpleName() +
					" bean found for bean name '" + qualifier +
					"'! (Note: Qualifier matching not supported because given " +
					"BeanFactory does not implement ConfigurableListableBeanFactory.)");
		}
	}

	private static <T> T qualifiedBeanOfType(ListableBeanFactory beanFactory, Class<T> beanType, String qualifier) {
		String[] candidateBeans = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, beanType);
		String matchingBean = null;
		for (String beanName : candidateBeans) {
			if (isQualifierMatch(qualifier::equals, beanName, beanFactory)) {
				if (matchingBean != null) {
					throw new NoUniqueBeanDefinitionException(beanType, matchingBean, beanName);
				}
				matchingBean = beanName;
			}
		}
		if (matchingBean != null) {
			return beanFactory.getBean(matchingBean, beanType);
		}
		else if (beanFactory.containsBean(qualifier) && beanFactory.isTypeMatch(qualifier, beanType)) {
			// Fallback: target bean at least found by bean name - probably a manually registered singleton.
			return beanFactory.getBean(qualifier, beanType);
		}
		else {
			throw new NoSuchBeanDefinitionException(qualifier, "No matching " + beanType.getSimpleName() +
					" bean found for qualifier '" + qualifier + "' - neither qualifier match nor bean name match!");
		}
	}

	public static  String getQualifierValue(AnnotatedElement annotatedElement) {
		Qualifier qualifier = AnnotationUtils.getAnnotation(annotatedElement, Qualifier.class);
		return (qualifier != null ? qualifier.value() : null);
	}

	public static boolean isQualifierMatch(
			Predicate<String> qualifier, String beanName,  BeanFactory beanFactory) {
		// Try quick bean name or alias match first...
		if (qualifier.test(beanName)) {
			return true;
		}
		if (beanFactory != null) {
			for (String alias : beanFactory.getAliases(beanName)) {
				if (qualifier.test(alias)) {
					return true;
				}
			}
			try {
				Class<?> beanType = beanFactory.getType(beanName);
				if (beanFactory instanceof ConfigurableBeanFactory cbf) {
					BeanDefinition bd = cbf.getMergedBeanDefinition(beanName);
					// Explicit qualifier metadata on bean definition? (typically in XML definition)
					if (bd instanceof AbstractBeanDefinition abd) {
						AutowireCandidateQualifier candidate = abd.getQualifier(Qualifier.class.getName());
						if (candidate != null) {
							Object value = candidate.getAttribute(AutowireCandidateQualifier.VALUE_KEY);
							if (value != null && qualifier.test(value.toString())) {
								return true;
							}
						}
					}
					// Corresponding qualifier on factory method? (typically in configuration class)
					if (bd instanceof RootBeanDefinition rbd) {
						Method factoryMethod = rbd.getResolvedFactoryMethod();
						if (factoryMethod != null) {
							Qualifier targetAnnotation = AnnotationUtils.getAnnotation(factoryMethod, Qualifier.class);
							if (targetAnnotation != null) {
								return qualifier.test(targetAnnotation.value());
							}
						}
					}
				}
				// Corresponding qualifier on bean implementation class? (for custom user types)
				if (beanType != null) {
					Qualifier targetAnnotation = AnnotationUtils.getAnnotation(beanType, Qualifier.class);
					if (targetAnnotation != null) {
						return qualifier.test(targetAnnotation.value());
					}
				}
			}
			catch (NoSuchBeanDefinitionException ignored) {
				// can't compare qualifiers for a manually registered singleton object
			}
		}
		return false;
	}

}
