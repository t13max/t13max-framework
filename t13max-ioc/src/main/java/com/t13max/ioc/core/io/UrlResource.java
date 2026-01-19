package com.t13max.ioc.core.io;

import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ResourceUtils;
import com.t13max.ioc.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @Author: t13max
 * @Since: 21:17 2026/1/16
 */
public class UrlResource extends AbstractFileResolvingResource {

    private static final String AUTHORIZATION = "Authorization";

    private final URI uri;
    private final URL url;
    private volatile String cleanedUrl;

    public UrlResource(URL url) {
        Assert.notNull(url, "URL must not be null");
        this.uri = null;
        this.url = url;
    }

    public UrlResource(URI uri) throws MalformedURLException {
        Assert.notNull(uri, "URI must not be null");
        this.uri = uri;
        this.url = uri.toURL();
    }

    public UrlResource(String path) throws MalformedURLException {
        Assert.notNull(path, "Path must not be null");
        String cleanedPath = StringUtils.cleanPath(path);
        URI uri;
        URL url;

        try {
            // Prefer URI construction with toURL conversion (as of 6.1)
            uri = ResourceUtils.toURI(cleanedPath);
            url = uri.toURL();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            uri = null;
            url = ResourceUtils.toURL(path);
        }

        this.uri = uri;
        this.url = url;
        this.cleanedUrl = cleanedPath;
    }

    public UrlResource(String protocol, String location) throws MalformedURLException {
        this(protocol, location, null);
    }

    public UrlResource(String protocol, String location, String fragment) throws MalformedURLException {
        try {
            this.uri = new URI(protocol, location, fragment);
            this.url = this.uri.toURL();
        } catch (URISyntaxException ex) {
            MalformedURLException exToThrow = new MalformedURLException(ex.getMessage());
            exToThrow.initCause(ex);
            throw exToThrow;
        }
    }

    public static UrlResource from(URI uri) throws UncheckedIOException {
        try {
            return new UrlResource(uri);
        } catch (MalformedURLException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static UrlResource from(String path) throws UncheckedIOException {
        try {
            return new UrlResource(path);
        } catch (MalformedURLException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String getCleanedUrl() {
        String cleanedUrl = this.cleanedUrl;
        if (cleanedUrl != null) {
            return cleanedUrl;
        }
        String originalPath = (this.uri != null ? this.uri : this.url).toString();
        cleanedUrl = StringUtils.cleanPath(originalPath);
        this.cleanedUrl = cleanedUrl;
        return cleanedUrl;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        URLConnection con = this.url.openConnection();
        customizeConnection(con);
        try {
            return con.getInputStream();
        } catch (IOException ex) {
            // Close the HTTP connection (if applicable).
            if (con instanceof HttpURLConnection httpCon) {
                httpCon.disconnect();
            }
            throw ex;
        }
    }

    @Override
    protected void customizeConnection(URLConnection con) throws IOException {
        super.customizeConnection(con);
        String userInfo = this.url.getUserInfo();
        if (userInfo != null) {
            String encodedCredentials = Base64.getUrlEncoder().encodeToString(userInfo.getBytes());
            con.setRequestProperty(AUTHORIZATION, "Basic " + encodedCredentials);
        }
    }

    @Override
    public URL getURL() {
        return this.url;
    }

    @Override
    public URI getURI() throws IOException {
        if (this.uri != null) {
            return this.uri;
        } else {
            return super.getURI();
        }
    }

    @Override
    public boolean isFile() {
        if (this.uri != null) {
            return super.isFile(this.uri);
        } else {
            return super.isFile();
        }
    }

    @Override
    public File getFile() throws IOException {
        if (this.uri != null) {
            return super.getFile(this.uri);
        } else {
            return super.getFile();
        }
    }

    @Override
    public Resource createRelative(String relativePath) throws MalformedURLException {
        return new UrlResource(createRelativeURL(relativePath));
    }

    protected URL createRelativeURL(String relativePath) throws MalformedURLException {
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return ResourceUtils.toRelativeURL(this.url, relativePath);
    }

    @Override
    public String getFilename() {
        if (this.uri != null) {
            String path = this.uri.getPath();
            if (path != null) {
                // Prefer URI path: decoded and has standard separators
                return StringUtils.getFilename(this.uri.getPath());
            }
        }
        // Otherwise, process URL path
        String filename = StringUtils.getFilename(StringUtils.cleanPath(this.url.getPath()));
        return (filename != null ? URLDecoder.decode(filename, StandardCharsets.UTF_8) : null);
    }

    @Override
    public String getDescription() {
        return "URL [" + (this.uri != null ? this.uri : this.url) + "]";
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof UrlResource that &&
                getCleanedUrl().equals(that.getCleanedUrl())));
    }

    @Override
    public int hashCode() {
        return getCleanedUrl().hashCode();
    }
}
