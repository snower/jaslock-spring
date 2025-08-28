package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.MaxConcurrentFlow;
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

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 8000)
public class MaxConcurrentFlowAspect extends AbstractBaseAspect {
    private static final Logger logger = LoggerFactory.getLogger(MaxConcurrentFlowAspect.class);

    public MaxConcurrentFlowAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow)")
    private void MaxConcurrentFlow() {}

    @Around("MaxConcurrentFlow()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        KeyEvaluate keyEvaluate = keyEvaluateCache.get(method);
        if (keyEvaluate == null) {
            io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow maxConcurrentFlowAnnotation = methodSignature.getMethod()
                    .getAnnotation(io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow.class);
            if (maxConcurrentFlowAnnotation == null) {
                throw new IllegalArgumentException("unknown MaxConcurrentFlow annotation");
            }
            String templateKey = maxConcurrentFlowAnnotation.value();
            if (isBlank(templateKey)) {
                templateKey = maxConcurrentFlowAnnotation.key();
                if (isBlank(templateKey)) {
                    throw new IllegalArgumentException("key is empty");
                }
            }
            keyEvaluate = compileKeyEvaluate(methodSignature.getMethod(), templateKey);
            keyEvaluate.setTargetParameter(maxConcurrentFlowAnnotation);
            int timeout = maxConcurrentFlowAnnotation.timeout() | (maxConcurrentFlowAnnotation.timeoutFlag() << 16);
            int expried = maxConcurrentFlowAnnotation.expried() | (maxConcurrentFlowAnnotation.expriedFlag() << 16);
            short count = maxConcurrentFlowAnnotation.count();
            byte databaseId = maxConcurrentFlowAnnotation.databaseId();
            if (databaseId >= 0 && databaseId < 127) {
                keyEvaluate.setTargetInstanceBuilder(key -> slockTemplate.selectDatabase(databaseId)
                        .newMaxConcurrentFlow((String) key, count, timeout, expried));
            }
            keyEvaluate.setTargetInstanceBuilder(key -> slockTemplate.newMaxConcurrentFlow((String) key, count, timeout, expried));
        }
        String key = keyEvaluate.evaluate(methodSignature.getMethod(), methodJoinPoint.getArgs(), methodJoinPoint.getThis());
        MaxConcurrentFlow maxConcurrentFlow = (MaxConcurrentFlow) keyEvaluate.buildTargetInstance(key);
        try {
            maxConcurrentFlow.acquire();
            try {
                return joinPoint.proceed();
            } finally {
                try {
                    maxConcurrentFlow.release();
                } catch (Exception e) {
                    logger.warn("MaxConcurrentFlowAspect release {} error {}", key, e, e);
                }
            }
        } catch (LockTimeoutException e) {
            io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow maxConcurrentFlowAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow) keyEvaluate.getTargetParameter();
            if (!maxConcurrentFlowAnnotation.timeoutException().isInstance(e)) {
                throw maxConcurrentFlowAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow maxConcurrentFlowAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow) keyEvaluate.getTargetParameter();
            if (!maxConcurrentFlowAnnotation.exception().isInstance(e)) {
                throw maxConcurrentFlowAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }
}
