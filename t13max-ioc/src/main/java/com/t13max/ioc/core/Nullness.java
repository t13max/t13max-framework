package com.t13max.ioc.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;
import java.util.function.Predicate;

import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;


public enum Nullness {	
	UNSPECIFIED,	
	NULLABLE,	
	NON_NULL;
	
	public static Nullness forMethodReturnType(Method method) {
		if (KotlinDetector.isKotlinType(method.getDeclaringClass())) {
			return KotlinDelegate.forMethodReturnType(method);
		}
		return (hasNullableAnnotation(method) ? Nullness.NULLABLE :
				jSpecifyNullness(method, method.getDeclaringClass(), method.getAnnotatedReturnType()));
	}	
	public static Nullness forParameter(Parameter parameter) {
		if (KotlinDetector.isKotlinType(parameter.getDeclaringExecutable().getDeclaringClass())) {
			// TODO Optimize when kotlin-reflect provide a more direct Parameter to KParameter resolution
			MethodParameter methodParameter = MethodParameter.forParameter(parameter);
			return KotlinDelegate.forParameter(methodParameter.getExecutable(), methodParameter.getParameterIndex());
		}
		Executable executable = parameter.getDeclaringExecutable();
		return (hasNullableAnnotation(parameter) ? Nullness.NULLABLE :
				jSpecifyNullness(executable, executable.getDeclaringClass(), parameter.getAnnotatedType()));
	}	
	public static Nullness forMethodParameter(MethodParameter methodParameter) {
		return (methodParameter.getParameterIndex() < 0 ?
				forMethodReturnType(Objects.requireNonNull(methodParameter.getMethod())) :
				forParameter(methodParameter.getParameter()));
	}	
	public static Nullness forField(Field field) {
		if (KotlinDetector.isKotlinType(field.getDeclaringClass())) {
			return KotlinDelegate.forField(field);
		}
		return (hasNullableAnnotation(field) ? Nullness.NULLABLE :
				jSpecifyNullness(field, field.getDeclaringClass(), field.getAnnotatedType()));
	}

	// Check method and parameter level  annotations regardless of the package
	// (including Spring and JSR 305 annotations)
	private static boolean hasNullableAnnotation(AnnotatedElement element) {
		for (Annotation annotation : element.getDeclaredAnnotations()) {
			if ("Nullable".equals(annotation.annotationType().getSimpleName())) {
				return true;
			}
		}
		return false;
	}
	private static Nullness jSpecifyNullness(
			AnnotatedElement annotatedElement, Class<?> declaringClass, AnnotatedType annotatedType) {
		if (annotatedType.getType() instanceof Class<?> clazz && clazz.isPrimitive()) {
			return (clazz != void.class ? Nullness.NON_NULL : Nullness.UNSPECIFIED);
		}
		if (annotatedType.isAnnotationPresent(Nullable.class)) {
			return Nullness.NULLABLE;
		}
		if (annotatedType.isAnnotationPresent(NonNull.class)) {
			return Nullness.NON_NULL;
		}
		Nullness nullness = Nullness.UNSPECIFIED;
		// Package level
		Package declaringPackage = declaringClass.getPackage();
		if (declaringPackage.isAnnotationPresent(NullMarked.class)) {
			nullness = Nullness.NON_NULL;
		}
		// Class level
		if (declaringClass.isAnnotationPresent(NullMarked.class)) {
			nullness = Nullness.NON_NULL;
		}
		else if (declaringClass.isAnnotationPresent(NullUnmarked.class)) {
			nullness = Nullness.UNSPECIFIED;
		}
		// Annotated element level
		if (annotatedElement.isAnnotationPresent(NullMarked.class)) {
			nullness = Nullness.NON_NULL;
		}
		else if (annotatedElement.isAnnotationPresent(NullUnmarked.class)) {
			nullness = Nullness.UNSPECIFIED;
		}
		return nullness;
	}	
	private static class KotlinDelegate {
		public static Nullness forMethodReturnType(Method method) {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
			if (function != null && function.getReturnType().isMarkedNullable()) {
				return Nullness.NULLABLE;
			}
			return Nullness.NON_NULL;
		}
		public static Nullness forParameter(Executable executable, int parameterIndex) {
			KFunction<?> function;
			Predicate<KParameter> predicate;
			if (executable instanceof Method method) {
				function = ReflectJvmMapping.getKotlinFunction(method);
				predicate = p -> KParameter.Kind.VALUE.equals(p.getKind());
			}
			else {
				function = ReflectJvmMapping.getKotlinFunction((Constructor<?>) executable);
				predicate = p -> (KParameter.Kind.VALUE.equals(p.getKind()) ||
						KParameter.Kind.INSTANCE.equals(p.getKind()));
			}
			if (function == null) {
				return Nullness.UNSPECIFIED;
			}
			int i = 0;
			for (KParameter kParameter : function.getParameters()) {
				if (predicate.test(kParameter) && parameterIndex == i++) {
					return (kParameter.getType().isMarkedNullable() ? Nullness.NULLABLE : Nullness.NON_NULL);
				}
			}
			return Nullness.UNSPECIFIED;
		}
		public static Nullness forField(Field field) {
			KProperty<?> property = ReflectJvmMapping.getKotlinProperty(field);
			if (property != null && property.getReturnType().isMarkedNullable()) {
				return Nullness.NULLABLE;
			}
			return Nullness.NON_NULL;
		}
	}

}
