package io.github.snower.jaslock.spring.boot;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.*;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractBaseAspect implements BeanFactoryAware, ApplicationContextAware {
    protected final static StandardReflectionParameterNameDiscoverer parameterNameDiscoverer = new StandardReflectionParameterNameDiscoverer();
    protected final SlockTemplate slockTemplate;
    protected BeanFactory beanFactory;
    protected ApplicationContext applicationContext;
    protected SpelExpressionParser spelExpressionParser;
    protected StandardEvaluationContext applicationEvaluationContext;
    protected Map<Method, KeyEvaluate> keyEvaluateCache = new ConcurrentHashMap<>();

    protected AbstractBaseAspect(SlockTemplate slockTemplate) {
        this.slockTemplate = slockTemplate;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        this.spelExpressionParser = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.MIXED,
                beanFactory.getClass().getClassLoader()));
        if (applicationContext != null && applicationEvaluationContext == null) {
            this.buildApplicationEvaluationContext();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        if (beanFactory != null && applicationEvaluationContext == null) {
            this.buildApplicationEvaluationContext();
        }
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void buildApplicationEvaluationContext() {
        applicationEvaluationContext = new StandardEvaluationContext(applicationContext);
        applicationEvaluationContext.addPropertyAccessor(new BeanExpressionContextAccessor());
        applicationEvaluationContext.addPropertyAccessor(new BeanFactoryAccessor());
        applicationEvaluationContext.addPropertyAccessor(new MapAccessor());
        applicationEvaluationContext.addPropertyAccessor(new EnvironmentAccessor());
        applicationEvaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
        applicationEvaluationContext.setTypeLocator(new StandardTypeLocator(beanFactory.getClass().getClassLoader()));
    }

    public static Method getMethod(Class<?> clazz, String filedName) {
        return ReflectionUtils.findMethod(clazz, "get" + filedName.substring(0, 1)
                .toUpperCase() + filedName.substring(1));
    }

    protected KeyEvaluate compileKeyEvaluate(Method method, String templateKey) {
        return compileKeyEvaluate(method, templateKey, null);
    }

    protected KeyEvaluate compileKeyEvaluate(Method method, String templateKey, Function<? super KeyEvaluate, ? extends KeyEvaluate> initFunction) {
        return keyEvaluateCache.computeIfAbsent(method, k -> initFunction != null ?
                initFunction.apply(doCompileKeyEvaluate(method, templateKey)) : doCompileKeyEvaluate(method, templateKey));
    }

    protected KeyEvaluate doCompileKeyEvaluate(Method method, String templateKey) {
        for (int i = 0; i < templateKey.length(); i++) {
            char c = templateKey.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-')
                continue;
            if (c != '{' && c != '}' && c != '.') {
                return new SPELKeyEvaluate(parameterNameDiscoverer, applicationContext, applicationEvaluationContext,
                        spelExpressionParser.parseExpression(templateKey, ParserContext.TEMPLATE_EXPRESSION));
            }
            return compileValueGetterKeyEvaluate(method, templateKey);
        }
        return new ConstKeyEvaluate(templateKey);
    }

    protected KeyEvaluate compileValueGetterKeyEvaluate(Method method, String templateKey) {
        Parameter[] parameters = method.getParameters();
        List<IValueGetter> valueGetters = new ArrayList<>();
        boolean isGetterKey = false;
        int startIndex = 0;
        for (int i = 0; i < templateKey.length(); i++) {
            char c = templateKey.charAt(i);
            switch (c) {
                case '{':
                    if (startIndex < i) {
                        valueGetters.add(new ConstValueGetter(templateKey.substring(startIndex, i)));
                    }
                    startIndex = i + 1;
                    isGetterKey = true;
                    break;
                case '}':
                    if (startIndex < i) {
                        if (isGetterKey) {
                            String argName = templateKey.substring(startIndex, i);
                            if (argName.isEmpty()) {
                                throw new IllegalArgumentException("Invalid template key: " + templateKey);
                            }
                            String[] argKeyInfo = argName.split(":");
                            String[] argKeys = argKeyInfo[0].split("\\.");
                            String argKey = argKeys[0];
                            String fieldKey = argKeys.length >= 2 ? argKeys[1] : null;
                            String defaultValue = argKeyInfo.length >= 2 ? argKeyInfo[1] : "null";
                            String[] getterNames = argKeys.length >= 3 ? Arrays.copyOfRange(argKeys, 2, argKeys.length) : null;

                            Parameter argParameter = null;
                            int argIndex = 0;
                            for (int j = 0; j < parameters.length; j++) {
                                Parameter parameter = parameters[j];
                                if (Objects.equals(parameter.getName(), argKey)) {
                                    argParameter = parameter;
                                    argIndex = j;
                                    break;
                                }
                            }
                            if (argParameter == null) {
                                for (int j = 0; j < parameters.length; j++) {
                                    Parameter parameter = parameters[j];
                                    if (Objects.equals(argKey, "arg" + j) || Objects.equals(argKey, "p" + j)) {
                                        argParameter = parameter;
                                        argIndex = j;
                                        break;
                                    }
                                }
                            }

                            if (argParameter == null) {
                                throw new IllegalArgumentException("unknown parameter: " + templateKey.substring(startIndex, i)
                                        + ", expected: " + Arrays.stream(parameters).map(Parameter::getName)
                                        .collect(Collectors.joining(",")));
                            } else if (fieldKey == null) {
                                valueGetters.add(new IndexValueGetter(argIndex, defaultValue));
                            } else {
                                Class<?> argClass = argParameter.getType();
                                if (Map.class.isAssignableFrom(argClass)) {
                                    valueGetters.add(new MapValueGetter(fieldKey, argIndex, defaultValue, getterNames));
                                } else {
                                    Method argGetterMethod = getMethod(argClass, fieldKey);
                                    if (argGetterMethod != null) {
                                        valueGetters.add(new MethodValueGetter(argGetterMethod, argIndex, defaultValue,
                                                compileValueGetterFieldGetters(argGetterMethod.getReturnType(), fieldKey, getterNames)));
                                    } else {
                                        Field field = ReflectionUtils.findField(argClass, fieldKey);
                                        if (field != null) {
                                            valueGetters.add(new FieldValueGetter(field, argIndex, defaultValue,
                                                    compileValueGetterFieldGetters(field.getDeclaringClass(), argName, getterNames)));
                                        } else {
                                            throw new IllegalArgumentException("unknown parameter: " + argName);
                                        }
                                    }
                                }
                            }
                        } else {
                            valueGetters.add(new ConstValueGetter(templateKey.substring(startIndex, i)));
                        }
                    }
                    startIndex = i + 1;
                    isGetterKey = false;
                    break;
            }
        }
        if (startIndex < templateKey.length()) {
            valueGetters.add(new ConstValueGetter(templateKey.substring(startIndex)));
        }
        if (valueGetters.size() == 1) {
            return new ValueGetterKeyEvaluate(valueGetters.get(0));
        }
        return new ValueGettersKeyEvaluate(valueGetters.toArray(new IValueGetter[0]));
    }

    public IFieldGetter[] compileValueGetterFieldGetters(Class<?> argClass, String argName, String[] getterNames) {
        if (getterNames == null || getterNames.length == 0) return null;
        List<IFieldGetter> fieldGetters = new ArrayList<>();
        for (int i = 0; i < getterNames.length; i++) {
            String getterName = getterNames[i];
            if (Map.class.isAssignableFrom(argClass)) {
                fieldGetters.add(new MapFieldGetter(getterName, Arrays.copyOfRange(getterNames, i + 1, getterNames.length)));
                return fieldGetters.toArray(new IFieldGetter[0]);
            } else {
                Method argGetterMethod = getMethod(argClass, getterName);
                if (argGetterMethod != null) {
                    fieldGetters.add(new MethodFieldGetter(argGetterMethod));
                    argClass = argGetterMethod.getReturnType();
                } else {
                    Field field = ReflectionUtils.findField(argClass, getterName);
                    if (field != null) {
                        fieldGetters.add(new FieldFieldGetter(field));
                        argClass = field.getType();
                    } else {
                        throw new IllegalArgumentException("unknown parameter: " + argName);
                    }
                }
            }
        }
        return fieldGetters.isEmpty() ? null : fieldGetters.toArray(new IFieldGetter[0]);
    }

    @FunctionalInterface
    public interface KeyEvaluate {
        String evaluate(Method method, Object[] args, Object target);
        default void setTargetParameter(Object targetParameter) {

        }
        default Object getTargetParameter() {
            return null;
        }
        default void setTargetInstanceBuilder(Function<Object, Object> builder) {

        }
        default Object buildTargetInstance(Object arg) {
            return null;
        }
    }

    public static abstract class AbstractKeyEvaluate implements KeyEvaluate {
        private Object  targetParameter;
        private Function<Object, Object> targetInstanceBuilder;

        @Override
        public void setTargetParameter(Object parameter) {
            this.targetParameter = parameter;
        }

        @Override
        public Object getTargetParameter() {
            return this.targetParameter;
        }

        @Override
        public void setTargetInstanceBuilder(Function<Object, Object> builder) {
            this.targetInstanceBuilder = builder;
        }

        @Override
        public Object buildTargetInstance(Object arg) {
            return this.targetInstanceBuilder.apply(arg);
        }
    }

    public static class ConstKeyEvaluate extends AbstractKeyEvaluate {
        private final String value;

        public ConstKeyEvaluate(String value) {
            this.value = value;
        }

        @Override
        public String evaluate(Method method, Object[] args, Object target) {
            return value;
        }
    }

    public static class SPELKeyEvaluate extends AbstractKeyEvaluate {
        private final StandardReflectionParameterNameDiscoverer parameterNameDiscoverer;
        private final ApplicationContext applicationContext;
        private final StandardEvaluationContext applicationEvaluationContext;
        private final Expression expression;

        protected SPELKeyEvaluate(StandardReflectionParameterNameDiscoverer parameterNameDiscoverer, ApplicationContext applicationContext, StandardEvaluationContext applicationEvaluationContext, Expression expression) {
            this.parameterNameDiscoverer = parameterNameDiscoverer;
            this.applicationContext = applicationContext;
            this.applicationEvaluationContext = applicationEvaluationContext;
            this.expression = expression;
        }

        @Override
        public String evaluate(Method method, Object[] args, Object target) {
            KeyEvaluationContext evaluationContext = new KeyEvaluationContext(target, method, args,
                    parameterNameDiscoverer, applicationEvaluationContext);
            evaluationContext.setVariable("environment", applicationContext.getEnvironment());
            return expression.getValue(evaluationContext, String.class);
        }
    }

    public static class ValueGetterKeyEvaluate extends AbstractKeyEvaluate {
        private final IValueGetter valueGetter;

        protected ValueGetterKeyEvaluate(IValueGetter valueGetter) {
            this.valueGetter = valueGetter;
        }

        @Override
        public String evaluate(Method method, Object[] args, Object target) {
            return valueGetter.getValue(args);
        }
    }

    public static class ValueGettersKeyEvaluate extends AbstractKeyEvaluate {
        private final IValueGetter[] valueGetters;

        protected ValueGettersKeyEvaluate(IValueGetter[] valueGetters) {
            this.valueGetters = valueGetters;
        }

        @Override
        public String evaluate(Method method, Object[] args, Object target) {
            StringBuilder stringBuilder = new StringBuilder();
            for (IValueGetter valueGetter : valueGetters) {
                stringBuilder.append(valueGetter.getValue(args));
            }
            return stringBuilder.toString();
        }
    }

    @FunctionalInterface
    public interface IValueGetter {
        String getValue(Object[] args);
    }

    public static class ConstValueGetter implements IValueGetter {
        private final String value;

        public ConstValueGetter(String value) {
            this.value = value;
        }

        @Override
        public String getValue(Object[] args) {
            return value;
        }
    }

    public static class IndexValueGetter implements IValueGetter {
        private final int argIndex;
        private final String defaultValue;

        public IndexValueGetter(int argIndex, String defaultValue) {
            this.argIndex = argIndex;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getValue(Object[] args) {
            Object value = args[argIndex] == null ? null : args[argIndex];
            if (value == null) return defaultValue;
            return value.toString();
        }
    }

    public static class MethodValueGetter implements IValueGetter {
        private final Method method;
        private final int argIndex;
        private final String defaultValue;
        private final IFieldGetter[] fieldGetters;

        public MethodValueGetter(Method method, int argIndex, String defaultValue, IFieldGetter[] fieldGetters) {
            this.method = method;
            this.argIndex = argIndex;
            this.defaultValue = defaultValue;
            this.fieldGetters = fieldGetters;
        }

        @Override
        public String getValue(Object[] args) {
            try {
                Object value = args[argIndex] == null ? null : method.invoke(args[argIndex]);
                if (value == null) return defaultValue;
                if (fieldGetters != null) {
                    for (IFieldGetter fieldGetter : fieldGetters) {
                        value = fieldGetter.getValue(value);
                        if (value == null) return defaultValue;
                    }
                }
                return value.toString();
            } catch (IllegalAccessException | InvocationTargetException ex) {
                return defaultValue;
            }
        }
    }

    public static class FieldValueGetter implements IValueGetter {
        private final Field field;
        private final int argIndex;
        private final String defaultValue;
        private final IFieldGetter[] fieldGetters;

        public FieldValueGetter(Field field, int argIndex, String defaultValue, IFieldGetter[] fieldGetters) {
            this.field = field;
            this.argIndex = argIndex;
            this.defaultValue = defaultValue;
            this.fieldGetters = fieldGetters;
        }

        @Override
        public String getValue(Object[] args) {
            try {
                Object value = args[argIndex] == null ? null : field.get(args[argIndex]).toString();
                if (value == null) return defaultValue;
                if (fieldGetters != null) {
                    for (IFieldGetter fieldGetter : fieldGetters) {
                        value = fieldGetter.getValue(value);
                        if (value == null) return defaultValue;
                    }
                }
                return value.toString();
            } catch (IllegalAccessException e) {
                return defaultValue;
            }
        }
    }

    public static class MapValueGetter implements IValueGetter {
        private final String key;
        private final int argIndex;
        private final String defaultValue;
        private final String[] getterNames;
        private final Map<String, ConcurrentHashMap<Class<?>, Method>> getterMethodCache = new ConcurrentHashMap<>();
        private final Map<String, ConcurrentHashMap<Class<?>, Field>> getterFieldCache = new ConcurrentHashMap<>();

        public MapValueGetter(String key, int argIndex, String defaultValue, String[] getterNames) {
            this.key = key;
            this.argIndex = argIndex;
            this.defaultValue = defaultValue;
            this.getterNames = getterNames;
        }

        @Override
        public String getValue(Object[] args) {
            Object value = args[argIndex] == null ? null : ((Map<?, ?>) args[argIndex]).get(key);
            if (value == null) return defaultValue;
            if (getterNames != null) {
                for (String getterName : getterNames) {
                    try {
                        Class<?> valueClass = value.getClass();
                        if (Map.class.isAssignableFrom(valueClass)) {
                            value = ((Map<?, ?>) value).get(getterName);
                        } else {
                            Method method = getMethod(valueClass, getterName);
                            if (method == null) {
                                Field field = getField(valueClass, getterName);
                                if (field == null) {
                                    return defaultValue;
                                }
                                value = field.get(value);
                            } else {
                                value = method.invoke(value);
                            }
                        }
                        if (value == null) return defaultValue;
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        return defaultValue;
                    }
                }
            }
            return value.toString();
        }

        public Method getMethod(Class<?> clazz, String filedName) {
            return getterMethodCache.computeIfAbsent(filedName, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(clazz, k2 -> ReflectionUtils.findMethod(clazz,
                            "get" + filedName.substring(0, 1).toUpperCase() + filedName.substring(1)));
        }

        public Field getField(Class<?> clazz, String filedName) {
            return getterFieldCache.computeIfAbsent(filedName, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(clazz, k2 -> ReflectionUtils.findField(clazz, filedName));
        }
    }

    @FunctionalInterface
    public interface IFieldGetter {
        Object getValue(Object arg);
    }

    public static class MethodFieldGetter implements IFieldGetter {
        private final Method method;

        public MethodFieldGetter(Method method) {
            this.method = method;
        }

        @Override
        public Object getValue(Object arg) {
            try {
                return arg == null ? null : method.invoke(arg);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                return null;
            }
        }
    }

    public static class FieldFieldGetter implements IFieldGetter {
        private final Field field;

        public FieldFieldGetter(Field field) {
            this.field = field;
        }

        @Override
        public Object getValue(Object arg) {
            try {
                return arg == null ? null : field.get(arg);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
    }

    public static class MapFieldGetter implements IFieldGetter {
        private final String key;
        private final String[] getterNames;
        private final Map<String, ConcurrentHashMap<Class<?>, Method>> getterMethodCache = new ConcurrentHashMap<>();
        private final Map<String, ConcurrentHashMap<Class<?>, Field>> getterFieldCache = new ConcurrentHashMap<>();

        public MapFieldGetter(String key, String[] getterNames) {
            this.key = key;
            this.getterNames = getterNames == null || getterNames.length == 0 ? null : getterNames;
        }

        @Override
        public Object getValue(Object arg) {
            Object value = arg == null ? null : ((Map<?, ?>) arg).get(key);
            if (value == null) return null;
            if (getterNames == null) return value;
            for (String getterName : getterNames) {
                try {
                    Class<?> valueClass = value.getClass();
                    if (Map.class.isAssignableFrom(valueClass)) {
                        value = ((Map<?, ?>) value).get(getterName);
                    } else {
                        Method method = getMethod(valueClass, getterName);
                        if (method == null) {
                            Field field = getField(valueClass, getterName);
                            if (field == null) {
                                return null;
                            }
                            value = field.get(value);
                        } else {
                            value = method.invoke(value);
                        }
                    }
                    if (value == null) return null;
                } catch (InvocationTargetException | IllegalAccessException e) {
                    return null;
                }
            }
            return value;
        }

        public Method getMethod(Class<?> clazz, String filedName) {
            return getterMethodCache.computeIfAbsent(filedName, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(clazz, k2 -> ReflectionUtils.findMethod(clazz,
                            "get" + filedName.substring(0, 1).toUpperCase() + filedName.substring(1)));
        }

        public Field getField(Class<?> clazz, String filedName) {
            return getterFieldCache.computeIfAbsent(filedName, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(clazz, k2 -> ReflectionUtils.findField(clazz, filedName));
        }
    }
}
