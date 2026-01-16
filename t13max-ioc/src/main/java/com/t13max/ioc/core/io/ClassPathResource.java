package com.t13max.ioc.core.io;

import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ClassUtils;
import com.t13max.ioc.utils.ObjectUtils;
import com.t13max.ioc.utils.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @Author: t13max
 * @Since: 21:13 2026/1/16
 */
public class ClassPathResource extends AbstractFileResolvingResource {
    private final String path;

    private final String absolutePath;

    private final  ClassLoader classLoader;

    private final  Class<?> clazz;

    public ClassPathResource(String path) {
        this(path, (ClassLoader) null);
    }
    public ClassPathResource(String path,  ClassLoader classLoader) {
        Assert.notNull(path, "Path must not be null");
        String pathToUse = StringUtils.cleanPath(path);
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1);
        }
        this.path = pathToUse;
        this.absolutePath = pathToUse;
        this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
        this.clazz = null;
    }
    public ClassPathResource(String path,  Class<?> clazz) {
        Assert.notNull(path, "Path must not be null");
        this.path = StringUtils.cleanPath(path);

        String absolutePath = this.path;
        if (clazz != null && !absolutePath.startsWith("/")) {
            absolutePath = ClassUtils.classPackageAsResourcePath(clazz) + "/" + absolutePath;
        }
        else if (absolutePath.startsWith("/")) {
            absolutePath = absolutePath.substring(1);
        }
        this.absolutePath = absolutePath;

        this.classLoader = null;
        this.clazz = clazz;
    }

    public final String getPath() {
        return this.absolutePath;
    }
    public final  ClassLoader getClassLoader() {
        return (this.clazz != null ? this.clazz.getClassLoader() : this.classLoader);
    }

    @Override
    public boolean exists() {
        return (resolveURL() != null);
    }
    @Override
    public boolean isReadable() {
        URL url = resolveURL();
        return (url != null && checkReadable(url));
    }
    protected  URL resolveURL() {
        try {
            if (this.clazz != null) {
                return this.clazz.getResource(this.path);
            }
            else if (this.classLoader != null) {
                return this.classLoader.getResource(this.absolutePath);
            }
            else {
                return ClassLoader.getSystemResource(this.absolutePath);
            }
        }
        catch (IllegalArgumentException ex) {
            // Should not happen according to the JDK's contract:
            // see https://github.com/openjdk/jdk/pull/2662
            return null;
        }
    }
    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is;
        if (this.clazz != null) {
            is = this.clazz.getResourceAsStream(this.path);
        }
        else if (this.classLoader != null) {
            is = this.classLoader.getResourceAsStream(this.absolutePath);
        }
        else {
            is = ClassLoader.getSystemResourceAsStream(this.absolutePath);
        }
        if (is == null) {
            throw new FileNotFoundException(getDescription() + " cannot be opened because it does not exist");
        }
        return is;
    }
    @Override
    public URL getURL() throws IOException {
        URL url = resolveURL();
        if (url == null) {
            throw new FileNotFoundException(getDescription() + " cannot be resolved to URL because it does not exist");
        }
        return url;
    }
    @Override
    public Resource createRelative(String relativePath) {
        String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
        return (this.clazz != null ? new ClassPathResource(pathToUse, this.clazz) :
                new ClassPathResource(pathToUse, this.classLoader));
    }
    @Override
    public  String getFilename() {
        return StringUtils.getFilename(this.absolutePath);
    }
    @Override
    public String getDescription() {
        return "class path resource [" + this.absolutePath + "]";
    }

    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof ClassPathResource that &&
                this.absolutePath.equals(that.absolutePath) &&
                ObjectUtils.nullSafeEquals(getClassLoader(), that.getClassLoader())));
    }
    @Override
    public int hashCode() {
        return this.absolutePath.hashCode();
    }
}
