package io.github.snower.jaslock.spring.boot;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.SlockDatabase;
import io.github.snower.jaslock.callback.CallbackCommandResult;
import io.github.snower.jaslock.datas.LockData;
import io.github.snower.jaslock.exceptions.SlockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class TransactionLock extends Lock {
    private static final Logger logger = LoggerFactory.getLogger(TransactionLock.class);
    private final String bindTransactionKey;
    private boolean acquired = false;

    public TransactionLock(SlockDatabase database, byte[] lockKey, byte[] lockId, int timeout, int expried, short count, byte rCount) {
        super(database, lockKey, lockId, timeout, expried, count, rCount);

        this.bindTransactionKey = "__SLOCK___LockWithTransaction___::" + new String(lockKey, StandardCharsets.UTF_8);
    }

    public TransactionLock(SlockDatabase database, byte[] lockKey, int timeout, int expried) {
        super(database, lockKey, timeout, expried);

        this.bindTransactionKey = "__SLOCK___LockWithTransaction___::" + new String(lockKey, StandardCharsets.UTF_8);
    }

    public TransactionLock(SlockDatabase database, String lockKey, int timeout, int expried) {
        super(database, lockKey, timeout, expried);

        this.bindTransactionKey = "__SLOCK___LockWithTransaction___::" + lockKey;
    }

    public void acquire(byte flag, Consumer<CallbackCommandResult> callback) throws SlockException {
        if (TransactionSynchronizationManager.hasResource(bindTransactionKey)) {
            return;
        }
        super.acquire(flag, callback);
        this.acquired = true;
        try {
            TransactionSynchronizationManager.bindResource(bindTransactionKey, this);
        } catch (Exception e) {
            this.acquired = false;
            super.release();
            throw e;
        }
    }

    public void acquire(byte flag, LockData lockData, Consumer<CallbackCommandResult> callback) throws SlockException {
        if (TransactionSynchronizationManager.hasResource(bindTransactionKey)) {
            return;
        }
        super.acquire(flag, lockData, callback);
        this.acquired = true;
        try {
            TransactionSynchronizationManager.bindResource(bindTransactionKey, this);
        } catch (Exception e) {
            this.acquired = false;
            super.release();
            throw e;
        }
    }

    public void acquire() throws SlockException {
        if (TransactionSynchronizationManager.hasResource(bindTransactionKey)) {
            return;
        }
        super.acquire();
        this.acquired = true;
        try {
            TransactionSynchronizationManager.bindResource(bindTransactionKey, this);
        } catch (Exception e) {
            this.acquired = false;
            super.release();
            throw e;
        }
    }

    public void acquire(LockData lockData) throws SlockException {
        if (TransactionSynchronizationManager.hasResource(bindTransactionKey)) {
            return;
        }
        super.acquire(lockData);
        this.acquired = true;
        try {
            TransactionSynchronizationManager.bindResource(bindTransactionKey, this);
        } catch (Exception e) {
            this.acquired = false;
            super.release();
            throw e;
        }
    }

    public void release(byte flag, Consumer<CallbackCommandResult> callback) throws SlockException {
        if (!this.acquired) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            this.acquired = false;
            try {
                super.release(flag, callback);
            } finally {
                try {
                    TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                } catch (Exception e) {
                    logger.warn("LockWithTransaction " + bindTransactionKey + " unbind error " + e);
                }
            }
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionLock.this.acquired = false;
                    try {
                        TransactionLock.super.release(flag, callback);
                    } catch (SlockException e) {
                        logger.warn("LockWithTransaction release error: {}", this);
                    }
                    try {
                        TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                    } catch (Exception e) {
                        logger.warn("LockWithTransaction " + bindTransactionKey + " unbind error " + e);
                    }
                }
            });
        }
    }

    public void release(byte flag, LockData lockData, Consumer<CallbackCommandResult> callback) throws SlockException {
        if (!this.acquired) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            this.acquired = false;
            try {
                super.release(flag, lockData, callback);
            } finally {
                try {
                    TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                } catch (Exception e) {
                    logger.warn("LockWithTransaction " + bindTransactionKey + " unbind error " + e);
                }
            }
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionLock.this.acquired = false;
                    try {
                        TransactionLock.super.release(flag, lockData, callback);
                    } catch (SlockException e) {
                        logger.warn("LockWithTransaction release error: {}", this);
                    }
                    try {
                        TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                    } catch (Exception e) {
                        logger.warn("LockWithTransaction " + bindTransactionKey + " unbind error " + e);
                    }
                }
            });
        }
    }

    @Override
    public void release() throws SlockException {
        if (!this.acquired) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            this.acquired = false;
            try {
                super.release();
            } finally {
                try {
                    TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                } catch (Exception e) {
                    logger.warn("LockWithTransaction " + bindTransactionKey + " unbind error " + e);
                }
            }
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionLock.this.acquired = false;
                    try {
                        TransactionLock.super.release();
                    } catch (SlockException e) {
                        logger.warn("LockWithTransaction release error: {}", this);
                    }
                    try {
                        TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                    } catch (Exception e) {
                        logger.warn("LockWithTransaction " + bindTransactionKey + " unbind error " + e);
                    }
                }
            });
        }
    }

    public void release(LockData lockData) throws SlockException {
        if (!this.acquired) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            this.acquired = false;
            try {
                super.release((byte) 0, lockData);
            } finally {
                try {
                    TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                } catch (Exception e) {
                    logger.warn("LockWithTransaction " + bindTransactionKey + " unbind error " + e);
                }
            }
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionLock.this.acquired = false;
                    try {
                        TransactionLock.super.release((byte) 0, lockData);
                    } catch (SlockException e) {
                        logger.warn("LockWithTransaction release error: {}", this);
                    }
                    try {
                        TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                    } catch (Exception e) {
                        logger.warn("LockWithTransaction " + bindTransactionKey + " error " + e);
                    }
                }
            });
        }
    }

    @Override
    public AutoCloseable with() throws SlockException {
        acquire();
        return this::release;
    }
}
