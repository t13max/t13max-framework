package com.t13max.ioc.context.xml;

import com.t13max.ioc.context.support.GenericApplicationContext;
import com.t13max.ioc.core.env.ConfigurableEnvironment;
import com.t13max.ioc.core.io.Resource;

/**
 * xml配置应用上下文
 *
 * @Author: t13max
 * @Since: 22:59 2026/1/14
 */
public class GenericXmlApplicationContext extends GenericApplicationContext {

    private final XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(this);

    public GenericXmlApplicationContext() {
    }

    public GenericXmlApplicationContext(Resource... resources) {
        load(resources);
        refresh();
    }    public GenericXmlApplicationContext(String... resourceLocations) {
        load(resourceLocations);
        refresh();
    }

    public GenericXmlApplicationContext(Class<?> relativeClass, String... resourceNames) {
        load(relativeClass, resourceNames);
        refresh();
    }    public final XmlBeanDefinitionReader getReader() {
        return this.reader;
    }    public void setValidating(boolean validating) {
        this.reader.setValidating(validating);
    }

    @Override
    public void setEnvironment(ConfigurableEnvironment environment) {
        super.setEnvironment(environment);
        this.reader.setEnvironment(getEnvironment());
    }

    //---------------------------------------------------------------------
    // 加载xml文件
    //---------------------------------------------------------------------

    public void load(Resource... resources) {
        this.reader.loadBeanDefinitions(resources);
    }

    public void load(String... resourceLocations) {
        this.reader.loadBeanDefinitions(resourceLocations);
    }

    public void load(Class<?> relativeClass, String... resourceNames) {
        Resource[] resources = new Resource[resourceNames.length];
        for (int i = 0; i < resourceNames.length; i++) {
            resources[i] = new ClassPathResource(resourceNames[i], relativeClass);
        }
        this.load(resources);
    }

}
