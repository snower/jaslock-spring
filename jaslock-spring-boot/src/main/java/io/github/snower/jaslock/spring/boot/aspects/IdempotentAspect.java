package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.commands.ICommand;
import io.github.snower.jaslock.datas.LockSetData;
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

import java.io.*;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class IdempotentAspect extends AbstractBaseAspect {
    private static final Logger logger = LoggerFactory.getLogger(LockAspect.class);

    public IdempotentAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.Idempotent)")
    private void Lock() {}

    @Around("Lock()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        io.github.snower.jaslock.spring.boot.annotations.Idempotent idempotentAnnotation =
                methodSignature.getMethod().getAnnotation(io.github.snower.jaslock.spring.boot.annotations.Idempotent.class);
        if (idempotentAnnotation == null) {
            throw new IllegalArgumentException("unknown Idempotent annotation");
        }

        String templateKey = idempotentAnnotation.value();
        if (isBlank(templateKey)) {
            templateKey = idempotentAnnotation.key();
            if (isBlank(templateKey)) {
                throw new IllegalArgumentException("key is empty");
            }
        }
        String key = evaluateKey(templateKey, methodSignature.getMethod(), methodJoinPoint.getArgs(), methodJoinPoint.getThis());
        Lock lock = idempotentAnnotation.databaseId() >= 0 && idempotentAnnotation.databaseId() < 127 ?
                slockTemplate.selectDatabase(idempotentAnnotation.databaseId())
                        .newLock(key, idempotentAnnotation.timeout(), idempotentAnnotation.expried()) :
                slockTemplate.newLock(key, idempotentAnnotation.timeout(), idempotentAnnotation.expried());
        lock.setTimeoutFlag((short) ICommand.TIMEOUT_FLAG_TIMEOUT_WHEN_CONTAINS_DATA);
        try {
            lock.acquire();
            boolean isUpdateResult = false;
            try {
                if (lock.getCurrentLockData() != null) {
                    return getResult(lock);
                }
                Object result = joinPoint.proceed();
                isUpdateResult = updateResult(lock, result);
                return result;
            } finally {
                if (!isUpdateResult) {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        logger.warn("Slock IdempotentAspect release {} error {}", templateKey, e, e);
                    }
                }
            }
        } catch (LockTimeoutException e) {
            if (lock.getCurrentLockData() != null) {
                return getResult(lock);
            }
            if (!idempotentAnnotation.timeoutException().isInstance(e)) {
                throw idempotentAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            if (!idempotentAnnotation.exception().isInstance(e)) {
                throw idempotentAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }

    private boolean updateResult(Lock lock, Object result) throws IOException, SlockException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
                IdempotentResult idempotentResult = new IdempotentResult(result);
                objectOutputStream.writeObject(idempotentResult);
            }
            lock.releaseHeadRetoLockWait(new LockSetData(byteArrayOutputStream.toByteArray()));
        }
        return true;
    }

    private Object getResult(Lock lock) throws IOException, ClassNotFoundException {
        if (lock.getCurrentLockData() == null) return null;
        byte[] lockData = lock.getCurrentLockData().getDataAsBytes();
        if (lockData == null) return null;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(lockData))) {
            Object idempotentResult = objectInputStream.readObject();
            if (!(idempotentResult instanceof IdempotentResult)) return null;
            return ((IdempotentResult) idempotentResult).getResult();
        }
    }

    private static class IdempotentResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private Object result;

        public IdempotentResult(Object result) {
            this.result = result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public Object getResult() {
            return result;
        }
    }
}
