package com.t13max.ioc.util;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;



public abstract class FileCopyUtils {

	public static final int BUFFER_SIZE = StreamUtils.BUFFER_SIZE;

	//---------------------------------------------------------------------
	// Copy methods for java.io.File
	//---------------------------------------------------------------------

	public static int copy(File in, File out) throws IOException {
		com.t13max.ioc.util.Assert.notNull(in, "No input File specified");
		com.t13max.ioc.util.Assert.notNull(out, "No output File specified");
		return copy(Files.newInputStream(in.toPath()), Files.newOutputStream(out.toPath()));
	}

	public static void copy(byte[] in, File out) throws IOException {
		com.t13max.ioc.util.Assert.notNull(in, "No input byte array specified");
		com.t13max.ioc.util.Assert.notNull(out, "No output File specified");
		copy(new ByteArrayInputStream(in), Files.newOutputStream(out.toPath()));
	}

	public static byte[] copyToByteArray(File in) throws IOException {
		com.t13max.ioc.util.Assert.notNull(in, "No input File specified");
		return copyToByteArray(Files.newInputStream(in.toPath()));
	}

	//---------------------------------------------------------------------
	// Copy methods for java.io.InputStream / java.io.OutputStream
	//---------------------------------------------------------------------

	public static int copy(InputStream in, OutputStream out) throws IOException {
		com.t13max.ioc.util.Assert.notNull(in, "No InputStream specified");
		com.t13max.ioc.util.Assert.notNull(out, "No OutputStream specified");
		try (in; out) {
			int count = (int) in.transferTo(out);
			out.flush();
			return count;
		}
	}

	public static void copy(byte[] in, OutputStream out) throws IOException {
		com.t13max.ioc.util.Assert.notNull(in, "No input byte array specified");
		com.t13max.ioc.util.Assert.notNull(out, "No OutputStream specified");
		try {
			out.write(in);
		}
		finally {
			close(out);
		}
	}

	public static byte[] copyToByteArray( InputStream in) throws IOException {
		if (in == null) {
			return new byte[0];
		}
		try (in) {
			return in.readAllBytes();
		}
	}

	//---------------------------------------------------------------------
	// Copy methods for java.io.Reader / java.io.Writer
	//---------------------------------------------------------------------

	public static int copy(Reader in, Writer out) throws IOException {
		com.t13max.ioc.util.Assert.notNull(in, "No Reader specified");
		com.t13max.ioc.util.Assert.notNull(out, "No Writer specified");
		try {
			int charCount = 0;
			char[] buffer = new char[BUFFER_SIZE];
			int charsRead;
			while ((charsRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, charsRead);
				charCount += charsRead;
			}
			out.flush();
			return charCount;
		}
		finally {
			close(in);
			close(out);
		}
	}

	public static void copy(String in, Writer out) throws IOException {
		com.t13max.ioc.util.Assert.notNull(in, "No input String specified");
		Assert.notNull(out, "No Writer specified");
		try {
			out.write(in);
		}
		finally {
			close(out);
		}
	}

	public static String copyToString( Reader in) throws IOException {
		if (in == null) {
			return "";
		}
		StringWriter out = new StringWriter(BUFFER_SIZE);
		copy(in, out);
		return out.toString();
	}

	private static void close(Closeable closeable) {
		try {
			closeable.close();
		}
		catch (IOException ignored) {
		}
	}

}
