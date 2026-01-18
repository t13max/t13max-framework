package com.t13max.ioc.core.io.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import com.t13max.ioc.core.io.InputStreamSource;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.ObjectUtils;

public class EncodedResource implements InputStreamSource {

    private final Resource resource;

    private final String encoding;

    private final Charset charset;

    public EncodedResource(Resource resource) {
        this(resource, null, null);
    }

    public EncodedResource(Resource resource, String encoding) {
        this(resource, encoding, null);
    }

    public EncodedResource(Resource resource, Charset charset) {
        this(resource, null, charset);
    }

    private EncodedResource(Resource resource, String encoding, Charset charset) {
        super();
        Assert.notNull(resource, "Resource must not be null");
        this.resource = resource;
        this.encoding = encoding;
        this.charset = charset;
    }

    public final Resource getResource() {
        return this.resource;
    }

    public final String getEncoding() {
        return this.encoding;
    }

    public final Charset getCharset() {
        return this.charset;
    }

    public boolean requiresReader() {
        return (this.encoding != null || this.charset != null);
    }

    public Reader getReader() throws IOException {
        if (this.charset != null) {
            return new InputStreamReader(this.resource.getInputStream(), this.charset);
        } else if (this.encoding != null) {
            return new InputStreamReader(this.resource.getInputStream(), this.encoding);
        } else {
            return new InputStreamReader(this.resource.getInputStream());
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.resource.getInputStream();
    }

    public String getContentAsString() throws IOException {
        Charset charset;
        if (this.charset != null) {
            charset = this.charset;
        } else if (this.encoding != null) {
            charset = Charset.forName(this.encoding);
        } else {
            charset = Charset.defaultCharset();
        }
        return this.resource.getContentAsString(charset);
    }


    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof EncodedResource that &&
                this.resource.equals(that.resource) &&
                ObjectUtils.nullSafeEquals(this.charset, that.charset) &&
                ObjectUtils.nullSafeEquals(this.encoding, that.encoding)));
    }

    @Override
    public int hashCode() {
        return this.resource.hashCode();
    }

    @Override
    public String toString() {
        return this.resource.toString();
    }

}
