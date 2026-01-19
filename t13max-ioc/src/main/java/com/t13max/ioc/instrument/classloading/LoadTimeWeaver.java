package com.t13max.ioc.instrument.classloading;

import java.lang.instrument.ClassFileTransformer;

/**
 * @Author: t13max
 * @Since: 21:43 2026/1/16
 */
public interface LoadTimeWeaver {

    void addTransformer(ClassFileTransformer transformer);

    ClassLoader getInstrumentableClassLoader();

    ClassLoader getThrowawayClassLoader();
}
