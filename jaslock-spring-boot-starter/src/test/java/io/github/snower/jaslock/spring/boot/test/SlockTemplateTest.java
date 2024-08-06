package io.github.snower.jaslock.spring.boot.test;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.EventFuture;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.boot.autoconfigure.SlockAutoConfiguration;
import io.github.snower.jaslock.spring.boot.test.config.SlockTestConfiguration;
import io.github.snower.jaslock.spring.boot.test.service.AspectTestService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SpringBootTest
@SpringBootConfiguration
@Import(value = {SlockTestConfiguration.class, SlockAutoConfiguration.class})
@RunWith(SpringRunner.class)
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class SlockTemplateTest {
    @Autowired
    private SlockTemplate slockTemplate;

    @Autowired
    private AspectTestService aspectTestService;

    @Test
    public void contextLoads() {
        Assert.notNull(slockTemplate, "slockTemplate not null");
        Assert.notNull(aspectTestService, "aspectTestService not null");
    }

    @Test
    public void testLock() throws IOException, SlockException {
        if (!slockTemplate.getClient().ping()) {
            throw new SlockException();
        }
        Lock lock = slockTemplate.newLock("testLock", 5, 120);
        lock.acquire();
        lock.release();
        if (!slockTemplate.getClient().ping()) {
            throw new SlockException();
        }
    }

    @Test
    public void testEventFuture() throws IOException, SlockException, ExecutionException, InterruptedException {
        EventFuture<String> eventFuture = slockTemplate.newEventFuture("testEventFuture");
        eventFuture.setResult("Test");
        eventFuture = slockTemplate.newEventFuture("testEventFuture");
        org.junit.Assert.assertEquals(eventFuture.get(), "Test");
        eventFuture.close();
        eventFuture = slockTemplate.newEventFuture("testEventFuture");
        try {
            org.junit.Assert.assertNull(eventFuture.get(0, TimeUnit.SECONDS));
            throw new SlockException();
        } catch (TimeoutException e) {}
    }

    @Test
    public void testLockAspect() throws SlockException {
        aspectTestService.testLock(1);
    }

    @Test
    public void testMaxConcurrentFlowAspect() throws SlockException {
        aspectTestService.testMaxConcurrentFlow(1);
    }

    @Test
    public void testTokenBucketFlowAspect() throws SlockException {
        aspectTestService.testTokenBucketFlow(1);
    }

    @Test
    public void testIdempotentAspect() throws SlockException {
        String value1 = aspectTestService.testIdempotent(1);
        String value2 = aspectTestService.testIdempotent(1);
        org.junit.Assert.assertEquals(value1, value2);
    }
}
