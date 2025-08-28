package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.MaxConcurrentFlow;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 8000)
public class MaxConcurrentFlowsAspect extends AbstractMultiBaseAspect {
    private static final Logger logger = LoggerFactory.getLogger(MaxConcurrentFlowsAspect.class);

    public MaxConcurrentFlowsAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlows)")
    private void MaxConcurrentFlows() {}

    @Around("MaxConcurrentFlows()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        AbstractMultiBaseAspect.MultiEvaluates keyEvaluate = (AbstractMultiBaseAspect.MultiEvaluates) keyEvaluateCache.get(method);
        if (keyEvaluate == null) {
            io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlows maxConcurrentFlowAnnotations =
                    method.getAnnotation(io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlows.class);
            if (maxConcurrentFlowAnnotations == null || maxConcurrentFlowAnnotations.value() == null || maxConcurrentFlowAnnotations.value().length == 0) {
                throw new IllegalArgumentException("MaxConcurrentFlows is empty");
            }
            keyEvaluate = compileKeyEvaluates(method, maxConcurrentFlowAnnotations.value());
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
        io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow maxConcurrentFlowAnnotation =
                (io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow) annotation;
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
        return templateKey;
    }

    @Override
    protected KeyEvaluate configureKeyEvaluate(Annotation annotation, KeyEvaluate keyEvaluate) {
        io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow maxConcurrentFlowAnnotation =
                (io.github.snower.jaslock.spring.boot.annotations.MaxConcurrentFlow) annotation;
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
        return keyEvaluate;
    }

    @Override
    protected Object execute(ProceedingJoinPoint joinPoint, KeyEvaluate[] keyEvaluates, String[] keys, int index) throws Throwable {
        KeyEvaluate keyEvaluate = keyEvaluates[index];
        String key = keys[index];
        MaxConcurrentFlow maxConcurrentFlow = (MaxConcurrentFlow) keyEvaluate.buildTargetInstance(key);
        try {
            maxConcurrentFlow.acquire();
            try {
                return proceed(joinPoint, keyEvaluates, keys, index + 1);
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
