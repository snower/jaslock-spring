package io.github.snower.jaslock.spring.aspects;

import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.AbstractBaseAspect;
import io.github.snower.jaslock.spring.SlockTemplate;
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

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class LockAspect extends AbstractBaseAspect {
    private static final Logger logger = LoggerFactory.getLogger(LockAspect.class);

    public LockAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.annotations.Lock)")
    private void Lock() {}

    @Around("Lock()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        io.github.snower.jaslock.spring.annotations.Lock lockAnnotation = methodSignature.getMethod().getAnnotation(io.github.snower.jaslock.spring.annotations.Lock.class);
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
        String key = evaluateKey(templateKey, methodSignature.getMethod(), methodJoinPoint.getArgs(), methodJoinPoint.getTarget());
        Lock lock = lockAnnotation.databaseId() >= 0 && lockAnnotation.databaseId() < 127 ?
                slockTemplate.selectDatabase(lockAnnotation.databaseId())
                        .newLock(key, lockAnnotation.timeout(), lockAnnotation.expried()) :
                slockTemplate.newLock(key, lockAnnotation.timeout(), lockAnnotation.expried());
        try {
            lock.acquire();
            try {
                return joinPoint.proceed();
            } finally {
                try {
                    lock.release();
                } catch (Exception e) {
                    logger.warn("Slock LockAspect release {} error {}", templateKey, e, e);
                }
            }
        } catch (LockTimeoutException e) {
            if (!lockAnnotation.timeoutException().isInstance(e)) {
                throw lockAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            if (!lockAnnotation.exception().isInstance(e)) {
                throw lockAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }
}
