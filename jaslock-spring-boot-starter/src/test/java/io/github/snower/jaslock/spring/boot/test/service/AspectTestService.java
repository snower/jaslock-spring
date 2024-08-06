package io.github.snower.jaslock.spring.boot.test.service;

import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.boot.annotations.Idempotent;
import io.github.snower.jaslock.spring.boot.annotations.Lock;
import io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow;
import io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AspectTestService {
    @Autowired
    private String version;

    @Autowired
    private SlockTemplate slockTemplate;

    @Lock("AspectTestService_Lock_#{@version}_#{#p0}")
    public void testLock(long userid) throws SlockException {
        String key = "AspectTestService_Lock_" + version + "_" + userid;
        io.github.snower.jaslock.Lock lock = slockTemplate.newLock(key, 0, 0);
        try {
            lock.acquire();
            throw new SlockException();
        } catch (LockTimeoutException e) {}
    }

    @MaxConcurrentFlow("AspectTestService_MaxConcurrentFlow_#{@version}_#{#p0}")
    public void testMaxConcurrentFlow(long userid) throws SlockException {
        String key = "AspectTestService_MaxConcurrentFlow_" + version + "_" + userid;
        io.github.snower.jaslock.Lock lock = slockTemplate.newLock(key, 0, 0);
        try {
            lock.acquire();
            throw new SlockException();
        } catch (LockTimeoutException e) {}
    }

    @TokenBucketFlow("AspectTestService_TokenBucketFlow_#{@version}_#{#p0}")
    public void testTokenBucketFlow(long userid) throws SlockException {
        String key = "AspectTestService_TokenBucketFlow_" + version + "_" + userid;
        io.github.snower.jaslock.Lock lock = slockTemplate.newLock(key, 0, 0);
        try {
            lock.acquire();
            throw new SlockException();
        } catch (LockTimeoutException e) {}
    }

    @Idempotent("AspectTestService_Idempotent_#{@version}_#{#p0}")
    public String testIdempotent(long userid) throws SlockException {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return String.valueOf(System.currentTimeMillis());
    }
}
