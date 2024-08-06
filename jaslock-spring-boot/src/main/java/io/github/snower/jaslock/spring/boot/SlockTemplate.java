package io.github.snower.jaslock.spring.boot;

import io.github.snower.jaslock.*;
import io.github.snower.jaslock.exceptions.ClientUnconnectException;

import java.io.IOException;

public class SlockTemplate {
    protected final SlockConfiguration configuration;
    protected volatile ISlockClient client;

    public SlockTemplate(SlockConfiguration configuration) {
        this.configuration = configuration;
        this.client = null;
    }

    public SlockConfiguration getConfiguration() {
        return configuration;
    }

    public synchronized void open() throws ClientUnconnectException, IOException {
        if (this.client != null) return;
        if (configuration.getHosts() != null && !configuration.getHosts().isEmpty()) {
            String[] hostsArray = new String[configuration.getHosts().size()];
            SlockReplsetClient replsetClient = new SlockReplsetClient(configuration.getHosts().toArray(hostsArray));
            if (configuration.getExecutorOption() != null) {
                replsetClient.enableAsyncCallback(configuration.getExecutorOption());
            } else {
                client.enableAsyncCallback();
            }
            if (configuration.getDefaultTimeoutFlag() > 0) {
                replsetClient.setDefaultTimeoutFlag(configuration.getDefaultTimeoutFlag());
            }
            if (configuration.getDefaultExpriedFlag() > 0) {
                replsetClient.setDefaultExpriedFlag(configuration.getDefaultExpriedFlag());
            }
            replsetClient.open();
            this.client = replsetClient;
            return;
        }

        SlockClient client = new SlockClient(configuration.getHost(), configuration.getPort());
        if (configuration.getExecutorOption() != null) {
            client.enableAsyncCallback(configuration.getExecutorOption());
        } else {
            client.enableAsyncCallback();
        }
        if (configuration.getDefaultTimeoutFlag() > 0) {
            client.setDefaultTimeoutFlag(configuration.getDefaultTimeoutFlag());
        }
        if (configuration.getDefaultExpriedFlag() > 0) {
            client.setDefaultExpriedFlag(configuration.getDefaultExpriedFlag());
        }
        client.open();
        this.client = client;
    }

    public synchronized void close() {
        if (this.client == null) return;
        this.client.close();
        this.client = null;
    }

    public ISlockClient getClient() {
        if (this.client == null) {
            try {
                open();
            } catch (ClientUnconnectException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.client;
    }

    public SlockDatabase selectDatabase(byte dbId) {
        return getClient().selectDatabase(dbId);
    }

    public Lock newLock(byte[] lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newLock(lockKey, timeout, expried);
    }

    public Lock newLock(String lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newLock(lockKey, timeout, expried);
    }

    public Event newEvent(byte[] eventKey, int timeout, int expried, boolean defaultSeted) {
        return selectDatabase((byte) configuration.getDatabaseId()).newEvent(eventKey, timeout, expried, defaultSeted);
    }

    public Event newEvent(String eventKey, int timeout, int expried, boolean defaultSeted) {
        return selectDatabase((byte) configuration.getDatabaseId()).newEvent(eventKey, timeout, expried, defaultSeted);
    }

    public ReentrantLock newReentrantLock(byte[] lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newReentrantLock(lockKey, timeout, expried);
    }

    public ReentrantLock newReentrantLock(String lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newReentrantLock(lockKey, timeout, expried);
    }

    public ReadWriteLock newReadWriteLock(byte[] lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newReadWriteLock(lockKey, timeout, expried);
    }

    public ReadWriteLock newReadWriteLock(String lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newReadWriteLock(lockKey, timeout, expried);
    }

    public Semaphore newSemaphore(byte[] semaphoreKey, short count, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newSemaphore(semaphoreKey, count, timeout, expried);
    }

    public Semaphore newSemaphore(String semaphoreKey, short count, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newSemaphore(semaphoreKey, count, timeout, expried);
    }

    public MaxConcurrentFlow newMaxConcurrentFlow(byte[] flowKey, short count, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newMaxConcurrentFlow(flowKey, count, timeout, expried);
    }

    public MaxConcurrentFlow newMaxConcurrentFlow(String flowKey, short count, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newMaxConcurrentFlow(flowKey, count, timeout, expried);
    }

    public MaxConcurrentFlow newMaxConcurrentFlow(byte[] flowKey, short count, int timeout, int expried, byte priority) {
        return selectDatabase((byte) configuration.getDatabaseId()).newMaxConcurrentFlow(flowKey, count, timeout, expried, priority);
    }

    public MaxConcurrentFlow newMaxConcurrentFlow(String flowKey, short count, int timeout, int expried, byte priority) {
        return selectDatabase((byte) configuration.getDatabaseId()).newMaxConcurrentFlow(flowKey, count, timeout, expried, priority);
    }

    public TokenBucketFlow newTokenBucketFlow(byte[] flowKey, short count, int timeout, double period) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTokenBucketFlow(flowKey, count, timeout, period);
    }

    public TokenBucketFlow newTokenBucketFlow(String flowKey, short count, int timeout, double period) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTokenBucketFlow(flowKey, count, timeout, period);
    }

    public TokenBucketFlow newTokenBucketFlow(byte[] flowKey, short count, int timeout, double period, byte priority) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTokenBucketFlow(flowKey, count, timeout, period, priority);
    }

    public TokenBucketFlow newTokenBucketFlow(String flowKey, short count, int timeout, double period, byte priority) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTokenBucketFlow(flowKey, count, timeout, period, priority);
    }

    public GroupEvent newGroupEvent(byte[] groupKey, long clientId, long versionId, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newGroupEvent(groupKey, clientId, versionId, timeout, expried);
    }

    public GroupEvent newGroupEvent(String groupKey, long clientId, long versionId, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newGroupEvent(groupKey, clientId, versionId, timeout, expried);
    }

    public TreeLock newTreeLock(byte[] parentKey, byte[] lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTreeLock(parentKey, lockKey, timeout, expried);
    }

    public TreeLock newTreeLock(String parentKey, String lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTreeLock(parentKey, lockKey, timeout, expried);
    }

    public TreeLock newTreeLock(byte[] lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTreeLock(lockKey, timeout, expried);
    }

    public TreeLock newTreeLock(String lockKey, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newTreeLock(lockKey, timeout, expried);
    }

    public PriorityLock newPriorityLock(byte[] lockKey, byte priority, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newPriorityLock(lockKey, priority, timeout, expried);
    }

    public PriorityLock newPriorityLock(String lockKey, byte priority, int timeout, int expried) {
        return selectDatabase((byte) configuration.getDatabaseId()).newPriorityLock(lockKey, priority, timeout, expried);
    }

    public <T> EventFuture<T> newEventFuture(byte[] eventKey) {
        return new EventFuture<>(selectDatabase((byte) configuration.getDatabaseId()), eventKey);
    }

    public <T> EventFuture<T> newEventFuture(String eventKey) {
        return new EventFuture<>(selectDatabase((byte) configuration.getDatabaseId()), eventKey);
    }

    public <T> EventFuture<T> newEventFuture(byte databaseId, byte[] eventKey) {
        return new EventFuture<>(selectDatabase(databaseId), eventKey);
    }

    public <T> EventFuture<T> newEventFuture(byte databaseId, String eventKey) {
        return new EventFuture<>(selectDatabase(databaseId), eventKey);
    }
}
