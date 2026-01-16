package com.t13max.ioc.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

/**
 * @Author: t13max
 * @Since: 21:31 2026/1/16
 */
public class SpringProperties {

    private static final String PROPERTIES_RESOURCE_LOCATION = "spring.properties";

    private static final Properties localProperties = new Properties();


    static {
        try {
            ClassLoader cl = SpringProperties.class.getClassLoader();
            URL url = (cl != null ? cl.getResource(PROPERTIES_RESOURCE_LOCATION) :
                    ClassLoader.getSystemResource(PROPERTIES_RESOURCE_LOCATION));
            if (url != null) {
                try (InputStream is = url.openStream()) {
                    localProperties.load(is);
                }
            }
        }
        catch (IOException ex) {
            System.err.println("Could not load 'spring.properties' file from local classpath: " + ex);
        }
    }


    private SpringProperties() {
    }
    
    public static void setProperty(String key,  String value) {
        if (value != null) {
            localProperties.setProperty(key, value);
        }
        else {
            localProperties.remove(key);
        }
    }    
    public static  String getProperty(String key) {
        String value = localProperties.getProperty(key);
        if (value == null) {
            try {
                value = System.getProperty(key);
            }
            catch (Throwable ex) {
                System.err.println("Could not retrieve system property '" + key + "': " + ex);
            }
        }
        return value;
    }    
    public static void setFlag(String key) {
        localProperties.setProperty(key, Boolean.TRUE.toString());
    }    
    public static void setFlag(String key, boolean value) {
        localProperties.setProperty(key, Boolean.toString(value));
    }    
    public static boolean getFlag(String key) {
        return Boolean.parseBoolean(getProperty(key));
    }    
    public static  Boolean checkFlag(String key) {
        String flag = getProperty(key);
        return (flag != null ? Boolean.valueOf(flag) : null);
    }
}
