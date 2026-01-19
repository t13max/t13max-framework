package com.t13max.ioc.core;

/**
 * @Author: t13max
 * @Since: 21:42 2026/1/16
 */
public abstract class NativeDetector {

    private static final String imageCode = System.getProperty("org.graalvm.nativeimage.imagecode");

    private static final boolean inNativeImage = (imageCode != null);

    public static boolean inNativeImage() {
        return inNativeImage;
    }

    public static boolean inNativeImage(Context... contexts) {
        for (Context context: contexts) {
            if (context.key.equals(imageCode)) {
                return true;
            }
        }
        return false;
    }

    public enum Context {

        BUILD("buildtime"),

        RUN("runtime");

        private final String key;

        Context(final String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }
    }

}
