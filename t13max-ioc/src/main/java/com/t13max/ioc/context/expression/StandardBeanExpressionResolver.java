package com.t13max.ioc.context.expression;

import com.t13max.ioc.beans.BeansException;
import com.t13max.ioc.beans.factory.config.BeanExpressionContext;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.StringUtils;

import java.beans.Expression;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author t13max
 * @since 11:07 2026/1/16
 */
public class StandardBeanExpressionResolver implements BeanExpressionResolver {

    public static final String MAX_SPEL_EXPRESSION_LENGTH_PROPERTY_NAME = "spring.context.expression.maxLength";

    public static final String DEFAULT_EXPRESSION_PREFIX = "#{";

    public static final String DEFAULT_EXPRESSION_SUFFIX = "}";

    
    private String expressionPrefix = DEFAULT_EXPRESSION_PREFIX;

    private String expressionSuffix = DEFAULT_EXPRESSION_SUFFIX;

    private ExpressionParser expressionParser;

    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(256);

    private final Map<BeanExpressionContext, StandardEvaluationContext> evaluationCache = new ConcurrentHashMap<>(8);

    private final ParserContext beanExpressionParserContext = new ParserContext() {
        @Override
        public boolean isTemplate() {
            return true;
        }
        @Override
        public String getExpressionPrefix() {
            return expressionPrefix;
        }
        @Override
        public String getExpressionSuffix() {
            return expressionSuffix;
        }
    };

    public StandardBeanExpressionResolver() {
        this(null);
    }

    public StandardBeanExpressionResolver(ClassLoader beanClassLoader) {
        SpelParserConfiguration parserConfig = new SpelParserConfiguration(
                null, beanClassLoader, false, false, Integer.MAX_VALUE, retrieveMaxExpressionLength());
        this.expressionParser = new SpelExpressionParser(parserConfig);
    }

    public void setExpressionPrefix(String expressionPrefix) {
        Assert.hasText(expressionPrefix, "Expression prefix must not be empty");
        this.expressionPrefix = expressionPrefix;
    }

    public void setExpressionSuffix(String expressionSuffix) {
        Assert.hasText(expressionSuffix, "Expression suffix must not be empty");
        this.expressionSuffix = expressionSuffix;
    }

    public void setExpressionParser(ExpressionParser expressionParser) {
        Assert.notNull(expressionParser, "ExpressionParser must not be null");
        this.expressionParser = expressionParser;
    }

    @Override
    public Object evaluate(String value, BeanExpressionContext beanExpressionContext) throws BeansException {
        if (!StringUtils.hasLength(value)) {
            return value;
        }
        try {
            Expression expr = this.expressionCache.get(value);
            if (expr == null) {
                // el表达式解析
                expr = this.expressionParser.parseExpression(value, this.beanExpressionParserContext);
                // 解析结果放入缓存
                this.expressionCache.put(value, expr);
            }
            StandardEvaluationContext sec = this.evaluationCache.get(beanExpressionContext);
            if (sec == null) {
                sec = new StandardEvaluationContext(beanExpressionContext);
                sec.addPropertyAccessor(new BeanExpressionContextAccessor());
                sec.addPropertyAccessor(new BeanFactoryAccessor());
                sec.addPropertyAccessor(new MapAccessor());
                sec.addPropertyAccessor(new EnvironmentAccessor());
                sec.setBeanResolver(new BeanFactoryResolver(beanExpressionContext.getBeanFactory()));
                sec.setTypeLocator(new StandardTypeLocator(beanExpressionContext.getBeanFactory().getBeanClassLoader()));
                sec.setTypeConverter(new StandardTypeConverter(() -> {
                    ConversionService cs = beanExpressionContext.getBeanFactory().getConversionService();
                    return (cs != null ? cs : DefaultConversionService.getSharedInstance());
                }));
                customizeEvaluationContext(sec);
                this.evaluationCache.put(beanExpressionContext, sec);
            }
            return expr.getValue(sec);
        }
        catch (Throwable ex) {
            throw new BeanExpressionException("Expression parsing failed", ex);
        }
    }

    protected void customizeEvaluationContext(StandardEvaluationContext evalContext) {
    }

    private static int retrieveMaxExpressionLength() {
        String value = SpringProperties.getProperty(MAX_SPEL_EXPRESSION_LENGTH_PROPERTY_NAME);
        if (!StringUtils.hasText(value)) {
            return SpelParserConfiguration.DEFAULT_MAX_EXPRESSION_LENGTH;
        }

        try {
            int maxLength = Integer.parseInt(value.trim());
            Assert.isTrue(maxLength > 0, () -> "Value [" + maxLength + "] for system property ["
                    + MAX_SPEL_EXPRESSION_LENGTH_PROPERTY_NAME + "] must be positive");
            return maxLength;
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Failed to parse value for system property [" +
                    MAX_SPEL_EXPRESSION_LENGTH_PROPERTY_NAME + "]: " + ex.getMessage(), ex);
        }
    }
}
