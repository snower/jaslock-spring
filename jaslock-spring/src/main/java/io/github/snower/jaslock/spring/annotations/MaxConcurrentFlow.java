package io.github.snower.jaslock.spring.annotations;

import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaxConcurrentFlow {
    @AliasFor("key")
    String value() default "";

    @AliasFor("value")
    String key() default "";

    short count() default 64;

    int timeout() default 60;

    int expried() default 120;

    byte databaseId() default -1;

    Class<? extends Exception> timeoutException() default LockTimeoutException.class;

    Class<? extends Exception> exception() default SlockException.class;
}
