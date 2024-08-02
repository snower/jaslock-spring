package io.github.snower.jaslock.spring;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;

public class KeyEvaluationContext extends MethodBasedEvaluationContext {
    protected final EvaluationContext parentContext;

    public KeyEvaluationContext(Object rootObject, Method method, Object[] arguments, ParameterNameDiscoverer parameterNameDiscoverer,
                                EvaluationContext parentContext) {
        super(rootObject, method, arguments, parameterNameDiscoverer);
        setPropertyAccessors(parentContext.getPropertyAccessors());
        setTypeLocator(parentContext.getTypeLocator());
        setBeanResolver(parentContext.getBeanResolver());
        this.parentContext = parentContext;
    }

    @Nullable
    public Object lookupVariable(String name) {
        Object value = super.lookupVariable(name);
        if(value == null && parentContext != null) {
            return parentContext.lookupVariable(name);
        }
        return value;
    }
}
