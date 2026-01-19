package com.t13max.ioc.core.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @Author: t13max
 * @Since: 21:13 2026/1/16
 */
public class AbstractFileResolvingResource extends AbstractResource {

    @Override
    public boolean exists() {
        try {
            URL url = getURL();
            if (ResourceUtils.isFileURL(url)) {
                // Proceed with file system resolution
                return getFile().exists();
            }
            else {
                // Try a URL connection content-length header
                URLConnection con = url.openConnection();
                customizeConnection(con);

                HttpURLConnection httpCon = (con instanceof HttpURLConnection huc ? huc : null);
                if (httpCon != null) {
                    httpCon.setRequestMethod("HEAD");
                    int code = httpCon.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        return true;
                    }
                    else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        return false;
                    }
                    else if (code == HttpURLConnection.HTTP_BAD_METHOD) {
                        con = url.openConnection();
                        customizeConnection(con);
                        if (con instanceof HttpURLConnection newHttpCon) {
                            code = newHttpCon.getResponseCode();
                            if (code == HttpURLConnection.HTTP_OK) {
                                return true;
                            }
                            else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                                return false;
                            }
                            httpCon = newHttpCon;
                        }
                    }
                }

                if (con instanceof JarURLConnection jarCon) {
                    // For JarURLConnection, do not check content-length but rather the
                    // existence of the entry (or the jar root in case of no entryName).
                    // getJarFile() called for enforced presence check of the jar file,
                    // throwing a NoSuchFileException otherwise (turned to false below).
                    JarFile jarFile = jarCon.getJarFile();
                    try {
                        return (jarCon.getEntryName() == null || jarCon.getJarEntry() != null);
                    }
                    finally {
                        if (!jarCon.getUseCaches()) {
                            jarFile.close();
                        }
                    }
                }
                else if (con.getContentLengthLong() > 0) {
                    return true;
                }

