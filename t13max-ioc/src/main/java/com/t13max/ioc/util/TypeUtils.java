package com.t13max.ioc.util;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

import org.jspecify.annotations.Nullable;

import com.t13max.ioc.lang.Contract;

public abstract class TypeUtils {

	private static final Type[] IMPLICIT_LOWER_BOUNDS = { null };

	private static final Type[] IMPLICIT_UPPER_BOUNDS = { Object.class };

	
	public static boolean isAssignable(Type lhsType, Type rhsType) {
		Assert.notNull(lhsType, "Left-hand side type must not be null");
		Assert.notNull(rhsType, "Right-hand side type must not be null");

		// all types are assignable to themselves and to class Object
		if (lhsType.equals(rhsType) || Object.class == lhsType) {
			return true;
		}

		if (lhsType instanceof Class<?> lhsClass) {
			// just comparing two classes
			if (rhsType instanceof Class<?> rhsClass) {
				return ClassUtils.isAssignable(lhsClass, rhsClass);
			}

			if (rhsType instanceof ParameterizedType rhsParameterizedType) {
				Type rhsRaw = rhsParameterizedType.getRawType();

				// a parameterized type is always assignable to its raw class type
				if (rhsRaw instanceof Class<?> rhRawClass) {
					return ClassUtils.isAssignable(lhsClass, rhRawClass);
				}
			}
			else if (lhsClass.isArray() && rhsType instanceof GenericArrayType rhsGenericArrayType) {
				Type rhsComponent = rhsGenericArrayType.getGenericComponentType();

				return isAssignable(lhsClass.componentType(), rhsComponent);
			}
		}

		// parameterized types are only assignable to other parameterized types and class types
		if (lhsType instanceof ParameterizedType lhsParameterizedType) {
			if (rhsType instanceof Class<?> rhsClass) {
				Type lhsRaw = lhsParameterizedType.getRawType();

				if (lhsRaw instanceof Class<?> lhsClass) {
					return ClassUtils.isAssignable(lhsClass, rhsClass);
				}
			}
			else if (rhsType instanceof ParameterizedType rhsParameterizedType) {
				return isAssignable(lhsParameterizedType, rhsParameterizedType);
			}
		}

		if (lhsType instanceof GenericArrayType lhsGenericArrayType) {
			Type lhsComponent = lhsGenericArrayType.getGenericComponentType();

			if (rhsType instanceof Class<?> rhsClass && rhsClass.isArray()) {
				return isAssignable(lhsComponent, rhsClass.componentType());
			}
			else if (rhsType instanceof GenericArrayType rhsGenericArrayType) {
				Type rhsComponent = rhsGenericArrayType.getGenericComponentType();

				return isAssignable(lhsComponent, rhsComponent);
			}
		}

		if (lhsType instanceof WildcardType lhsWildcardType) {
			return isAssignable(lhsWildcardType, rhsType);
		}

		return false;
	}

	private static boolean isAssignable(ParameterizedType lhsType, ParameterizedType rhsType) {
		if (lhsType.equals(rhsType)) {
			return true;
		}

		Type[] lhsTypeArguments = lhsType.getActualTypeArguments();
		Type[] rhsTypeArguments = rhsType.getActualTypeArguments();

		if (lhsTypeArguments.length != rhsTypeArguments.length) {
			return false;
		}

		for (int size = lhsTypeArguments.length, i = 0; i < size; ++i) {
			Type lhsArg = lhsTypeArguments[i];
			Type rhsArg = rhsTypeArguments[i];

			if (!lhsArg.equals(rhsArg) &&
					!(lhsArg instanceof WildcardType wildcardType && isAssignable(wildcardType, rhsArg))) {
				return false;
			}
		}

		return true;
	}

	private static boolean isAssignable(WildcardType lhsType, Type rhsType) {
		Type[] lUpperBounds = getUpperBounds(lhsType);

		Type[] lLowerBounds = getLowerBounds(lhsType);

		if (rhsType instanceof WildcardType rhsWcType) {
			// both the upper and lower bounds of the right-hand side must be
			// completely enclosed in the upper and lower bounds of the left-
			// hand side.
			Type[] rUpperBounds = getUpperBounds(rhsWcType);

			Type[] rLowerBounds = getLowerBounds(rhsWcType);

			for (Type lBound : lUpperBounds) {
				for (Type rBound : rUpperBounds) {
					if (!isAssignableBound(lBound, rBound)) {
						return false;
					}
				}

				for (Type rBound : rLowerBounds) {
					if (!isAssignableBound(lBound, rBound)) {
						return false;
					}
				}
			}

			for (Type lBound : lLowerBounds) {
				for (Type rBound : rUpperBounds) {
					if (!isAssignableBound(rBound, lBound)) {
						return false;
					}
				}

				for (Type rBound : rLowerBounds) {
					if (!isAssignableBound(rBound, lBound)) {
						return false;
					}
				}
			}
		}
		else {
			for (Type lBound : lUpperBounds) {
				if (!isAssignableBound(lBound, rhsType)) {
					return false;
				}
			}

			for (Type lBound : lLowerBounds) {
				if (!isAssignableBound(rhsType, lBound)) {
					return false;
				}
			}
		}

		return true;
	}

	private static Type[] getLowerBounds(WildcardType wildcardType) {
		Type[] lowerBounds = wildcardType.getLowerBounds();

		// supply the implicit lower bound if none are specified
		return (lowerBounds.length == 0 ? IMPLICIT_LOWER_BOUNDS : lowerBounds);
	}

	private static Type[] getUpperBounds(WildcardType wildcardType) {
		Type[] upperBounds = wildcardType.getUpperBounds();

		// supply the implicit upper bound if none are specified
		return (upperBounds.length == 0 ? IMPLICIT_UPPER_BOUNDS : upperBounds);
	}

	@Contract("_, null -> true; null, _ -> false")
	public static boolean isAssignableBound( Type lhsType,  Type rhsType) {
		if (rhsType == null) {
			return true;
		}
		if (lhsType == null) {
			return false;
		}
		return isAssignable(lhsType, rhsType);
	}

}
