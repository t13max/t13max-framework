package com.t13max.ioc.core.annotation;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;



import com.t13max.ioc.core.DecoratingProxy;
import com.t13max.ioc.core.OrderComparator;
import com.t13max.ioc.core.annotation.MergedAnnotations.SearchStrategy;

public class AnnotationAwareOrderComparator extends OrderComparator {

	public static final AnnotationAwareOrderComparator INSTANCE = new AnnotationAwareOrderComparator();


	@Override
	protected  Integer findOrder(Object obj) {
		Integer order = super.findOrder(obj);
		if (order != null) {
			return order;
		}
		return findOrderFromAnnotation(obj);
	}

	private  Integer findOrderFromAnnotation(Object obj) {
		AnnotatedElement element = (obj instanceof AnnotatedElement ae ? ae : obj.getClass());
		MergedAnnotations annotations = MergedAnnotations.from(element, SearchStrategy.TYPE_HIERARCHY);
		Integer order = OrderUtils.getOrderFromAnnotations(element, annotations);
		if (order == null && obj instanceof DecoratingProxy decoratingProxy) {
			return findOrderFromAnnotation(decoratingProxy.getDecoratedClass());
		}
		return order;
	}

	@Override
	public  Integer getPriority(Object obj) {
		if (obj instanceof Class<?> clazz) {
			return OrderUtils.getPriority(clazz);
		}
		Integer priority = OrderUtils.getPriority(obj.getClass());
		if (priority == null && obj instanceof DecoratingProxy decoratingProxy) {
			return getPriority(decoratingProxy.getDecoratedClass());
		}
		return priority;
	}


	public static void sort(List<?> list) {
		if (list.size() > 1) {
			list.sort(INSTANCE);
		}
	}

	public static void sort(Object[] array) {
		if (array.length > 1) {
			Arrays.sort(array, INSTANCE);
		}
	}

	public static void sortIfNecessary(Object value) {
		if (value instanceof Object[] objects) {
			sort(objects);
		}
		else if (value instanceof List<?> list) {
			sort(list);
		}
	}

}
