package com.t13max.ioc.core;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;



import com.t13max.ioc.util.ObjectUtils;

public class OrderComparator implements Comparator<Object> {	
	public static final OrderComparator INSTANCE = new OrderComparator();
	
	public Comparator<Object> withSourceProvider(OrderSourceProvider sourceProvider) {
		return (o1, o2) -> doCompare(o1, o2, sourceProvider);
	}
	@Override
	public int compare( Object o1,  Object o2) {
		return doCompare(o1, o2, null);
	}
	private int doCompare( Object o1,  Object o2,  OrderSourceProvider sourceProvider) {
		boolean p1 = (o1 instanceof PriorityOrdered);
		boolean p2 = (o2 instanceof PriorityOrdered);
		if (p1 && !p2) {
			return -1;
		}
		else if (p2 && !p1) {
			return 1;
		}
		int i1 = getOrder(o1, sourceProvider);
		int i2 = getOrder(o2, sourceProvider);
		return Integer.compare(i1, i2);
	}	
	private int getOrder( Object obj,  OrderSourceProvider sourceProvider) {
		Integer order = null;
		if (obj != null && sourceProvider != null) {
			Object orderSource = sourceProvider.getOrderSource(obj);
			if (orderSource != null) {
				if (orderSource.getClass().isArray()) {
					for (Object source : ObjectUtils.toObjectArray(orderSource)) {
						order = findOrder(source);
						if (order != null) {
							break;
						}
					}
				}
				else {
					order = findOrder(orderSource);
				}
			}
		}
		return (order != null ? order : getOrder(obj));
	}	
	protected int getOrder( Object obj) {
		if (obj != null) {
			Integer order = findOrder(obj);
			if (order != null) {
				return order;
			}
		}
		return Ordered.LOWEST_PRECEDENCE;
	}	
	protected  Integer findOrder(Object obj) {
		return (obj instanceof Ordered ordered ? ordered.getOrder() : null);
	}	
	public  Integer getPriority(Object obj) {
		return null;
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
	
	@FunctionalInterface
	public interface OrderSourceProvider {
		
		 Object getOrderSource(Object obj);
	}

}
