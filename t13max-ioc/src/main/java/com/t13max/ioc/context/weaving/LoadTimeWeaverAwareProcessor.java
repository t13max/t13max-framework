package com.t13max.ioc.context.weaving;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.BeanFactory;
import com.t13max.ioc.beans.factory.BeanFactoryAware;
import com.t13max.ioc.beans.factory.config.BeanPostProcessor;
import com.t13max.ioc.context.ConfigurableApplicationContext;
import com.t13max.ioc.instrument.classloading.LoadTimeWeaver;
import com.t13max.ioc.utils.Assert;

/**
 * @Author: t13max
 * @Since: 21:42 2026/1/16
 */
public class LoadTimeWeaverAwareProcessor implements BeanPostProcessor, BeanFactoryAware {

    private  LoadTimeWeaver loadTimeWeaver;

    private  BeanFactory beanFactory;
    
    public LoadTimeWeaverAwareProcessor() {
    }    
    public LoadTimeWeaverAwareProcessor( LoadTimeWeaver loadTimeWeaver) {
        this.loadTimeWeaver = loadTimeWeaver;
    }    
    public LoadTimeWeaverAwareProcessor(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }


    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }


    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof LoadTimeWeaverAware loadTimeWeaverAware) {
            LoadTimeWeaver ltw = this.loadTimeWeaver;
            if (ltw == null) {
                Assert.state(this.beanFactory != null,
                        "BeanFactory required if no LoadTimeWeaver explicitly specified");
                ltw = this.beanFactory.getBean(
                        ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME, LoadTimeWeaver.class);
            }
            loadTimeWeaverAware.setLoadTimeWeaver(ltw);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        return bean;
    }
}
