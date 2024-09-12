# jaslock-spring

[![Tests](https://img.shields.io/github/actions/workflow/status/snower/jaslock/build-test.yml?label=tests)](https://github.com/snower/jaslock-spring/actions/workflows/build-test.yml)
[![GitHub Repo stars](https://img.shields.io/github/stars/snower/jaslock-spring?style=social)](https://github.com/snower/jaslock-spring/stargazers)

High-performance distributed sync service and atomic DB. Provides good multi-core support through lock queues, high-performance asynchronous binary network protocols. Can be used for spikes, synchronization, event notification, concurrency control. https://github.com/snower/slock

# Install

```xml
<dependency>
    <groupId>io.github.snower</groupId>
    <artifactId>jaslock-spring-boot-starter</artifactId>
    <version>0.1.1</version>
</dependency>
```

# Configuration

```yaml
spring:
  slock:
    enabled: true # 是否启用，默认是
    # 连接URL
    #url: 'slock://127.0.0.1:5658,127.0.0.1:5659/?database=0&executorWorkerCount=1&executorMaxWorkerCount=2&executorMaxCapacity=2147483647&executorWorkerKeepAliveTime=7200&defaultTimeoutFlag=0&defaultExpriedFlag=0'
    host: '127.0.0.1' # 连接Host
    port: 5658  # 连接端口
    #hosts: 127.0.0.1:5658,127.0.0.1:5659 # 集群连接Host列表
    # databaseId: 0 # 连接DB
    # executor: # 异步执行线程池配置
    #   workerCount: 1 # 异步执行线程池核心线程数
    #   maxWorkerCount: 2 # 异步执行线程池最大线程数
    #   maxCapacity: 2147483647 # 异步执行线程池最大任务数量
    #   workerKeepAliveTime: 7200 # 异步执行线程池KeepAlive时间
    #   workerKeepAliveTimeUnit: 'seconds' # 异步执行线程池KeepAlive单位
    # defaultTimeoutFlag: 0 # 默认超时时间Flag
    # defaultExpriedFlag: 0 # 默认过期时间Flag
```

# Useage

## Aspect Annotation

#### Lock 加锁

```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {
    @AliasFor("key")
    String value() default "";

    @AliasFor("value")
    String key() default ""; // 加锁Key，支持spel表达式

    int timeout() default 5; // 等待超时时间

    int expried() default 120; // 过期时间

    byte databaseId() default -1; // 使用DB，-1使用全局配置

    Class<? extends Exception> timeoutException() default LockTimeoutException.class; // 超时抛出异常类型

    Class<? extends Exception> exception() default SlockException.class; // 抛出其它异常类型
}
```

#### MaxConcurrentFlow 最大并发限流

```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaxConcurrentFlow {
    @AliasFor("key")
    String value() default "";

    @AliasFor("value")
    String key() default ""; // 限流Key，支持spel表达式

    short count() default 64; // 限流最大并发数

    int timeout() default 60; // 等待超时时间

    int expried() default 120; // 过期时间

    byte databaseId() default -1; // 使用DB，-1使用全局配置

    Class<? extends Exception> timeoutException() default LockTimeoutException.class; // 超时抛出异常类型

    Class<? extends Exception> exception() default SlockException.class; // 抛出其它异常类型
}
```

#### TokenBucketFlow 令牌桶限流

```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TokenBucketFlow {
    @AliasFor("key")
    String value() default "";

    @AliasFor("value")
    String key() default ""; // 限流Key，支持spel表达式

    short count() default 64; // 令牌数

    int timeout() default 60; // 加锁等待超时时间

    double period() default 0.1; // 过期周期，单位秒

    byte databaseId() default -1; // 使用DB，-1使用全局配置

    Class<? extends Exception> timeoutException() default LockTimeoutException.class; // 超时抛出异常类型

    Class<? extends Exception> exception() default SlockException.class; // 抛出其它异常类型
}
```

#### Idempotent 幂等

```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    @AliasFor("key")
    String value() default "";

    @AliasFor("value")
    String key() default ""; // 幂等Key，支持spel表达式

    int timeout() default 5; // 加锁等待超时时间

    int expried() default 300; // 加锁过期时间

    byte databaseId() default -1; // 使用DB，-1使用全局配置

    Class<? extends Exception> timeoutException() default LockTimeoutException.class; // 超时抛出异常类型

    Class<? extends Exception> exception() default SlockException.class; // 抛出其它异常类型
}
```

## EventFuture

分布式Event Future，用于等待及获取远程异步结果。

```java
package main;

import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.EventFuture;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.boot.SlockConfiguration;
import io.github.snower.jaslock.spring.boot.SlockSerializater;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class App {
    public static void main(String[] args) throws SlockException, IOException, ExecutionException, InterruptedException {
        SlockTemplate slockTemplate = new SlockTemplate(SlockConfiguration.newBuilder()
                .build(), new SlockSerializater.ObjectSerializater());
        EventFuture<String> eventFuture = slockTemplate.newEventFuture("testEventFuture");
        eventFuture.setResult("Test");
        eventFuture = slockTemplate.newEventFuture("testEventFuture");
        org.junit.Assert.assertEquals(eventFuture.get(), "Test");
        eventFuture.close();
        eventFuture = slockTemplate.newEventFuture("testEventFuture");
        try {
            org.junit.Assert.assertNull(eventFuture.get(0, TimeUnit.SECONDS));
            throw new SlockException();
        } catch (TimeoutException e) {
        }
    }
}
```

# License

slock uses the MIT license, see LICENSE file for the details.