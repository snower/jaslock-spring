package io.github.snower.jaslock.spring.boot.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LockWithTransactions {
    LockWithTransaction[] value();
}
