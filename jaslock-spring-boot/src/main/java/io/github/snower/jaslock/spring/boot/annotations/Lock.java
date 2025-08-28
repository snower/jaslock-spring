package io.github.snower.jaslock.spring.boot.annotations;

import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {
    @AliasFor("key")
    String value() default "";

    @AliasFor("value")
    String key() default "";

    int timeout() default 5;

    int expried() default 120;

    byte databaseId() default -1;

    int timeoutFlag() default 0;

    int expriedFlag() default 0;

    Class<? extends Exception> timeoutException() default LockTimeoutException.class;

    Class<? extends Exception> exception() default SlockException.class;
}
