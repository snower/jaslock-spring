# jaslock-spring pubsub 示例

基于 [jaslock](https://github.com/snower/jaslock-spring) 实现的发布/订阅（pubsub）消息推送示例工程。演示如何用分布式锁的"事件通知"能力构建长轮询与 SSE 实时推送，**无需独立的 WebSocket / Push 服务组件**——订阅者直接阻塞在 slock 锁队列上，发布者通过 `Lock.releaseHead(data)` 唤醒并把消息随锁数据一并下发。

## 概述

本示例展示 jaslock 在 pubsub 场景下的四种典型用法：

| 用法 | 位置 | 作用 |
|---|---|---|
| `@Lock` 声明式锁 | `PubsubTopicService.createTopic` | SpEL Key 加锁，保证 Topic 创建幂等 |
| `SlockTemplate.newLock(...).with()` | `PubsubPublishTask`、`PubsubMessageController` | try-with-resources 编程式锁，串行化发布 |
| `ReadWriteLock` 读写锁 | `PubsubMessageService` | 发布写独占、订阅读共享，避免 messageId 重复 |
| 锁队列作为事件通知通道 | `PubsubController` + `PubsubMessageService.publish` | 订阅者阻塞在 `Lock.acquire(callback)`，发布者 `releaseHead(data)` 唤醒并附带消息体 |

第四种是本示例的核心：**把 slock 锁队列当作一个天然的 FIFO 等待队列**，发布者唤醒队首订阅者时把消息序列化进锁的 `LockSetData`，订阅者被唤醒后直接从锁数据反序列化出消息，无需再读库；只有发生超时竞态时才回退到 MongoDB 拉取。

## 技术栈

- **Spring Boot 4.1.0 / Java 21** —— Web 框架
- **MongoDB** —— 持久化 `PubsubTopic` 与 `PubsubMessage`
- **Redis (Spring Data Redis / Lettuce)** —— 缓存最新发布位点 `PublishDto(topicId, latestMessageId)`，TTL 1 天，加速热路径；通过 `StringRedisTemplate` 操作，`RedisCacheManager` 提供 Spring Cache 抽象
- **RabbitMQ** —— 异步发布任务队列（`PubsubPublishTask` 监听 `queue("pubsub")`）
- **jaslock-spring-boot-starter 0.1.4** —— 分布式锁 + 事件通知
- **wvkity/sequence 1.0.0** —— Snowflake 生成订阅者 `clientId`
- **springdoc-openapi 2.6.0** —— Swagger UI
- **Lombok** —— 样板代码消除

## 架构

```
                          ┌──────────────────────────────────────────────┐
                          │                 发布端                        │
                          │                                              │
              同步发布    │  POST /backend/pubsub/message/publish        │
        ─────────────────►│  PubsubMessageController                     │
                          │     │ requireLockKey → SlockTemplate.newLock │
                          │     │   .with()  ← try-with-resources 互斥   │
        异步发布 (MQ)      │     │ releaseLockKey → Lock.releaseHead()    │
        ─────────────────►│  PubsubPublishTask @RabbitListener           │
                          │     │   (同样的 lock + releaseHead 模式)       │
                          │     ▼                                        │
                          │  PubsubMessageService.publish                │
                          │     │ ReadWriteLock.acquireWrite             │
                          │     │ Redis 读最新位点 → Mongo 落库 → Redis 回填│
                          │     │ LockSetData(消息体)                     │
                          │     │ Lock.releaseHead(lockSetData) ◄─ 唤醒订阅│
                          │     │ Lock.acquire() 封锁下一个版本号           │
                          │     ▼ ReadWriteLock.releaseWrite             │
                          └──────────────────┬───────────────────────────┘
                                             │
                              slock 锁队列事件通知
                          (key: notification_pubsub_message_polling_<topic>)
                                             │
                                             ▼
                          ┌──────────────────────────────────────────────┐
                          │                 订阅端                        │
                          │                                              │
   长轮询   GET /api/pubsub/message/polling                              │
        ─────────────────►│  PubsubController.pollingMessages            │
                          │   DeferredResult (Spring 异步响应)            │
                          │   Lock.acquire(callback) 阻塞等待发布者唤醒    │
                          │     ├─ 被唤醒: 从 lock 数据反序列化消息 → 返回 │
                          │     └─ 超时: wakupLock 兜底 → 回退 fetchMessages│
                          │                                              │
   SSE     GET /api/pubsub/message/sse/polling (text/event-stream)        │
        ─────────────────►│  PubsubController.ssePollingMessages         │
                          │   SseEmitter                                 │
                          │   doSsePollingMessagesLoop 递归重投锁          │
                          │   事件: PushState / PushMessage / Ping / Error│
                          │   收到 messageName=="__done__" 时关闭流        │
                          └──────────────────────────────────────────────┘
```

## 项目结构

```
examples/pubsub/
├── pom.xml
├── src/main/
│   ├── resources/application.properties
│   └── java/io/github/snower/jaslock/spring/example/pubsub/
│       ├── PubsubApplication.java            # Spring Boot 入口
│       ├── config/
│       │   ├── RabbitConfiguration.java      # RabbitMQ Queue / DirectExchange / Binding
    │       │   └── RedisConfiguration.java       # @EnableCaching + RedisCacheManager (CachingConfigurerSupport)
│       ├── controller/
│       │   ├── api/PubsubController.java      # 订阅端：长轮询 + SSE
│       │   └── backend/
│       │       ├── PubsubTopicController.java # Topic 管理
│       │       └── PubsubMessageController.java # 发布 + 状态查询 + 拉取
│       ├── service/
│       │   ├── PubsubTopicService.java        # @Lock 幂等建 Topic
│       │   └── PubsubMessageService.java      # 核心：publish / getCurrentState / fetchMessages / encodeLockId
│       ├── task/
│       │   └── PubsubPublishTask.java         # @RabbitListener 异步发布消费者
│       ├── entity/
│       │   ├── BaseEntity.java                # Mongo 基类 (id/审计/乐观锁)
│       │   ├── PubsubTopic.java               # t_pubsub_topic
│       │   └── PubsubMessage.java             # t_pubsub_messages
│       ├── repository/
│       │   ├── PubsubTopicRepository.java     # MongoRepository
│       │   └── PubsubMessageRepository.java   # MongoRepository
│       └── dto/
│           ├── TopicDto.java
│           ├── PublishMessageDto.java         # 含 requireLockKey / releaseLockKey
│           ├── PollStateDto.java              # 订阅初始化位点
│           ├── PollMessageDto.java            # 订阅返回体（也作为锁数据载体）
│           └── MessageDto.java
```

## 数据模型

`BaseEntity`（Mongo `_id` 为 `ObjectId`，含 `is_deleted` 软删、`create_time`/`update_time` 审计、`row_version` 乐观锁 `@Version`）

- **`t_pubsub_topic`**：`topic_key`（业务 Key，例如房间号）
- **`t_pubsub_messages`**：`topic_id`、`message_id`（**按 topic 单调递增**，发布时在写锁内 `latestMessageId + 1` 分配）、`message_name`、`message_data`（`Map<String,Object>`）、`message_time`

订阅者 `clientId` 由 Snowflake（`DefaultSnowflakeSequence`，epoch `1288834974657L`，workerId `30`）生成，用于在锁 ID 里唯一标识每个订阅者。

## API

### 订阅端（`/api/pubsub`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pubsub/message/polling?topicKey&clientId&lastMessageId&timeout=45` | 长轮询。`DeferredResult` 异步响应，被发布者唤醒或超时后返回 `PollMessageDto` |
| GET | `/api/pubsub/message/sse/polling?topicKey&clientId&lastMessageId` | SSE 流。事件名：`PushState`（初始位点）/ `PushMessage`（消息，id=messageId）/ `Ping` / `Error`；收到 `messageName == "__done__"` 时关闭流 |

### 后端（`/backend/pubsub/message`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/getTopic?topicKey` | 查询 Topic |
| POST | `/createTopic` | 创建 Topic（走 `@Lock` 幂等） |
| POST | `/getOrCreateTopic` | 同上（不存在则创建） |
| POST | `/publish` | 发布消息（可选 `requireLockKey` 串行化、`releaseLockKey` 唤醒订阅） |
| GET | `/current?topicKey` | 查询当前位点 `PollStateDto(topicId, clientId, lastMessageId)` |
| GET | `/fetch?topicKey&clientId&lastMessageId&timeout=45` | 主动拉取 `lastMessageId` 之后的消息 |

`PublishMessageDto` 关键字段：

```java
private String topicKey;       // 目标 Topic
private String messageName;    // 消息名称（SSE 流中 "__done__" 为终止信号）
private Map<String, Object> messageData;
private Date messageTime;
private String requireLockKey; // 可选：发布前加锁，串行化多个发布者
private String releaseLockKey; // 可选：发布完成后 releaseHead 唤醒订阅者
```

## jaslock 使用详解

### 1. `@Lock` 声明式锁 —— Topic 幂等创建

`PubsubTopicService.createTopic`：

```java
@Lock("notification_pubsub_topic_{topicKey}")
public PubsubTopic createTopic(String topicKey) {
    PubsubTopic pubsubTopic = pubsubTopicRepository.getTopic(topicKey);
    if (pubsubTopic != null) return pubsubTopic;   // 已存在直接返回
    pubsubTopic = new PubsubTopic();
    pubsubTopic.setTopicKey(topicKey);
    return pubsubTopicRepository.save(pubsubTopic);
}
```

`{topicKey}` 是 SpEL 表达式，按业务 Key 分锁，避免不同 Topic 互相阻塞；锁内做"查-建"原子操作，保证并发创建下不产生重复 Topic。

### 2. `SlockTemplate.newLock(...).with()` —— 发布互斥 + 唤醒

`PubsubPublishTask`（RabbitMQ 异步消费）和 `PubsubMessageController.publishMessage`（同步 HTTP）共用同一模式：

```java
if (StringUtils.hasLength(messageDto.getRequireLockKey())) {
    try (AutoCloseable autoCloseable = slockTemplate.newLock(messageDto.getRequireLockKey(), 30, 300).with()) {
        pubsubMessageService.publish(messageDto);
        return;
    } finally {
        if (StringUtils.hasLength(messageDto.getReleaseLockKey())) {
            Lock lock = slockTemplate.newLock(messageDto.getReleaseLockKey(), 30, 300);
            try { lock.releaseHead(); } catch (Exception ignored) {}
        }
    }
}
// 无 requireLockKey 时直接发布，但 finally 中仍执行 releaseLockKey 唤醒
```

- `requireLockKey` —— 把同一业务 Key 的发布串行化（例如同一用户的消息按序到达）
- `releaseLockKey` —— `releaseHead()` 释放锁队列队首的等待者，作为订阅端的事件通知
- `Lock.with()` 返回 `AutoCloseable`，try-with-resources 自动释放，避免泄漏

### 3. `ReadWriteLock` —— 发布/订阅读写分离

`PubsubMessageService` 中 `publish` / `getCurrentState` / `fetchMessages` 三个方法都围绕同一 Key 加读写锁：

```java
String lockKey = "notification_pubsub_message_" + topicKey;
ReadWriteLock readWriteLock = slockTemplate.newReadWriteLock(lockKey, 15, 300);

// 发布：写锁，独占
readWriteLock.acquireWrite();
try { /* 分配 messageId = latestMessageId + 1，落库，写 Redis 缓存，唤醒订阅者 */ }
finally { readWriteLock.releaseWrite(); }

// 查询/拉取：读锁，共享
readWriteLock.acquireRead();
try { /* 读 Redis 缓存 → 回退 Mongo 查询 */ }
finally { readWriteLock.releaseRead(); }
```

写锁保证 `messageId` 分配的原子性（不会两个发布者拿到同一个 `latestMessageId`）；读锁允许大量订阅者并发拉取，只阻塞发布瞬间。

### 4. 锁队列事件通知 —— 长轮询 / SSE 的核心

这是本示例最有价值的部分：**用 slock 锁队列替代独立的推送服务**。

#### 4.1 锁 ID 编码

`encodeLockId(clientId, versionId)` 把订阅者 `clientId` 和 `lastMessageId`（版本号）拼成 16 字节 lockId：

```
lockId = [versionId : 8 bytes LE] + [clientId : 8 bytes LE]
```

slock 的锁匹配是"同一 lockKey + lockId"，因此每个 (订阅者, 期望的下一版本号) 组合都是独立锁；发布者用 `encodeLockId(0, 0)` 和 `encodeLockId(0, messageId)` 配合版本号比较 flag 实现按版本号唤醒。

#### 4.2 订阅端 —— 阻塞等待

`PubsubController.pollingMessages`：

```java
Lock waitLock = new Lock(slockTemplate.selectDatabase((byte) 0),
        ("notification_pubsub_message_polling_" + topicKey).getBytes(UTF_8),
        pubsubMessageService.encodeLockId(clientId, lastMessageId),
        timeout, 0, (short) 0xffff, (byte) 0);
waitLock.setTimeoutFlag((short) ICommand.TIMEOUT_FLAG_LESS_LOCK_VERSION_IS_LOCK_SUCCED);
waitLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
waitLock.acquire(r -> {   // 异步回调，不阻塞容器线程
    r.getResult();
    PollMessageDto dto = null;
    if (waitLock.getCurrentLockData() != null) {
        // 发布者把消息直接塞进了锁数据，反序列化即可
        dto = objectMapper.readValue(waitLock.getCurrentLockData().getDataAsBytes(), PollMessageDto.class);
        if (dto != null && dto.getLastMessageId() == lastMessageId + 1) dto.setClientId(clientId);
        else dto = null;
    }
    if (dto == null) dto = pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId, timeout);
    deferredResult.setResult(dto);
});
```

关键 flag：

- `TIMEOUT_FLAG_LESS_LOCK_VERSION_IS_LOCK_SUCCED` —— 当锁队列里存在**更小 version**的锁（即发布者用 `encodeLockId(0, 0)` 唤醒）时，本次 acquire 视为成功
- `EXPRIED_FLAG_UNLIMITED_AOF_TIME` —— 不限制 AOF 持久化时间
- `acquire(callback)` —— **异步**，容器线程立即返回 `DeferredResult`，slock 事件循环在锁被唤醒/超时时回调

#### 4.3 发布端 —— 唤醒并附带消息

`PubsubMessageService.publish` 在写锁内、落库之后：

```java
byte[] wakeupLockKey = ("notification_pubsub_message_polling_" + topicKey).getBytes(UTF_8);
LockSetData lockSetData = new LockSetData(objectMapper.writeValueAsBytes(
        new PollMessageDto(topicId, 0L,
            Collections.singletonList(MessageDto.fromEntity(pubsubMessage)),
            pubsubMessage.getMessageId())));   // 把消息体塞进锁数据

Lock wakeupLock = new Lock(slockTemplate.selectDatabase((byte) 0), wakeupLockKey,
        encodeLockId(0, 0), 0, 0, (short) 0, (byte) 0);
wakeupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
try { wakeupLock.releaseHead(lockSetData); }   // 唤醒队首订阅者并下发数据
catch (LockUnlockedException ignored) {}

// 封锁下一个版本号，避免后续订阅者错过
wakeupLock = new Lock(slockTemplate.selectDatabase((byte) 0), wakeupLockKey,
        encodeLockId(0, pubsubMessage.getMessageId()), 5, 300, (short) 0, (byte) 0);
wakeupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
wakeupLock.acquire();
```

`releaseHead(lockSetData)` 是核心：**释放锁队列头部第一个等待者，并把 `lockSetData` 作为锁的附加数据写回**，订阅者被唤醒后通过 `getCurrentLockData()` 读到完整消息体，**一次唤醒完成通知 + 数据下发**，无需再查库。

#### 4.4 超时兜底 —— 处理发布者/订阅者竞态

当订阅者 `waitLock` 超时（`LockTimeoutException`）但发布者其实已经 `releaseHead` 时，存在竞态：唤醒信号丢了。兜底用 `wakupLock` 再抢一次：

```java
Lock wakupLock = new Lock(..., encodeLockId(0, lastMessageId), 0, Math.max(timeout*2, 120), ...);
wakupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
try {
    wakupLock.acquire(ICommand.LOCK_FLAG_UPDATE_WHEN_LOCKED,
            waitLock.getCurrentLockData() != null ? new LockUnsetData() : null);
    wakupLock.release();
    deferredResult.setErrorResult(... GATEWAY_TIMEOUT ...);
} catch (LockTimeoutException e) {
    // 抢到说明发布者确实写过数据 → 从 wakupLock.getCurrentLockData() 取或回退 fetchMessages
}
```

`LOCK_FLAG_UPDATE_WHEN_LOCKED` + `LockUnsetData`：若锁已被占用则更新并清除数据，否则正常获取。这一步保证竞态下订阅者要么拿到发布者写入的数据，要么明确超时——不会"丢消息"。

#### 4.5 SSE 流式 —— 递归重投锁

`doSsePollingMessagesLoop` 在每条消息推送后**递归调用自己**重新 `acquire` 等待锁，实现持续推送：

```java
waitLock.acquire(r -> {
    // 取消息 → pushSseEvent("PushMessage", messageId, dto)
    if (messageName == "__done__") { isCompletion.set(true); emitter.complete(); return; }
    if (!isCompletion.get()) doSsePollingMessagesLoop(...);  // 重新等下一条
});
```

空闲时发送 `Ping` 事件保持连接。任何异常都通过 `Error` 事件回写并决定是否重投。

### jaslock API 速查

| API | 用处 |
|---|---|
| `@Lock("..._{var}")` | SpEL Key 声明式锁 |
| `SlockTemplate.newLock(key, timeout, expried)` | 编程式锁 |
| `Lock.with()` | `AutoCloseable` 包装，try-with-resources 自动释放 |
| `SlockTemplate.newReadWriteLock(...)` | 读写锁 |
| `Lock.acquire()` / `Lock.acquire(callback)` | 同步 / 异步获取 |
| `Lock.acquire(flag, data)` | 带 flag（`LOCK_FLAG_UPDATE_WHEN_LOCKED`）与数据（`LockUnsetData`）获取 |
| `Lock.releaseHead()` / `releaseHead(LockSetData)` | 释放队首等待者，可附带数据 |
| `Lock.getCurrentLockData()` | 读取锁附加数据（发布者写入的消息体） |
| `setTimeoutFlag(TIMEOUT_FLAG_LESS_LOCK_VERSION_IS_LOCK_SUCCED)` | 版本号比较 flag |
| `setExpriedFlag(EXPRIED_FLAG_UNLIMITED_AOF_TIME)` | 不限 AOF 时间 |
| `SlockTemplate.selectDatabase(byte)` | 选择 slock database |
| `LockSetData` / `LockUnsetData` | 锁数据载体（写入 / 清除） |

## 配置

`src/main/resources/application.properties` 仅一行 `spring.application.name=pubsub`，其余全部走默认值。如需指向自己的中间件，按主项目 [README](../../README.md#configuration) 添加：

```properties
# MongoDB（默认 localhost:27017）
# spring.data.mongodb.uri=mongodb://localhost:27017/pubsub

# Redis（Spring Data Redis，Lettuce 连接池）
spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
# spring.data.redis.password=
# spring.data.redis.database=0
# spring.data.redis.lettuce.pool.max-active=8
# spring.data.redis.lettuce.pool.max-idle=8
# spring.data.redis.lettuce.pool.min-idle=0

# RabbitMQ（默认 localhost:5672, guest/guest）
# spring.rabbitmq.host=127.0.0.1
# spring.rabbitmq.port=5672
# spring.rabbitmq.username=guest
# spring.rabbitmq.password=guest

# jaslock
spring.slock.enabled=true
spring.slock.host=127.0.0.1
spring.slock.port=5658
```

## 运行

**前置依赖**：需要先启动 slock 服务、MongoDB、Redis、RabbitMQ。

- slock 服务端：参见 https://github.com/snower/slock
- MongoDB / Redis / RabbitMQ 可用本地实例或 docker：

```bash
docker run -d -p 27017:27017 --name mongo mongo:7
docker run -d -p 6379:6379 --name redis redis:7
docker run -d -p 5672:5672 --name rabbit rabbitmq:3-management
```

**启动示例**：

```bash
cd examples/pubsub
./mvnw spring-boot:run
# 或 Windows
mvnw.cmd spring-boot:run
```

启动后：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- SSE 测试（浏览器 / curl）：
  ```
  GET http://localhost:8080/api/pubsub/message/sse/polling?topicKey=room1
  ```
- 发布消息：
  ```bash
  curl -X POST http://localhost:8080/backend/pubsub/message/publish \
       -H 'Content-Type: application/json' \
       -d '{"topicKey":"room1","messageName":"hello","messageData":{"text":"hi"}}'
  ```
- 异步发布（经 RabbitMQ）：把消息发往 `exchange=pubsub`、`routing-key=pubsub` 即可，`PubsubPublishTask` 会消费并执行同样的 publish + releaseHead 流程。

## 关键要点

1. **锁即事件通道**：slock 的锁队列天然是一个 FIFO 等待队列，`releaseHead(data)` 同时完成"唤醒 + 数据下发"，省掉独立推送服务。
2. **lockId 编码版本号 + clientId**：用 `encodeLockId(clientId, versionId)` 让每个订阅者的等待锁唯一，发布者用 `versionId=0` 配合 `TIMEOUT_FLAG_LESS_LOCK_VERSION_IS_LOCK_SUCCED` 实现"任意更小版本即唤醒"。
3. **读写锁分离发布/订阅**：写锁独占分配 `messageId`，读锁共享拉取，热点 Topic 下高并发订阅不互斥。
4. **Redis 只做位点缓存**：`PublishDto(topicId, latestMessageId)` 缓存 24h，让发布热路径跳过 Mongo 查询；缓存失败/miss 自动回退 Mongo 并刷新。
5. **超时兜底防竞态**：`wakupLock` + `LOCK_FLAG_UPDATE_WHEN_LOCKED` + `LockUnsetData` 处理订阅者 waitLock 超时但发布者已唤醒的竞态，保证不丢消息。
6. **同步 / 异步发布同构**：`PubsubMessageController`（HTTP 同步）和 `PubsubPublishTask`（RabbitMQ 异步）走完全一致的 `requireLockKey → publish → releaseLockKey` 模式，便于按吞吐需求切换。
7. **SSE 终止约定**：消息 `messageName == "__done__"` 是 SSE 流的终止信号，便于一次性推送场景优雅关闭。

## License

随主项目 [MIT](../../LICENSE)。
