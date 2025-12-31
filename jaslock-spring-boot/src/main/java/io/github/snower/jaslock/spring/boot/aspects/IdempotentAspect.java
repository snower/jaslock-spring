package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.commands.ICommand;
import io.github.snower.jaslock.datas.LockSetData;
import io.github.snower.jaslock.datas.LockUnsetData;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.AbstractBaseAspect;
import io.github.snower.jaslock.spring.boot.SlockSerializater;
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
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 9000)
public class IdempotentAspect extends AbstractBaseAspect {
    private static final Logger logger = LoggerFactory.getLogger(IdempotentAspect.class);

    private final SlockSerializater serializater;
    private final Map<String, IdempotentEvaluation> idempotentEvaluationCache = new ConcurrentHashMap<>();

    public IdempotentAspect(SlockTemplate slockTemplate, SlockSerializater serializater) {
        super(slockTemplate);
        this.serializater = serializater == null ? slockTemplate.getSerializater() : serializater;
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.Idempotent)")
    private void Lock() {}

    @Around("Lock()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        KeyEvaluate keyEvaluate = keyEvaluateCache.get(method);
        if (keyEvaluate == null) {
            io.github.snower.jaslock.spring.boot.annotations.Idempotent idempotentAnnotation =
                    method.getAnnotation(io.github.snower.jaslock.spring.boot.annotations.Idempotent.class);
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
            keyEvaluate = compileKeyEvaluate(methodSignature.getMethod(), templateKey,  ke -> {
                ke.setTargetParameter(idempotentAnnotation);
                int timeout = idempotentAnnotation.timeout() | (idempotentAnnotation.timeoutFlag() << 16) | (ICommand.TIMEOUT_FLAG_TIMEOUT_WHEN_CONTAINS_DATA << 16);
                int expried = idempotentAnnotation.expried() | (idempotentAnnotation.expriedFlag() << 16);
                byte databaseId = idempotentAnnotation.databaseId();
                if (databaseId >= 0 && databaseId < 127) {
                    ke.setTargetInstanceBuilder(key -> slockTemplate.selectDatabase(databaseId)
                            .newLock((String) key, timeout, expried));
                }
                ke.setTargetInstanceBuilder(key -> slockTemplate.newLock((String) key, timeout, expried));
                return ke;
            });
        }
        String key = keyEvaluate.evaluate(methodSignature.getMethod(), methodJoinPoint.getArgs(), methodJoinPoint.getThis());
        Class<?> resultClass = method.getReturnType();
        IdempotentEvaluation idempotentEvaluation = idempotentEvaluationCache.computeIfAbsent(key, k -> new IdempotentEvaluation());
        synchronized (idempotentEvaluation) {
            if (resultClass.isInstance(idempotentEvaluation.result)) {
                return idempotentEvaluation.result;
            }
            Object result = execute(methodJoinPoint, keyEvaluate, key, resultClass);
            idempotentEvaluation.result = result;
            idempotentEvaluationCache.remove(key);
            return result;
        }
    }

    private Object execute(ProceedingJoinPoint joinPoint, KeyEvaluate keyEvaluate, String key, Class<?> resultClass) throws Throwable {
        Lock lock = (Lock) keyEvaluate.buildTargetInstance(key);
        try {
            lock.acquire();
            boolean isUpdateResult = false;
            try {
                if (lock.getCurrentLockData() != null) {
                    Object result = getResult(lock, resultClass);
                    if (result != null) return result;
                }
                Object result = joinPoint.proceed();
                isUpdateResult = updateResult(lock, result, (io.github.snower.jaslock.spring.boot.annotations.Idempotent) keyEvaluate.getTargetParameter());
                return result;
            } finally {
                if (!isUpdateResult) {
                    io.github.snower.jaslock.spring.boot.annotations.Idempotent idempotentAnnotation =
                            (io.github.snower.jaslock.spring.boot.annotations.Idempotent) keyEvaluate.getTargetParameter();
                    try {
                        if (idempotentAnnotation.persistence() <= 0) {
                            lock.release(new LockUnsetData(ICommand.LOCK_DATA_FLAG_PROCESS_FIRST_OR_LAST));
                        } else {
                            lock.release();
                        }
                    } catch (Exception e) {
                        logger.warn("IdempotentAspect release {} error {}", key, e, e);
                    }
                }
            }
        } catch (LockTimeoutException e) {
            if (lock.getCurrentLockData() != null) {
                return getResult(lock, resultClass);
            }
            io.github.snower.jaslock.spring.boot.annotations.Idempotent idempotentAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.Idempotent) keyEvaluate.getTargetParameter();
            if (!idempotentAnnotation.timeoutException().isInstance(e)) {
                throw idempotentAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            io.github.snower.jaslock.spring.boot.annotations.Idempotent idempotentAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.Idempotent) keyEvaluate.getTargetParameter();
            if (!idempotentAnnotation.exception().isInstance(e)) {
                throw idempotentAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }

    private boolean updateResult(Lock lock, Object result, io.github.snower.jaslock.spring.boot.annotations.Idempotent idempotent) throws IOException, SlockException {
        if (idempotent.persistence() <= 0) {
            lock.release(new LockSetData(serializater.serializate(result)));
        } else {
            lock.setExpried((short) idempotent.persistence());
            lock.setExpriedFlag((short) idempotent.persistenceFlag());
            lock.releaseHeadRetoLockWait(new LockSetData(serializater.serializate(result)));
        }
        return true;
    }

    private Object getResult(Lock lock, Class<?> resultClass) throws IOException {
        if (lock.getCurrentLockData() == null) return null;
        byte[] lockData = lock.getCurrentLockData().getDataAsBytes();
        if (lockData == null) return null;
        Object idempotentResult = serializater.deserialize(lockData, resultClass);
        if (!resultClass.isInstance(idempotentResult)) return null;
        return idempotentResult;
    }

    public static class IdempotentEvaluation {
        private Object result;
    }
}