                if (httpCon != null) {
                    // No HTTP OK status, and no content-length header: give up
                    httpCon.disconnect();
                    return false;
                }
                else {
                    // Fall back to stream existence: can we open the stream?
                    getInputStream().close();
                    return true;
                }
            }
        }
        catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean isReadable() {
        try {
            return checkReadable(getURL());
        }
        catch (IOException ex) {
            return false;
        }
    }

    boolean checkReadable(URL url) {
        try {
            if (ResourceUtils.isFileURL(url)) {
                // Proceed with file system resolution
                File file = getFile();
                return (file.canRead() && !file.isDirectory());
            }
            else {
                // Try InputStream resolution for jar resources
                URLConnection con = url.openConnection();
                customizeConnection(con);
                if (con instanceof HttpURLConnection httpCon) {
                    httpCon.setRequestMethod("HEAD");
                    int code = httpCon.getResponseCode();
                    if (code == HttpURLConnection.HTTP_BAD_METHOD) {
                        con = url.openConnection();
                        customizeConnection(con);
                        if (!(con instanceof HttpURLConnection newHttpCon)) {
                            return false;
                        }
                        code = newHttpCon.getResponseCode();
                        httpCon = newHttpCon;
                    }
                    if (code != HttpURLConnection.HTTP_OK) {
                        httpCon.disconnect();
                        return false;
                    }
                }
                else if (con instanceof JarURLConnection jarCon) {
                    JarEntry jarEntry = jarCon.getJarEntry();
                    if (jarEntry == null) {
                        return false;
                    }
                    else {
                        return !jarEntry.isDirectory();
                    }
                }
                long contentLength = con.getContentLengthLong();
                if (contentLength > 0) {
                    return true;
                }
                else if (contentLength == 0) {
                    // Empty file or directory -> not considered readable...
                    return false;
                }
                else {
                    // Fall back to stream existence: can we open the stream?
                    getInputStream().close();
                    return true;
                }
            }
        }
        catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean isFile() {
        try {
            URL url = getURL();
            if (url.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                return VfsResourceDelegate.getResource(url).isFile();
            }
            return ResourceUtils.URL_PROTOCOL_FILE.equals(url.getProtocol());
        }
        catch (IOException ex) {
            return false;
        }
    }
    @Override
    public File getFile() throws IOException {
        URL url = getURL();
        if (url.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
            return VfsResourceDelegate.getResource(url).getFile();
        }
        return ResourceUtils.getFile(url, getDescription());
    }
    @Override
    protected File getFileForLastModifiedCheck() throws IOException {
        URL url = getURL();
        if (ResourceUtils.isJarURL(url)) {
            URL actualUrl = ResourceUtils.extractArchiveURL(url);
            if (actualUrl.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                return VfsResourceDelegate.getResource(actualUrl).getFile();
            }
            return ResourceUtils.getFile(actualUrl, "Jar URL");
        }
        else {
            return getFile();
        }
    }
    protected boolean isFile(URI uri) {
        try {
            if (uri.getScheme().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                return VfsResourceDelegate.getResource(uri).isFile();
            }
            return ResourceUtils.URL_PROTOCOL_FILE.equals(uri.getScheme());
        }
        catch (IOException ex) {
            return false;
        }
    }
    protected File getFile(URI uri) throws IOException {
        if (uri.getScheme().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
            return VfsResourceDelegate.getResource(uri).getFile();
        }
        return ResourceUtils.getFile(uri, getDescription());
    }
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        try {
            // Try file system channel
            return FileChannel.open(getFile().toPath(), StandardOpenOption.READ);
        }
        catch (FileNotFoundException | NoSuchFileException ex) {
            // Fall back to InputStream adaptation in superclass
            return super.readableChannel();
        }
    }

    @Override
    public long contentLength() throws IOException {
        URL url = getURL();
        if (ResourceUtils.isFileURL(url)) {
            // Proceed with file system resolution
            File file = getFile();
            long length = file.length();
            if (length == 0L && !file.exists()) {
                throw new FileNotFoundException(getDescription() +
                        " cannot be resolved in the file system for checking its content length");
            }
            return length;
        }
        else {
            // Try a URL connection content-length header
            URLConnection con = url.openConnection();
            customizeConnection(con);
            if (con instanceof HttpURLConnection httpCon) {
                httpCon.setRequestMethod("HEAD");
            }
            long length = con.getContentLengthLong();
            if (length <= 0 && con instanceof HttpURLConnection httpCon &&
                    httpCon.getResponseCode() == HttpURLConnection.HTTP_BAD_METHOD) {
                con = url.openConnection();
                customizeConnection(con);
                length = con.getContentLengthLong();
            }
            return length;
        }
    }

    @Override
    public long lastModified() throws IOException {
        URL url = getURL();
        boolean fileCheck = false;
        if (ResourceUtils.isFileURL(url) || ResourceUtils.isJarURL(url)) {
            // Proceed with file system resolution
            fileCheck = true;
            try {
                File fileToCheck = getFileForLastModifiedCheck();
                long lastModified = fileToCheck.lastModified();
                if (lastModified > 0L || fileToCheck.exists()) {
                    return lastModified;
                }
            }
            catch (FileNotFoundException ex) {
                // Defensively fall back to URL connection check instead
            }
        }
        // Try a URL connection last-modified header
        URLConnection con = url.openConnection();
        customizeConnection(con);
        if (con instanceof HttpURLConnection httpCon) {
            httpCon.setRequestMethod("HEAD");
        }
        long lastModified = con.getLastModified();
        if (lastModified == 0) {
            if (con instanceof HttpURLConnection httpCon &&
                    httpCon.getResponseCode() == HttpURLConnection.HTTP_BAD_METHOD) {
                con = url.openConnection();
                customizeConnection(con);
                lastModified = con.getLastModified();
            }
            if (fileCheck && con.getContentLengthLong() <= 0) {
                throw new FileNotFoundException(getDescription() +
                        " cannot be resolved in the file system for checking its last-modified timestamp");
            }
        }
        return lastModified;
    }
    protected void customizeConnection(URLConnection con) throws IOException {
        ResourceUtils.useCachesIfNecessary(con);
        if (con instanceof HttpURLConnection httpCon) {
            customizeConnection(httpCon);
        }
    }
    protected void customizeConnection(HttpURLConnection con) throws IOException {
    }

    private static class VfsResourceDelegate {

        public static Resource getResource(URL url) throws IOException {
            return new VfsResource(VfsUtils.getRoot(url));
        }

        public static Resource getResource(URI uri) throws IOException {
            return new VfsResource(VfsUtils.getRoot(uri));
        }
    }

}
