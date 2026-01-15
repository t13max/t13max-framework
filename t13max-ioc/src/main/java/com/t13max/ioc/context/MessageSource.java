package com.t13max.ioc.context;

import java.util.Locale;

/**
 * @Author: t13max
 * @Since: 21:41 2026/1/15
 */
public interface MessageSource {

    String getMessage(String code, Object[] args, String defaultMessage, Locale locale);

    String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException;

    String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException;
}
