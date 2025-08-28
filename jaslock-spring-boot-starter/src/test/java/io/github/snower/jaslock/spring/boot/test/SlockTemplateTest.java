package io.github.snower.jaslock.spring.boot.test;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.EventFuture;
import io.github.snower.jaslock.spring.boot.SlockConfiguration;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.boot.autoconfigure.SlockAutoConfiguration;
import io.github.snower.jaslock.spring.boot.autoconfigure.SlockProperties;
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
import java.util.Arrays;
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
    public void testSlockPropertiesParseUrl() {
        SlockProperties slockProperties = new SlockProperties();
        slockProperties.setUrl("http://localhost:5657");
        try {
            SlockConfiguration slockConfiguration = slockProperties.buildConfiguration();
            org.junit.Assert.assertEquals(slockConfiguration.getHost(), "localhost");
            org.junit.Assert.assertEquals(slockConfiguration.getPort(), 5657);
            org.junit.Assert.assertNull(slockConfiguration.getHosts());
            org.junit.Assert.assertNull(slockConfiguration.getExecutorOption());
        } catch (IllegalArgumentException e) {}

        slockProperties = new SlockProperties();
        slockProperties.setUrl("slock://localhost:5657");
        SlockConfiguration slockConfiguration = slockProperties.buildConfiguration();
        org.junit.Assert.assertEquals(slockConfiguration.getHost(), "localhost");
        org.junit.Assert.assertEquals(slockConfiguration.getPort(), 5657);
        org.junit.Assert.assertNull(slockConfiguration.getHosts());
        org.junit.Assert.assertNull(slockConfiguration.getExecutorOption());
        org.junit.Assert.assertEquals(slockConfiguration.getDatabaseId(), 0);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultTimeoutFlag(), 0);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultExpriedFlag(), 0);

        slockProperties = new SlockProperties();
        slockProperties.setUrl("slock://server1:5657,server2:5659");
        slockConfiguration = slockProperties.buildConfiguration();
        org.junit.Assert.assertEquals(slockConfiguration.getHost(), "127.0.0.1");
        org.junit.Assert.assertEquals(slockConfiguration.getPort(), 5658);
        org.junit.Assert.assertEquals(slockConfiguration.getHosts(), Arrays.asList("server1:5657", "server2:5659"));
        org.junit.Assert.assertNull(slockConfiguration.getExecutorOption());
        org.junit.Assert.assertEquals(slockConfiguration.getDatabaseId(), 0);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultTimeoutFlag(), 0);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultExpriedFlag(), 0);

        slockProperties = new SlockProperties();
        slockProperties.setUrl("slock://localhost:5657/?database=1&defaultTimeoutFlag=1&defaultExpriedFlag=1");
        slockConfiguration = slockProperties.buildConfiguration();
        org.junit.Assert.assertEquals(slockConfiguration.getHost(), "localhost");
        org.junit.Assert.assertEquals(slockConfiguration.getPort(), 5657);
        org.junit.Assert.assertNull(slockConfiguration.getHosts());
        org.junit.Assert.assertNull(slockConfiguration.getExecutorOption());
        org.junit.Assert.assertEquals(slockConfiguration.getDatabaseId(), 1);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultTimeoutFlag(), 1);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultExpriedFlag(), 1);

        slockProperties = new SlockProperties();
        slockProperties.setUrl("slock://server1:5657,server2:5659/?database=1&executorWorkerCount=2&executorMaxWorkerCount=4&executorMaxCapacity=65536&executorWorkerKeepAliveTime=3600&defaultTimeoutFlag=1&defaultExpriedFlag=1");
        slockConfiguration = slockProperties.buildConfiguration();
        org.junit.Assert.assertEquals(slockConfiguration.getHost(), "127.0.0.1");
        org.junit.Assert.assertEquals(slockConfiguration.getPort(), 5658);
        org.junit.Assert.assertEquals(slockConfiguration.getHosts(), Arrays.asList("server1:5657", "server2:5659"));
        org.junit.Assert.assertNotNull(slockConfiguration.getExecutorOption());
        org.junit.Assert.assertEquals(slockConfiguration.getExecutorOption().getWorkerCount(), 2);
        org.junit.Assert.assertEquals(slockConfiguration.getExecutorOption().getMaxWorkerCount(), 4);
        org.junit.Assert.assertEquals(slockConfiguration.getExecutorOption().getMaxCapacity(), 65536);
        org.junit.Assert.assertEquals(slockConfiguration.getExecutorOption().getWorkerKeepAliveTime(), 3600);
        org.junit.Assert.assertEquals(slockConfiguration.getDatabaseId(), 1);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultTimeoutFlag(), 1);
        org.junit.Assert.assertEquals(slockConfiguration.getDefaultExpriedFlag(), 1);
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
        aspectTestService.testLock(1);
        aspectTestService.testLockConstKey(1);
        aspectTestService.testLockConstKey(1);
        aspectTestService.testLockFastKey(1);
        aspectTestService.testLockFastKey(1);
        aspectTestService.testLocks(1);
        aspectTestService.testLocks(1);
    }
    @Test
    public void testLockWithTransaction() throws SlockException {
        aspectTestService.testLockWithTransaction(1);
        aspectTestService.testLockWithTransaction(1);
        aspectTestService.testLockWithTransactionFastKey(1);
        aspectTestService.testLockWithTransactionFastKey(1);
        aspectTestService.testLockWithTransactions(1);
        aspectTestService.testLockWithTransactions(1);
    }


    @Test
    public void testMaxConcurrentFlowAspect() throws SlockException {
        aspectTestService.testMaxConcurrentFlow(1);
        aspectTestService.testMaxConcurrentFlow(1);
        aspectTestService.testMaxConcurrentFlowFastKey(1);
        aspectTestService.testMaxConcurrentFlowFastKey(1);
        aspectTestService.testMaxConcurrentFlows(1);
        aspectTestService.testMaxConcurrentFlows(1);
    }

    @Test
    public void testTokenBucketFlowAspect() throws SlockException {
        aspectTestService.testTokenBucketFlow(1);
        aspectTestService.testTokenBucketFlow(1);
        aspectTestService.testTokenBucketFlowFastKey(1);
        aspectTestService.testTokenBucketFlowFastKey(1);
        aspectTestService.testTokenBucketFlows(1);
        aspectTestService.testTokenBucketFlows(1);
    }

    @Test
    public void testIdempotentAspect() throws SlockException {
        String value1 = aspectTestService.testIdempotent(1);
        org.junit.Assert.assertTrue(value1 != null && Math.abs(System.currentTimeMillis() - Long.parseLong(value1)) < 10000);
        String value2 = aspectTestService.testIdempotent(1);
        org.junit.Assert.assertTrue(value2 != null && Math.abs(System.currentTimeMillis() - Long.parseLong(value2)) < 10000);
        org.junit.Assert.assertEquals(value1, value2);
        String value3 = aspectTestService.testIdempotentFastKey(1);
        org.junit.Assert.assertTrue(value3 != null && Math.abs(System.currentTimeMillis() - Long.parseLong(value3)) < 10000);
        String value4 = aspectTestService.testIdempotentFastKey(1);
        org.junit.Assert.assertTrue(value4 != null && Math.abs(System.currentTimeMillis() - Long.parseLong(value4)) < 10000);
        org.junit.Assert.assertEquals(value3, value4);
    }
}
