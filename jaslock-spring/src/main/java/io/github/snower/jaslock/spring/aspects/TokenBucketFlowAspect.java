package io.github.snower.jaslock.spring.aspects;

import io.github.snower.jaslock.TokenBucketFlow;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.AbstractBaseAspect;
import io.github.snower.jaslock.spring.SlockTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 80)
public class TokenBucketFlowAspect extends AbstractBaseAspect {
    public TokenBucketFlowAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.annotations.TokenBucketFlow)")
    private void TokenBucketFlow() {}

    @Around("TokenBucketFlow()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        io.github.snower.jaslock.spring.annotations.TokenBucketFlow tokenBucketFlowAnnotation = methodSignature.getMethod()
                .getAnnotation(io.github.snower.jaslock.spring.annotations.TokenBucketFlow.class);
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
        String key = evaluateKey(templateKey, methodSignature.getMethod(), methodJoinPoint.getArgs(), methodJoinPoint.getTarget());
        TokenBucketFlow tokenBucketFlow = tokenBucketFlowAnnotation.databaseId() >= 0 && tokenBucketFlowAnnotation.databaseId() < 127 ?
                slockTemplate.selectDatabase(tokenBucketFlowAnnotation.databaseId())
                        .newTokenBucketFlow(key, tokenBucketFlowAnnotation.count(),
                                tokenBucketFlowAnnotation.timeout(), tokenBucketFlowAnnotation.period()) :
                slockTemplate.newTokenBucketFlow(key, tokenBucketFlowAnnotation.count(),
                        tokenBucketFlowAnnotation.timeout(), tokenBucketFlowAnnotation.period());
        try {
            tokenBucketFlow.acquire();
            return joinPoint.proceed();
        } catch (LockTimeoutException e) {
            if (!tokenBucketFlowAnnotation.timeoutException().isInstance(e)) {
                throw tokenBucketFlowAnnotation.timeoutException().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        } catch (SlockException e) {
            if (!tokenBucketFlowAnnotation.exception().isInstance(e)) {
                throw tokenBucketFlowAnnotation.exception().getConstructor(String.class, Throwable.class)
                        .newInstance(e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }
}
