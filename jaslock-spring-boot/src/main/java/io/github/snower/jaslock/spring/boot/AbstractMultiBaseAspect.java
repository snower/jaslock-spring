package io.github.snower.jaslock.spring.boot;

import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.function.Function;

public abstract class AbstractMultiBaseAspect extends AbstractBaseAspect {
    protected AbstractMultiBaseAspect(SlockTemplate slockTemplate) {
        super(slockTemplate);
    }

    protected MultiEvaluates compileKeyEvaluates(Method method, Annotation[] annotations) {
        return (MultiEvaluates) keyEvaluateCache.computeIfAbsent(method, k -> doCompileKeyEvaluates(method, annotations));
    }

    protected MultiEvaluates doCompileKeyEvaluates(Method method, Annotation[] annotations) {
        KeyEvaluate[] keyEvaluates = new KeyEvaluate[annotations.length];
        for (int i = 0; i < annotations.length; i++) {
            keyEvaluates[i] = configureKeyEvaluate(annotations[i], doCompileKeyEvaluate(method, getTemplateKey(annotations[i])));
        }
        return new MultiEvaluates(keyEvaluates);
    }

    protected Object proceed(ProceedingJoinPoint joinPoint, KeyEvaluate[] keyEvaluates, String[] keys, int index) throws Throwable {
        if (index >= keyEvaluates.length) return joinPoint.proceed();
        return execute(joinPoint, keyEvaluates, keys, index);
    }

    protected abstract String getTemplateKey(Annotation annotation);

    protected abstract KeyEvaluate configureKeyEvaluate(Annotation annotation, KeyEvaluate keyEvaluate);

    protected abstract Object execute(ProceedingJoinPoint joinPoint, KeyEvaluate[] keyEvaluates, String[] keys, int index) throws Throwable;

    public static class MultiEvaluates implements KeyEvaluate {
        private final KeyEvaluate[] keyEvaluates;

        public MultiEvaluates(KeyEvaluate[] keyEvaluates) {
            this.keyEvaluates = keyEvaluates;
        }

        @Override
        public String evaluate(Method method, Object[] args, Object target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTargetParameter(Object targetParameter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getTargetParameter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTargetInstanceBuilder(Function<Object, Object> builder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object buildTargetInstance(Object arg) {
            throw new UnsupportedOperationException();
        }

        public KeyEvaluate[] getKeyEvaluates() {
            return keyEvaluates;
        }
    }
}
