package com.t13max.ioc.util.xml;

import java.io.BufferedReader;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.t13max.ioc.util.StringUtils;

public class XmlValidationModeDetector {

	
	public static final int VALIDATION_NONE = 0;

	
	public static final int VALIDATION_AUTO = 1;

	
	public static final int VALIDATION_DTD = 2;

	
	public static final int VALIDATION_XSD = 3;


	
	private static final String DOCTYPE = "DOCTYPE";

	
	private static final String START_COMMENT = "<!--";

	
	private static final String END_COMMENT = "-->";


	
	private boolean inComment;


	
	public int detectValidationMode(InputStream inputStream) throws IOException {
		this.inComment = false;

		// Peek into the file to look for DOCTYPE.
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			boolean isDtdValidated = false;
			String content;
			while ((content = reader.readLine()) != null) {
				content = consumeCommentTokens(content);
				if (!StringUtils.hasText(content)) {
					continue;
				}
				if (hasDoctype(content)) {
					isDtdValidated = true;
					break;
				}
				if (hasOpeningTag(content)) {
					// End of meaningful data...
					break;
				}
			}
			return (isDtdValidated ? VALIDATION_DTD : VALIDATION_XSD);
		}
		catch (CharConversionException ex) {
			// Choked on some character encoding...
			// Leave the decision up to the caller.
			return VALIDATION_AUTO;
		}
	}


	
	private boolean hasDoctype(String content) {
		return content.contains(DOCTYPE);
	}

	
	private boolean hasOpeningTag(String content) {
		if (this.inComment) {
			return false;
		}
		int openTagIndex = content.indexOf('<');
		return (openTagIndex > -1 && (content.length() > openTagIndex + 1) &&
				Character.isLetter(content.charAt(openTagIndex + 1)));
	}

	
	private String consumeCommentTokens(String line) {
		int indexOfStartComment = line.indexOf(START_COMMENT);
		if (indexOfStartComment == -1 && !line.contains(END_COMMENT)) {
			return line;
		}

		String result = "";
		String currLine = line;
		if (!this.inComment && (indexOfStartComment >= 0)) {
			result = line.substring(0, indexOfStartComment);
			currLine = line.substring(indexOfStartComment);
		}

		if ((currLine = consume(currLine)) != null) {
			result += consumeCommentTokens(currLine);
		}
		return result;
	}

	
	private  String consume(String line) {
		int index = (this.inComment ? endComment(line) : startComment(line));
		return (index == -1 ? null : line.substring(index));
	}

	
	private int startComment(String line) {
		return commentToken(line, START_COMMENT, true);
	}

	
	private int endComment(String line) {
		return commentToken(line, END_COMMENT, false);
	}

	
	private int commentToken(String line, String token, boolean inCommentIfPresent) {
		int index = line.indexOf(token);
		if (index > - 1) {
			this.inComment = inCommentIfPresent;
		}
		return (index == -1 ? index : index + token.length());
	}

}
