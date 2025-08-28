package io.github.snower.jaslock.spring.boot.aspects;

import io.github.snower.jaslock.TokenBucketFlow;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.AbstractBaseAspect;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 7000)
public class TokenBucketFlowAspect extends AbstractBaseAspect {
    public TokenBucketFlowAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    @Pointcut("@annotation(io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow)")
    private void TokenBucketFlow() {}

    @Around("TokenBucketFlow()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodInvocationProceedingJoinPoint methodJoinPoint = (MethodInvocationProceedingJoinPoint) joinPoint;
        MethodSignature methodSignature = (MethodSignature) methodJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        KeyEvaluate keyEvaluate = keyEvaluateCache.get(method);
        if (keyEvaluate == null) {
            io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow tokenBucketFlowAnnotation = methodSignature.getMethod()
                    .getAnnotation(io.github.snower.jaslock.spring.boot.annotations.TokenBucketFlow.class);
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
            keyEvaluate = compileKeyEvaluate(methodSignature.getMethod(), templateKey);
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
        }
        String key = keyEvaluate.evaluate(methodSignature.getMethod(), methodJoinPoint.getArgs(), methodJoinPoint.getThis());
        TokenBucketFlow tokenBucketFlow = (TokenBucketFlow) keyEvaluate.buildTargetInstance(key);
        try {
            tokenBucketFlow.acquire();
            return joinPoint.proceed();
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
