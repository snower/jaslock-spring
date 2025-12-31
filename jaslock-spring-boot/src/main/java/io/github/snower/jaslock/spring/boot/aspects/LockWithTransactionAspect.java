package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.AbstractBaseAspect;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10001)
public class LockWithTransactionAspect extends AbstractBaseAspect {
    private static final Logger logger = LoggerFactory.getLogger(LockWithTransactionAspect.class);

    public LockWithTransactionAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.LockWithTransaction)")
    private void LockWithTransaction() {}

    @Around("LockWithTransaction()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        AbstractBaseAspect.KeyEvaluate keyEvaluate = keyEvaluateCache.get(method);
        if (keyEvaluate == null) {
            io.github.snower.jaslock.spring.boot.annotations.LockWithTransaction lockAnnotation =
                    method.getAnnotation(io.github.snower.jaslock.spring.boot.annotations.LockWithTransaction.class);
            if (lockAnnotation == null) {
                throw new IllegalArgumentException("unknown LockWithTransaction annotation");
            }
            String templateKey = lockAnnotation.value();
            if (isBlank(templateKey)) {
                templateKey = lockAnnotation.key();
                if (isBlank(templateKey)) {
                    throw new IllegalArgumentException("key is empty");
                }
            }
            keyEvaluate = compileKeyEvaluate(methodSignature.getMethod(), templateKey, ke -> {
                ke.setTargetParameter(lockAnnotation);
                int timeout = lockAnnotation.timeout() | (lockAnnotation.timeoutFlag() << 16);
                int expried = lockAnnotation.expried() | (lockAnnotation.expriedFlag() << 16);
                byte databaseId = lockAnnotation.databaseId();
                if (databaseId >= 0 && databaseId < 127) {
                    ke.setTargetInstanceBuilder(key -> slockTemplate.selectDatabase(databaseId)
                            .newLock((String) key, timeout, expried));
                }
                ke.setTargetInstanceBuilder(key -> slockTemplate.newLock((String) key, timeout, expried));
                return ke;
            });
        }
        String key = keyEvaluate.evaluate(methodSignature.getMethod(), methodJoinPoint.getArgs(), methodJoinPoint.getThis());
        String bindTransactionKey = "__SLOCK___LockWithTransaction___::" + key;
        if (TransactionSynchronizationManager.hasResource(bindTransactionKey)) {
            return joinPoint.proceed();
        }
        Lock lock = (Lock) keyEvaluate.buildTargetInstance(key);
        try {
            lock.acquire();
            try {
                TransactionSynchronizationManager.bindResource(bindTransactionKey, lock);
                return joinPoint.proceed();
            } finally {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        logger.warn("LockWithTransactionAspect release {} error {}", key, e, e);
                    }
                    try {
                        TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                    } catch (Exception e) {
                        logger.warn("LockWithTransactionAspect " + bindTransactionKey + " unbind error " + e);
                    }
                } else {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            try {
                                lock.release();
                            } catch (Exception e) {
                                logger.warn("LockWithTransactionAspect release {} error {}", key, e, e);
                            }
                            try {
                                TransactionSynchronizationManager.unbindResource(bindTransactionKey);
                            } catch (Exception e) {
                                logger.warn("LockWithTransactionAspect " + bindTransactionKey + " unbind error " + e);
                            }
                        }
                    });
                }
            }
        } catch (LockTimeoutException e) {
            io.github.snower.jaslock.spring.boot.annotations.LockWithTransaction lockAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.LockWithTransaction) keyEvaluate.getTargetParameter();
            if (!lockAnnotation.timeoutException().isInstance(e)) {
                throw lockAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            io.github.snower.jaslock.spring.boot.annotations.LockWithTransaction lockAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.LockWithTransaction) keyEvaluate.getTargetParameter();
            if (!lockAnnotation.exception().isInstance(e)) {
                throw lockAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }
}
