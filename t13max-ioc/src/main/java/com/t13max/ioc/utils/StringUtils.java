package com.t13max.ioc.utils;

import com.t13max.ioc.lang.Contract;

import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public abstract class StringUtils {

	private static final String[] EMPTY_STRING_ARRAY = {};

	private static final String FOLDER_SEPARATOR = "/";

	private static final char FOLDER_SEPARATOR_CHAR = '/';

	private static final String WINDOWS_FOLDER_SEPARATOR = "\\";

	private static final char WINDOWS_FOLDER_SEPARATOR_CHAR = '\\';

	private static final String DOUBLE_BACKSLASHES = "\\\\";

	private static final String TOP_PATH = "..";

	private static final String CURRENT_PATH = ".";

	private static final char DOT_CHAR = '.';

	private static final int DEFAULT_TRUNCATION_THRESHOLD = 100;

	private static final String TRUNCATION_SUFFIX = " (truncated)...";


	//---------------------------------------------------------------------
	// General convenience methods for working with Strings
	//---------------------------------------------------------------------
	
	@Deprecated(since = "5.3")
	@Contract("null -> true")
	public static boolean isEmpty( Object str) {
		return (str == null || "".equals(str));
	}
	
	@Contract("null -> false")
	public static boolean hasLength( CharSequence str) {
		return (str != null && !str.isEmpty());  // as of JDK 15
	}
	
	@Contract("null -> false")
	public static boolean hasLength( String str) {
		return (str != null && !str.isEmpty());
	}
	
	@Contract("null -> false")
	public static boolean hasText( CharSequence str) {
		if (str == null) {
			return false;
		}

		int strLen = str.length();
		if (strLen == 0) {
			return false;
		}

		for (int i = 0; i < strLen; i++) {
			if (!Character.isWhitespace(str.charAt(i))) {
				return true;
			}
		}
		return false;
	}
	
	@Contract("null -> false")
	public static boolean hasText( String str) {
		return (str != null && !str.isBlank());
	}
	
	@Contract("null -> false")
	public static boolean containsWhitespace( CharSequence str) {
		if (!hasLength(str)) {
			return false;
		}

		int strLen = str.length();
		for (int i = 0; i < strLen; i++) {
			if (Character.isWhitespace(str.charAt(i))) {
				return true;
			}
		}
		return false;
	}
	
	@Contract("null -> false")
	public static boolean containsWhitespace( String str) {
		return containsWhitespace((CharSequence) str);
	}
	
	@Deprecated(since = "6.0")
	public static String trimWhitespace(String str) {
		if (!hasLength(str)) {
			return str;
		}

		return str.strip();
	}
	
	public static CharSequence trimAllWhitespace(CharSequence str) {
		if (!hasLength(str)) {
			return str;
		}

		int len = str.length();
		StringBuilder sb = new StringBuilder(str.length());
		for (int i = 0; i < len; i++) {
			char c = str.charAt(i);
			if (!Character.isWhitespace(c)) {
				sb.append(c);
			}
		}
		return sb;
	}
	
	public static String trimAllWhitespace(String str) {
		if (!hasLength(str)) {
			return str;
		}

		return trimAllWhitespace((CharSequence) str).toString();
	}
	
	@Deprecated(since = "6.0")
	public static String trimLeadingWhitespace(String str) {
		if (!hasLength(str)) {
			return str;
		}

		return str.stripLeading();
	}
	
	@Deprecated(since = "6.0")
	public static String trimTrailingWhitespace(String str) {
		if (!hasLength(str)) {
			return str;
		}

		return str.stripTrailing();
	}
	
	public static String trimLeadingCharacter(String str, char leadingCharacter) {
		if (!hasLength(str)) {
			return str;
		}

		int beginIdx = 0;
		while (beginIdx < str.length() && leadingCharacter == str.charAt(beginIdx)) {
			beginIdx++;
		}
		return str.substring(beginIdx);
	}
	
	public static String trimTrailingCharacter(String str, char trailingCharacter) {
		if (!hasLength(str)) {
			return str;
		}

		int endIdx = str.length() - 1;
		while (endIdx >= 0 && trailingCharacter == str.charAt(endIdx)) {
			endIdx--;
		}
		return str.substring(0, endIdx + 1);
	}
	
	@Contract("null, _ -> false")
	public static boolean matchesCharacter( String str, char singleCharacter) {
		return (str != null && str.length() == 1 && str.charAt(0) == singleCharacter);
	}
	
	@Contract("null, _ -> false; _, null -> false")
	public static boolean startsWithIgnoreCase( String str,  String prefix) {
		return (str != null && prefix != null && str.length() >= prefix.length() &&
				str.regionMatches(true, 0, prefix, 0, prefix.length()));
	}
	
	@Contract("null, _ -> false; _, null -> false")
	public static boolean endsWithIgnoreCase( String str,  String suffix) {
		return (str != null && suffix != null && str.length() >= suffix.length() &&
				str.regionMatches(true, str.length() - suffix.length(), suffix, 0, suffix.length()));
	}
	
	public static boolean substringMatch(CharSequence str, int index, CharSequence substring) {
		if (index + substring.length() > str.length()) {
			return false;
		}
		for (int i = 0; i < substring.length(); i++) {
			if (str.charAt(index + i) != substring.charAt(i)) {
				return false;
			}
		}
		return true;
	}
	
	public static int countOccurrencesOf(String str, String sub) {
		if (!hasLength(str) || !hasLength(sub)) {
			return 0;
		}

		int count = 0;
		int pos = 0;
		int idx;
		while ((idx = str.indexOf(sub, pos)) != -1) {
			++count;
			pos = idx + sub.length();
		}
		return count;
	}
	
	public static String replace(String inString, String oldPattern,  String newPattern) {
		if (!hasLength(inString) || !hasLength(oldPattern) || newPattern == null) {
			return inString;
		}
		int index = inString.indexOf(oldPattern);
		if (index == -1) {
			// no occurrence -> can return input as-is
			return inString;
		}

		int capacity = inString.length();
		if (newPattern.length() > oldPattern.length()) {
			capacity += 16;
		}
		StringBuilder sb = new StringBuilder(capacity);

		int pos = 0;  // our position in the old string
		int patLen = oldPattern.length();
		while (index >= 0) {
			sb.append(inString, pos, index);
			sb.append(newPattern);
			pos = index + patLen;
			index = inString.indexOf(oldPattern, pos);
		}

		// append any characters to the right of a match
		sb.append(inString, pos, inString.length());
		return sb.toString();
	}
	
	public static String delete(String inString, String pattern) {
		return replace(inString, pattern, "");
	}
	
	public static String deleteAny(String inString,  String charsToDelete) {
		if (!hasLength(inString) || !hasLength(charsToDelete)) {
			return inString;
		}

		int lastCharIndex = 0;
		char[] result = new char[inString.length()];
		for (int i = 0; i < inString.length(); i++) {
			char c = inString.charAt(i);
			if (charsToDelete.indexOf(c) == -1) {
				result[lastCharIndex++] = c;
			}
		}
		if (lastCharIndex == inString.length()) {
			return inString;
		}
		return new String(result, 0, lastCharIndex);
	}

	//---------------------------------------------------------------------
	// Convenience methods for working with formatted Strings
	//---------------------------------------------------------------------
	
	@Contract("null -> null; !null -> !null")
	public static  String quote( String str) {
		return (str != null ? "'" + str + "'" : null);
	}
	
	@Contract("null -> null; !null -> !null")
	public static  Object quoteIfString( Object obj) {
		return (obj instanceof String str ? quote(str) : obj);
	}
	
	public static String unqualify(String qualifiedName) {
		return unqualify(qualifiedName, DOT_CHAR);
	}
	
	public static String unqualify(String qualifiedName, char separator) {
		return qualifiedName.substring(qualifiedName.lastIndexOf(separator) + 1);
	}
	
	public static String capitalize(String str) {
		return changeFirstCharacterCase(str, true);
	}
	
	public static String uncapitalize(String str) {
		return changeFirstCharacterCase(str, false);
	}
	
	public static String uncapitalizeAsProperty(String str) {
		if (!hasLength(str) || (str.length() > 1 && Character.isUpperCase(str.charAt(0)) &&
				Character.isUpperCase(str.charAt(1)))) {
			return str;
		}
		return changeFirstCharacterCase(str, false);
	}

	private static String changeFirstCharacterCase(String str, boolean capitalize) {
		if (!hasLength(str)) {
			return str;
		}

		char baseChar = str.charAt(0);
		char updatedChar;
		if (capitalize) {
			updatedChar = Character.toUpperCase(baseChar);
		}
		else {
			updatedChar = Character.toLowerCase(baseChar);
		}
		if (baseChar == updatedChar) {
			return str;
		}

		char[] chars = str.toCharArray();
		chars[0] = updatedChar;
		return new String(chars);
	}
	
	@Contract("null -> null; !null -> !null")
	public static  String getFilename( String path) {
		if (path == null) {
			return null;
		}

		int separatorIndex = path.lastIndexOf(FOLDER_SEPARATOR_CHAR);
		return (separatorIndex != -1 ? path.substring(separatorIndex + 1) : path);
	}
	
	@Contract("null -> null")
	public static  String getFilenameExtension( String path) {
		if (path == null) {
			return null;
		}

		int extIndex = path.lastIndexOf(DOT_CHAR);
		if (extIndex == -1) {
			return null;
		}

		int folderIndex = path.lastIndexOf(FOLDER_SEPARATOR_CHAR);
		if (folderIndex > extIndex) {
			return null;
		}

		return path.substring(extIndex + 1);
	}
	
	public static String stripFilenameExtension(String path) {
		int extIndex = path.lastIndexOf(DOT_CHAR);
		if (extIndex == -1) {
			return path;
		}

		int folderIndex = path.lastIndexOf(FOLDER_SEPARATOR_CHAR);
		if (folderIndex > extIndex) {
			return path;
		}

		return path.substring(0, extIndex);
	}
	
	public static String applyRelativePath(String path, String relativePath) {
		int separatorIndex = path.lastIndexOf(FOLDER_SEPARATOR_CHAR);
		if (separatorIndex != -1) {
			String newPath = path.substring(0, separatorIndex);
			if (!relativePath.startsWith(FOLDER_SEPARATOR)) {
				newPath += FOLDER_SEPARATOR_CHAR;
			}
			return newPath + relativePath;
		}
		else {
			return relativePath;
		}
	}
	
	public static String cleanPath(String path) {
		if (!hasLength(path)) {
			return path;
		}

		String normalizedPath;
		// Optimize when there is no backslash
		if (path.indexOf(WINDOWS_FOLDER_SEPARATOR_CHAR) != -1) {
			normalizedPath = replace(path, DOUBLE_BACKSLASHES, FOLDER_SEPARATOR);
			normalizedPath = replace(normalizedPath, WINDOWS_FOLDER_SEPARATOR, FOLDER_SEPARATOR);
		}
		else {
			normalizedPath = path;
		}
		String pathToUse = normalizedPath;

		// Shortcut if there is no work to do
		if (pathToUse.indexOf(DOT_CHAR) == -1) {
			return pathToUse;
		}

		// Strip prefix from path to analyze, to not treat it as part of the
		// first path element. This is necessary to correctly parse paths like
		// "file:core/../core/io/Resource.class", where the ".." should just
		// strip the first "core" directory while keeping the "file:" prefix.
		int prefixIndex = pathToUse.indexOf(':');
		String prefix = "";
		if (prefixIndex != -1) {
			prefix = pathToUse.substring(0, prefixIndex + 1);
			if (prefix.contains(FOLDER_SEPARATOR)) {
				prefix = "";
			}
			else {
				pathToUse = pathToUse.substring(prefixIndex + 1);
			}
		}
		if (pathToUse.startsWith(FOLDER_SEPARATOR)) {
			prefix = prefix + FOLDER_SEPARATOR;
			pathToUse = pathToUse.substring(1);
		}

		String[] pathArray = delimitedListToStringArray(pathToUse, FOLDER_SEPARATOR);
		// we never require more elements than pathArray and in the common case the same number
		Deque<String> pathElements = new ArrayDeque<>(pathArray.length);
		int tops = 0;

		for (int i = pathArray.length - 1; i >= 0; i--) {
			String element = pathArray[i];
			if (CURRENT_PATH.equals(element)) {
				// Points to current directory - drop it.
			}
			else if (TOP_PATH.equals(element)) {
				// Registering top path found.
				tops++;
			}
			else {
				if (tops > 0) {
					// Merging path element with element corresponding to top path.
					tops--;
				}
				else {
					// Normal path element found.
					pathElements.addFirst(element);
				}
			}
		}

		// All path elements stayed the same - shortcut
		if (pathArray.length == pathElements.size()) {
			return normalizedPath;
		}
		// Remaining top paths need to be retained.
		for (int i = 0; i < tops; i++) {
			pathElements.addFirst(TOP_PATH);
		}
		// If nothing else left, at least explicitly point to current path.
		if (pathElements.size() == 1 && pathElements.getLast().isEmpty() && !prefix.endsWith(FOLDER_SEPARATOR)) {
			pathElements.addFirst(CURRENT_PATH);
		}

		String joined = collectionToDelimitedString(pathElements, FOLDER_SEPARATOR);
		// Avoid String concatenation with empty prefix
		return (prefix.isEmpty() ? joined : prefix + joined);
	}
	
	public static boolean pathEquals(String path1, String path2) {
		return cleanPath(path1).equals(cleanPath(path2));
	}
	
	public static String uriDecode(String source, Charset charset) {
		int length = source.length();
		int firstPercentIndex = source.indexOf('%');
		if (length == 0 || firstPercentIndex < 0) {
			return source;
		}

		StringBuilder output = new StringBuilder(length);
		output.append(source, 0, firstPercentIndex);
		byte[] bytes = null;
		int i = firstPercentIndex;
		while (i < length) {
			char ch = source.charAt(i);
			if (ch == '%') {
				try {
					if (bytes == null) {
						bytes = new byte[(length - i) / 3];
					}

					int pos = 0;
					while (i + 2 < length && ch == '%') {
						bytes[pos++] = (byte) HexFormat.fromHexDigits(source, i + 1, i + 3);
						i += 3;
						if (i < length) {
							ch = source.charAt(i);
						}
					}

					if (i < length && ch == '%') {
						throw new IllegalArgumentException("Incomplete trailing escape (%) pattern");
					}

					output.append(new String(bytes, 0, pos, charset));
				}
				catch (NumberFormatException ex) {
					throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
				}
			}
			else {
				output.append(ch);
				i++;
			}
		}
		return output.toString();
	}
	
	public static  Locale parseLocale(String localeValue) {
		if (!localeValue.contains("_") && !localeValue.contains(" ")) {
			validateLocalePart(localeValue);
			Locale resolved = Locale.forLanguageTag(localeValue);
			if (!resolved.getLanguage().isEmpty()) {
				return resolved;
			}
		}
		return parseLocaleString(localeValue);
	}
	
	@SuppressWarnings("deprecation")  // for Locale constructors on JDK 19
	public static  Locale parseLocaleString(String localeString) {
		if (localeString.isEmpty()) {
			return null;
		}

		String delimiter = "_";
		if (!localeString.contains("_") && localeString.contains(" ")) {
			delimiter = " ";
		}

		String[] tokens = localeString.split(delimiter, -1);
		if (tokens.length == 1) {
			String language = tokens[0];
			validateLocalePart(language);
			return new Locale(language);
		}
		else if (tokens.length == 2) {
			String language = tokens[0];
			validateLocalePart(language);
			String country = tokens[1];
			validateLocalePart(country);
			return new Locale(language, country);
		}
		else if (tokens.length > 2) {
			String language = tokens[0];
			validateLocalePart(language);
			String country = tokens[1];
			validateLocalePart(country);
			String variant = Arrays.stream(tokens).skip(2).collect(Collectors.joining(delimiter));
			return new Locale(language, country, variant);
		}

		throw new IllegalArgumentException("Invalid locale format: '" + localeString + "'");
	}

	private static void validateLocalePart(String localePart) {
		for (int i = 0; i < localePart.length(); i++) {
			char ch = localePart.charAt(i);
			if (ch != ' ' && ch != '_' && ch != '-' && ch != '#' && !Character.isLetterOrDigit(ch)) {
				throw new IllegalArgumentException(
						"Locale part \"" + localePart + "\" contains invalid characters");
			}
		}
	}
	
	public static TimeZone parseTimeZoneString(String timeZoneString) {
		TimeZone timeZone = TimeZone.getTimeZone(timeZoneString);
		if ("GMT".equals(timeZone.getID()) && !timeZoneString.startsWith("GMT")) {
			// We don't want that GMT fallback...
			throw new IllegalArgumentException("Invalid time zone specification '" + timeZoneString + "'");
		}
		return timeZone;
	}


	//---------------------------------------------------------------------
	// Convenience methods for working with String arrays
	//---------------------------------------------------------------------
	
	public static String[] toStringArray( Collection<String> collection) {
		return (!CollectionUtils.isEmpty(collection) ? collection.toArray(EMPTY_STRING_ARRAY) : EMPTY_STRING_ARRAY);
	}
	
	public static String[] toStringArray( Enumeration<String> enumeration) {
		return (enumeration != null ? toStringArray(Collections.list(enumeration)) : EMPTY_STRING_ARRAY);
	}
	
	public static String[] addStringToArray(String  [] array, String str) {
		if (ObjectUtils.isEmpty(array)) {
			return new String[] {str};
		}

		String[] newArr = new String[array.length + 1];
		System.arraycopy(array, 0, newArr, 0, array.length);
		newArr[array.length] = str;
		return newArr;
	}
	
	@Contract("null, _ -> param2; _, null -> param1")
	public static String  [] concatenateStringArrays(String  [] array1, String  [] array2) {
		if (ObjectUtils.isEmpty(array1)) {
			return array2;
		}
		if (ObjectUtils.isEmpty(array2)) {
			return array1;
		}

		String[] newArr = new String[array1.length + array2.length];
		System.arraycopy(array1, 0, newArr, 0, array1.length);
		System.arraycopy(array2, 0, newArr, array1.length, array2.length);
		return newArr;
	}
	
	public static String[] sortStringArray(String[] array) {
		if (ObjectUtils.isEmpty(array)) {
			return array;
		}

		Arrays.sort(array);
		return array;
	}
	
	public static  String[] trimArrayElements( String[] array) {
		if (ObjectUtils.isEmpty(array)) {
			return array;
		}

		 String[] result = new String[array.length];
		for (int i = 0; i < array.length; i++) {
			String element = array[i];
			result[i] = (element != null ? element.trim() : null);
		}
		return result;
	}
	
	public static String[] removeDuplicateStrings(String[] array) {
		if (ObjectUtils.isEmpty(array)) {
			return array;
		}

		Set<String> set = new LinkedHashSet<>(Arrays.asList(array));
		return toStringArray(set);
	}
	
	@Contract("null, _ -> null; _, null -> null")
	public static String  [] split( String toSplit,  String delimiter) {
		if (!hasLength(toSplit) || !hasLength(delimiter)) {
			return null;
		}
		int offset = toSplit.indexOf(delimiter);
		if (offset < 0) {
			return null;
		}

		String beforeDelimiter = toSplit.substring(0, offset);
		String afterDelimiter = toSplit.substring(offset + delimiter.length());
		return new String[] {beforeDelimiter, afterDelimiter};
	}
	
	public static  Properties splitArrayElementsIntoProperties(String[] array, String delimiter) {
		return splitArrayElementsIntoProperties(array, delimiter, null);
	}
	
	@Contract("null, _, _ -> null")
	public static  Properties splitArrayElementsIntoProperties(
			String  [] array, String delimiter,  String charsToDelete) {

		if (ObjectUtils.isEmpty(array)) {
			return null;
		}

		Properties result = new Properties();
		for (String element : array) {
			if (charsToDelete != null) {
				element = deleteAny(element, charsToDelete);
			}
			String[] splittedElement = split(element, delimiter);
			if (splittedElement == null) {
				continue;
			}
			result.setProperty(splittedElement[0].trim(), splittedElement[1].trim());
		}
		return result;
	}
	
	public static String[] tokenizeToStringArray( String str, String delimiters) {
		return tokenizeToStringArray(str, delimiters, true, true);
	}
	
	public static String[] tokenizeToStringArray(
			 String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {

		if (str == null) {
			return EMPTY_STRING_ARRAY;
		}

		StringTokenizer st = new StringTokenizer(str, delimiters);
		List<String> tokens = new ArrayList<>();
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (trimTokens) {
				token = token.trim();
			}
			if (!ignoreEmptyTokens || !token.isEmpty()) {
				tokens.add(token);
			}
		}
		return toStringArray(tokens);
	}
	
	public static String[] delimitedListToStringArray( String str,  String delimiter) {
		return delimitedListToStringArray(str, delimiter, null);
	}
	
	public static String[] delimitedListToStringArray(
			 String str,  String delimiter,  String charsToDelete) {

		if (str == null) {
			return EMPTY_STRING_ARRAY;
		}
		if (delimiter == null) {
			return new String[] {str};
		}

		List<String> result = new ArrayList<>();
		if (delimiter.isEmpty()) {
			for (int i = 0; i < str.length(); i++) {
				result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
			}
		}
		else {
			int pos = 0;
			int delPos;
			while ((delPos = str.indexOf(delimiter, pos)) != -1) {
				result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
				pos = delPos + delimiter.length();
			}
			if (!str.isEmpty() && pos <= str.length()) {
				// Add rest of String, but not in case of empty input.
				result.add(deleteAny(str.substring(pos), charsToDelete));
			}
		}
		return toStringArray(result);
	}
	
	public static String[] commaDelimitedListToStringArray( String str) {
		return delimitedListToStringArray(str, ",");
	}
	
	public static Set<String> commaDelimitedListToSet( String str) {
		String[] tokens = commaDelimitedListToStringArray(str);
		return new LinkedHashSet<>(Arrays.asList(tokens));
	}
	
	public static String collectionToDelimitedString(
			 Collection<?> coll, String delim, String prefix, String suffix) {

		if (CollectionUtils.isEmpty(coll)) {
			return "";
		}

		int totalLength = coll.size() * (prefix.length() + suffix.length()) + (coll.size() - 1) * delim.length();
		for (Object element : coll) {
			totalLength += String.valueOf(element).length();
		}

		StringBuilder sb = new StringBuilder(totalLength);
		Iterator<?> it = coll.iterator();
		while (it.hasNext()) {
			sb.append(prefix).append(it.next()).append(suffix);
			if (it.hasNext()) {
				sb.append(delim);
			}
		}
		return sb.toString();
	}
	
	public static String collectionToDelimitedString( Collection<?> coll, String delim) {
		return collectionToDelimitedString(coll, delim, "", "");
	}
	
	public static String collectionToCommaDelimitedString( Collection<?> coll) {
		return collectionToDelimitedString(coll, ",");
	}
	
	public static String arrayToDelimitedString( Object  [] arr, String delim) {
		if (ObjectUtils.isEmpty(arr)) {
			return "";
		}
		if (arr.length == 1) {
			return ObjectUtils.nullSafeToString(arr[0]);
		}

		StringJoiner sj = new StringJoiner(delim);
		for (Object elem : arr) {
			sj.add(String.valueOf(elem));
		}
		return sj.toString();
	}
	
	public static String arrayToCommaDelimitedString( Object  [] arr) {
		return arrayToDelimitedString(arr, ",");
	}
	
	public static String truncate(CharSequence charSequence) {
		return truncate(charSequence, DEFAULT_TRUNCATION_THRESHOLD);
	}
	
	public static String truncate(CharSequence charSequence, int threshold) {
		Assert.isTrue(threshold > 0,
				() -> "Truncation threshold must be a positive number: " + threshold);
		if (charSequence.length() > threshold) {
			return charSequence.subSequence(0, threshold) + TRUNCATION_SUFFIX;
		}
		return charSequence.toString();
	}

}
