package com.t13max.ioc.core.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: t13max
 * @Since: 21:23 2026/1/15
 */
public interface InputStreamSource {

    InputStream getInputStream() throws IOException;
}
