package com.t13max.ioc.context.support;

import com.t13max.ioc.context.HierarchicalMessageSource;
import com.t13max.ioc.context.MessageSource;
import com.t13max.ioc.context.MessageSourceResolvable;
import com.t13max.ioc.context.NoSuchMessageException;
import com.t13max.ioc.core.testfixture.nullness.custom.Nullable;

import java.util.Locale;

/**
 * @Author: t13max
 * @Since: 7:44 2026/1/17
 */
public class DelegatingMessageSource extends MessageSourceSupport implements HierarchicalMessageSource {

    private  MessageSource parentMessageSource;


    @Override
    public void setParentMessageSource( MessageSource parent) {
        this.parentMessageSource = parent;
    }

    @Override
    public  MessageSource getParentMessageSource() {
        return this.parentMessageSource;
    }


    @Override
    public  String getMessage(String code, Object  [] args,  String defaultMessage, Locale locale) {
        if (this.parentMessageSource != null) {
            return this.parentMessageSource.getMessage(code, args, defaultMessage, locale);
        }
        else if (defaultMessage != null) {
            return renderDefaultMessage(defaultMessage, args, locale);
        }
        else {
            return null;
        }
    }

    @Override
    public String getMessage(String code, Object  [] args, Locale locale) throws NoSuchMessageException {
        if (this.parentMessageSource != null) {
            return this.parentMessageSource.getMessage(code, args, locale);
        }
        else {
            throw new NoSuchMessageException(code, locale);
        }
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        if (this.parentMessageSource != null) {
            return this.parentMessageSource.getMessage(resolvable, locale);
        }
        else {
            if (resolvable.getDefaultMessage() != null) {
                return renderDefaultMessage(resolvable.getDefaultMessage(), resolvable.getArguments(), locale);
            }
            String[] codes = resolvable.getCodes();
            String code = (codes != null && codes.length > 0 ? codes[0] : "");
            throw new NoSuchMessageException(code, locale);
        }
    }


    @Override
    public String toString() {
        return (this.parentMessageSource != null ? this.parentMessageSource.toString() : "Empty MessageSource");
    }

}
