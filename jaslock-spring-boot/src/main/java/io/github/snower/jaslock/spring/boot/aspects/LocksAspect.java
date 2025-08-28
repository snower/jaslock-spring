package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.AbstractMultiBaseAspect;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10000)
public class LocksAspect extends AbstractMultiBaseAspect {
    private static final Logger logger = LoggerFactory.getLogger(LocksAspect.class);

    public LocksAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.Locks)")
    private void Locks() {}

    @Around("Locks()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        MultiEvaluates keyEvaluate = (MultiEvaluates) keyEvaluateCache.get(method);
        if (keyEvaluate == null) {
            io.github.snower.jaslock.spring.boot.annotations.Locks locksAnnotation =
                    method.getAnnotation(io.github.snower.jaslock.spring.boot.annotations.Locks.class);
            if (locksAnnotation == null || locksAnnotation.value() == null || locksAnnotation.value().length == 0) {
                throw new IllegalArgumentException("Locks is empty");
            }
            keyEvaluate = compileKeyEvaluates(method, locksAnnotation.value());
        }
        Object[] args = methodJoinPoint.getArgs();
        Object target = methodJoinPoint.getThis();
        KeyEvaluate[] keyEvaluates = keyEvaluate.getKeyEvaluates();
        String[] keys = new String[keyEvaluates.length];
        for (int i = 0; i < keyEvaluates.length; i++) {
            keys[i] = keyEvaluates[i].evaluate(method, args, target);
        }
        return proceed(joinPoint, keyEvaluates, keys, 0);
    }

    @Override
    protected String getTemplateKey(Annotation annotation) {
        io.github.snower.jaslock.spring.boot.annotations.Lock lockAnnotation =
                (io.github.snower.jaslock.spring.boot.annotations.Lock) annotation;
        if (lockAnnotation == null) {
            throw new IllegalArgumentException("unknown Lock annotation");
        }
        String templateKey = lockAnnotation.value();
        if (isBlank(templateKey)) {
            templateKey = lockAnnotation.key();
            if (isBlank(templateKey)) {
                throw new IllegalArgumentException("key is empty");
            }
        }
        return templateKey;
    }

    @Override
    protected KeyEvaluate configureKeyEvaluate(Annotation annotation, KeyEvaluate keyEvaluate) {
        io.github.snower.jaslock.spring.boot.annotations.Lock lockAnnotation =
                (io.github.snower.jaslock.spring.boot.annotations.Lock) annotation;
        keyEvaluate.setTargetParameter(lockAnnotation);
        int timeout = lockAnnotation.timeout() | (lockAnnotation.timeoutFlag() << 16);
        int expried = lockAnnotation.expried() | (lockAnnotation.expriedFlag() << 16);
        byte databaseId = lockAnnotation.databaseId();
        if (databaseId >= 0 && databaseId < 127) {
            keyEvaluate.setTargetInstanceBuilder(key -> slockTemplate.selectDatabase(databaseId)
                    .newLock((String) key, timeout, expried));
        }
        keyEvaluate.setTargetInstanceBuilder(key -> slockTemplate.newLock((String) key, timeout, expried));
        return keyEvaluate;
    }

    @Override
    protected Object execute(ProceedingJoinPoint joinPoint, KeyEvaluate[] keyEvaluates, String[] keys, int index) throws Throwable {
        KeyEvaluate keyEvaluate = keyEvaluates[index];
        String key = keys[index];
        Lock lock = (Lock) keyEvaluate.buildTargetInstance(key);
        try {
            lock.acquire();
            try {
                return proceed(joinPoint, keyEvaluates, keys, index + 1);
            } finally {
                try {
                    lock.release();
                } catch (Exception e) {
                    logger.warn("LockAspect release {} error {}", key, e, e);
                }
            }
        } catch (LockTimeoutException e) {
            io.github.snower.jaslock.spring.boot.annotations.Lock lockAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.Lock) keyEvaluate.getTargetParameter();
            if (!lockAnnotation.timeoutException().isInstance(e)) {
                throw lockAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            io.github.snower.jaslock.spring.boot.annotations.Lock lockAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.Lock) keyEvaluate.getTargetParameter();
            if (!lockAnnotation.exception().isInstance(e)) {
                throw lockAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }
}
