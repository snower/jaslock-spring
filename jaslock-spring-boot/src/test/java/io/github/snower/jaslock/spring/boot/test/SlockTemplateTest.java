package io.github.snower.jaslock.spring.boot.test;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.*;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.expression.*;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SlockTemplateTest {
    @Test
    public void testKeyEvaluation() throws NoSuchMethodException {
        SpelExpressionParser spelExpressionParser = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.MIXED,
                this.getClass().getClassLoader()));
        Date now1 = new Date(), now2 = new Date();

        StandardEvaluationContext applicationEvaluationContext = new StandardEvaluationContext(this);
        applicationEvaluationContext.addPropertyAccessor(new BeanExpressionContextAccessor());
        applicationEvaluationContext.addPropertyAccessor(new BeanFactoryAccessor());
        applicationEvaluationContext.addPropertyAccessor(new MapAccessor());
        applicationEvaluationContext.addPropertyAccessor(new EnvironmentAccessor());
        applicationEvaluationContext.setBeanResolver(new BeanFactoryResolver(new StaticListableBeanFactory() {{
            addBean("testBean", now1);
        }}));
        applicationEvaluationContext.setTypeLocator(new StandardTypeLocator(this.getClass().getClassLoader()));

        String key = "aaa_#{#p0}_#{#p1}_#{@testBean}";
        Expression expression = spelExpressionParser.parseExpression(key, ParserContext.TEMPLATE_EXPRESSION);
        KeyEvaluationContext evaluationContext = new KeyEvaluationContext(this,
                this.getClass().getMethod("runKeyEvaluation", Long.class, Date.class), new Object[]{1223232L, now2},
                new StandardReflectionParameterNameDiscoverer(), applicationEvaluationContext);
        Assert.assertEquals(expression.getValue(evaluationContext, String.class), "aaa_1223232_" + now2 + "_" + now1);
    }

    public String runKeyEvaluation(Long id, Date time) {
        return "A" + id + time;
    }

    @Test
    public void testLock() throws IOException, SlockException {
        SlockTemplate slockTemplate = new SlockTemplate(SlockConfiguration.newBuilder()
                .build(), new SlockSerializater.ObjectSerializater());
        slockTemplate.open();
        if (!slockTemplate.getClient().ping()) {
            throw new SlockException();
        }

        try {
            Lock lock = slockTemplate.newLock("testLock", 5, 120);
            lock.acquire();
            lock.release();

            if (!slockTemplate.getClient().ping()) {
                throw new SlockException();
            }
        } finally {
            slockTemplate.close();
        }
    }

    @Test
    public void testEventFuture() throws IOException, SlockException, ExecutionException, InterruptedException {
        SlockTemplate slockTemplate = new SlockTemplate(SlockConfiguration.newBuilder()
                .build(), new SlockSerializater.ObjectSerializater());
        slockTemplate.open();
        try {
            EventFuture<String> eventFuture = slockTemplate.newEventFuture("testEventFuture");
            eventFuture.setResult("Test");
            eventFuture = slockTemplate.newEventFuture("testEventFuture");
            Assert.assertEquals(eventFuture.get(), "Test");
            eventFuture.close();
            eventFuture = slockTemplate.newEventFuture("testEventFuture");
            try {
                Assert.assertNull(eventFuture.get(0, TimeUnit.SECONDS));
                throw new SlockException();
            } catch (TimeoutException e) {}
        } finally {
            slockTemplate.close();
        }
    }
}
