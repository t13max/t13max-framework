package com.t13max.ioc.transaction.interceptor;

import com.t13max.ioc.beans.factory.InitializingBean;
import com.t13max.ioc.context.EmbeddedValueResolverAware;
import com.t13max.ioc.util.ClassUtils;
import com.t13max.ioc.util.ObjectUtils;
import com.t13max.ioc.util.PatternMatchUtils;
import com.t13max.ioc.util.StringValueResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @Author: t13max
 * @Since: 20:33 2026/1/16
 */
public class NameMatchTransactionAttributeSource implements TransactionAttributeSource, EmbeddedValueResolverAware, InitializingBean, Serializable {
    protected static final Logger logger = LogManager.getLogger(NameMatchTransactionAttributeSource.class);
    private final Map<String, TransactionAttribute> nameMap = new HashMap<>();

    private  StringValueResolver embeddedValueResolver;

    public void setNameMap(Map<String, TransactionAttribute> nameMap) {
        nameMap.forEach(this::addTransactionalMethod);
    }
    public void setProperties(Properties transactionAttributes) {
        TransactionAttributeEditor tae = new TransactionAttributeEditor();
        Enumeration<?> propNames = transactionAttributes.propertyNames();
        while (propNames.hasMoreElements()) {
            String methodName = (String) propNames.nextElement();
            String value = transactionAttributes.getProperty(methodName);
            tae.setAsText(value);
            TransactionAttribute attr = (TransactionAttribute) tae.getValue();
            addTransactionalMethod(methodName, attr);
        }
    }
    public void addTransactionalMethod(String methodName, TransactionAttribute attr) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding transactional method [" + methodName + "] with attribute [" + attr + "]");
        }
        if (this.embeddedValueResolver != null && attr instanceof DefaultTransactionAttribute dta) {
            dta.resolveAttributeStrings(this.embeddedValueResolver);
        }
        this.nameMap.put(methodName, attr);
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.embeddedValueResolver = resolver;
    }

    @Override
    public void afterPropertiesSet() {
        for (TransactionAttribute attr : this.nameMap.values()) {
            if (attr instanceof DefaultTransactionAttribute dta) {
                dta.resolveAttributeStrings(this.embeddedValueResolver);
            }
        }
    }


    @Override
    public  TransactionAttribute getTransactionAttribute(Method method,  Class<?> targetClass) {
        if (!ClassUtils.isUserLevelMethod(method)) {
            return null;
        }

        // Look for direct name match.
        String methodName = method.getName();
        TransactionAttribute attr = this.nameMap.get(methodName);

        if (attr == null) {
            // Look for most specific name match.
            String bestNameMatch = null;
            for (String mappedName : this.nameMap.keySet()) {
                if (isMatch(methodName, mappedName) &&
                        (bestNameMatch == null || bestNameMatch.length() <= mappedName.length())) {
                    attr = this.nameMap.get(mappedName);
                    bestNameMatch = mappedName;
                }
            }
        }

        return attr;
    }
    protected boolean isMatch(String methodName, String mappedName) {
        return PatternMatchUtils.simpleMatch(mappedName, methodName);
    }


    @Override
    public boolean equals( Object other) {
        return (this == other || (other instanceof NameMatchTransactionAttributeSource otherTas &&
                ObjectUtils.nullSafeEquals(this.nameMap, otherTas.nameMap)));
    }

    @Override
    public int hashCode() {
        return NameMatchTransactionAttributeSource.class.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + this.nameMap;
    }
}
