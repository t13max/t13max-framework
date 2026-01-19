package com.t13max.ioc.util;



import com.t13max.ioc.lang.Contract;

public abstract class PatternMatchUtils {	
	@Contract("null, _ -> false; _, null -> false")
	public static boolean simpleMatch( String pattern,  String str) {
		return simpleMatch(pattern, str, false);
	}	
	@Contract("null, _ -> false; _, null -> false")
	public static boolean simpleMatchIgnoreCase( String pattern,  String str) {
		return simpleMatch(pattern, str, true);
	}
	private static boolean simpleMatch( String pattern,  String str, boolean ignoreCase) {
		if (pattern == null || str == null) {
			return false;
		}
		int firstIndex = pattern.indexOf('*');
		if (firstIndex == -1) {
			return (ignoreCase ? pattern.equalsIgnoreCase(str) : pattern.equals(str));
		}
		if (firstIndex == 0) {
			if (pattern.length() == 1) {
				return true;
			}
			int nextIndex = pattern.indexOf('*', 1);
			if (nextIndex == -1) {
				String part = pattern.substring(1);
				return (ignoreCase ? com.t13max.ioc.util.StringUtils.endsWithIgnoreCase(str, part) : str.endsWith(part));
			}
			String part = pattern.substring(1, nextIndex);
			if (part.isEmpty()) {
				return simpleMatch(pattern.substring(nextIndex), str, ignoreCase);
			}
			int partIndex = indexOf(str, part, 0, ignoreCase);
			while (partIndex != -1) {
				if (simpleMatch(pattern.substring(nextIndex), str.substring(partIndex + part.length()), ignoreCase)) {
					return true;
				}
				partIndex = indexOf(str, part, partIndex + 1, ignoreCase);
			}
			return false;
		}
		return (str.length() >= firstIndex &&
				checkStartsWith(pattern, str, firstIndex, ignoreCase) &&
				simpleMatch(pattern.substring(firstIndex), str.substring(firstIndex), ignoreCase));
	}
	private static boolean checkStartsWith(String pattern, String str, int index, boolean ignoreCase) {
		String part = str.substring(0, index);
		return (ignoreCase ? StringUtils.startsWithIgnoreCase(pattern, part) : pattern.startsWith(part));
	}
	private static int indexOf(String str, String otherStr, int startIndex, boolean ignoreCase) {
		if (!ignoreCase) {
			return str.indexOf(otherStr, startIndex);
		}
		for (int i = startIndex; i <= (str.length() - otherStr.length()); i++) {
			if (str.regionMatches(true, i, otherStr, 0, otherStr.length())) {
				return i;
			}
		}
		return -1;
	}	
	@Contract("null, _ -> false; _, null -> false")
	public static boolean simpleMatch(String  [] patterns,  String str) {
		if (patterns != null) {
			for (String pattern : patterns) {
				if (simpleMatch(pattern, str)) {
					return true;
				}
			}
		}
		return false;
	}	
	@Contract("null, _ -> false; _, null -> false")
	public static boolean simpleMatchIgnoreCase(String  [] patterns,  String str) {
		if (patterns != null) {
			for (String pattern : patterns) {
				if (simpleMatch(pattern, str, true)) {
					return true;
				}
			}
		}
		return false;
	}

}
