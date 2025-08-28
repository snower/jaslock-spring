package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.TokenBucketFlow;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.AbstractMultiBaseAspect;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 7000)
public class TokenBucketFlowsAspect extends AbstractMultiBaseAspect {
    public TokenBucketFlowsAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlows)")
    private void TokenBucketFlows() {}

    @Around("TokenBucketFlows()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        AbstractMultiBaseAspect.MultiEvaluates keyEvaluate = (AbstractMultiBaseAspect.MultiEvaluates) keyEvaluateCache.get(method);
        if (keyEvaluate == null) {
            io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlows tokenBucketFlowAnnotations =
                    method.getAnnotation(io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlows.class);
            if (tokenBucketFlowAnnotations == null || tokenBucketFlowAnnotations.value() == null || tokenBucketFlowAnnotations.value().length == 0) {
                throw new IllegalArgumentException("TokenBucketFlows is empty");
            }
            keyEvaluate = compileKeyEvaluates(method, tokenBucketFlowAnnotations.value());
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
        io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow tokenBucketFlowAnnotation =
                (io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow) annotation;
        if (tokenBucketFlowAnnotation == null) {
            throw new IllegalArgumentException("unknown TokenBucketFlow annotation");
        }
        String templateKey = tokenBucketFlowAnnotation.value();
        if (isBlank(templateKey)) {
            templateKey = tokenBucketFlowAnnotation.key();
            if (isBlank(templateKey)) {
                throw new IllegalArgumentException("key is empty");
            }
        }
        return templateKey;
    }

    @Override
    protected KeyEvaluate configureKeyEvaluate(Annotation annotation, KeyEvaluate keyEvaluate) {
        io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow tokenBucketFlowAnnotation =
                (io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow) annotation;
        keyEvaluate.setTargetParameter(tokenBucketFlowAnnotation);
        int timeout = tokenBucketFlowAnnotation.timeout() | (tokenBucketFlowAnnotation.timeoutFlag() << 16);
        double period = tokenBucketFlowAnnotation.period();
        short count = tokenBucketFlowAnnotation.count();
        byte databaseId = tokenBucketFlowAnnotation.databaseId();
        if (databaseId >= 0 && databaseId < 127) {
            keyEvaluate.setTargetInstanceBuilder(key -> slockTemplate.selectDatabase(databaseId)
                    .newTokenBucketFlow((String) key, count, timeout, period));
        }
        keyEvaluate.setTargetInstanceBuilder(key -> slockTemplate.newTokenBucketFlow((String) key, count, timeout, period));
        return keyEvaluate;
    }

    @Override
    protected Object execute(ProceedingJoinPoint joinPoint, KeyEvaluate[] keyEvaluates, String[] keys, int index) throws Throwable {
        KeyEvaluate keyEvaluate = keyEvaluates[index];
        String key = keys[index];
        TokenBucketFlow tokenBucketFlow = (TokenBucketFlow) keyEvaluate.buildTargetInstance(key);
        try {
            tokenBucketFlow.acquire();
            return proceed(joinPoint, keyEvaluates, keys, index + 1);
        } catch (LockTimeoutException e) {
            io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow tokenBucketFlowAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow) keyEvaluate.getTargetParameter();
            if (!tokenBucketFlowAnnotation.timeoutException().isInstance(e)) {
                throw tokenBucketFlowAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow tokenBucketFlowAnnotation =
                    (io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow) keyEvaluate.getTargetParameter();
            if (!tokenBucketFlowAnnotation.exception().isInstance(e)) {
                throw tokenBucketFlowAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }
}
