package com.t13max.ioc.context;

import com.t13max.ioc.beans.factory.Aware;

/**
 * @Author: t13max
 * @Since: 21:38 2026/1/16
 */
public interface MessageSourceAware extends Aware {

    void setMessageSource(MessageSource messageSource);
}
