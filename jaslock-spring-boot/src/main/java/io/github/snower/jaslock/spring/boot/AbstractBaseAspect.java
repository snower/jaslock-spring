package io.github.snower.jaslock.spring.boot;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.*;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractBaseAspect implements BeanFactoryAware, ApplicationContextAware {
    protected final static StandardReflectionParameterNameDiscoverer parameterNameDiscoverer = new StandardReflectionParameterNameDiscoverer();
    protected final SlockTemplate slockTemplate;
    protected BeanFactory beanFactory;
    protected ApplicationContext applicationContext;
    protected SpelExpressionParser spelExpressionParser;
    protected StandardEvaluationContext applicationEvaluationContext;
    protected Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    protected AbstractBaseAspect(SlockTemplate slockTemplate) {
        this.slockTemplate = slockTemplate;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        this.spelExpressionParser = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.MIXED,
                beanFactory.getClass().getClassLoader()));
        if (applicationContext != null && applicationEvaluationContext == null) {
            this.buildApplicationEvaluationContext();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        if (beanFactory != null && applicationEvaluationContext == null) {
            this.buildApplicationEvaluationContext();
        }
    }

    protected String evaluateKey(String key, Method method, Object[] arguments, Object target) {
        Expression expression = expressionCache.computeIfAbsent(key, k -> spelExpressionParser.parseExpression(key, ParserContext.TEMPLATE_EXPRESSION));
        KeyEvaluationContext evaluationContext = new KeyEvaluationContext(target, method, arguments,
                parameterNameDiscoverer, applicationEvaluationContext);
        evaluationContext.setVariable("environment", applicationContext.getEnvironment());
        return expression.getValue(evaluationContext, String.class);
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void buildApplicationEvaluationContext() {
        applicationEvaluationContext = new StandardEvaluationContext(applicationContext);
        applicationEvaluationContext.addPropertyAccessor(new BeanExpressionContextAccessor());
        applicationEvaluationContext.addPropertyAccessor(new BeanFactoryAccessor());
        applicationEvaluationContext.addPropertyAccessor(new MapAccessor());
        applicationEvaluationContext.addPropertyAccessor(new EnvironmentAccessor());
        applicationEvaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
        applicationEvaluationContext.setTypeLocator(new StandardTypeLocator(beanFactory.getClass().getClassLoader()));
    }
}
