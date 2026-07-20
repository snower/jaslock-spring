# jaslock-spring polling-request 示例

基于 [jaslock](https://github.com/snower/jaslock-spring) 实现的"反向轮询 RPC"示例工程。解决一个常见的网络打通场景：**云上服务需要调用部署在医院内网/受防火墙保护环境中的系统，但内网系统无法被云上直接访问**。本示例让内网系统主动长轮询云上接口拉取任务、处理后再回写结果，云上调用方则同步等待结果——**用 jaslock 的 `Event` 作为跨进程唤醒信号，把一次"请求—响应"拆成两条单向 HTTP 调用，无需 WebSocket / 主动推送 / VPN**。

## 概述

本示例展示 jaslock 在 polling-request 场景下的三种典型用法：

| 用法 | 位置 | 作用 |
|---|---|---|
| `@Lock` 声明式锁 | `PollingServiceImpl.loadTasks` / `handleTaskResult` | SpEL Key 加锁，前者串行化同 App 的任务领取、后者保证结果提交幂等 |
| `SlockTemplate.newEvent(...)` 编程式事件 | `PollingServiceImpl.requestTask` / `pushTask` / `handleTaskResult` | `set()` / `wait()` / `clear()` 作为跨进程唤醒信号 |
| `Event.waitAndTimeoutRetryClear` 异步长轮询 | `PollingController.polling` | 配合 Spring `DeferredResult` 异步响应，被云上 `set()` 唤醒后拉取任务 |

核心是**两条 Event 通道**配合 MongoDB 持久化：

- **App 级事件** `polling:app:<appId>` —— 云上"有新任务了"的通知：`requestTask` / `pushTask` 落库后 `event.set()`，医院端长轮询 `event.waitAndTimeoutRetryClear(...)` 被唤醒，随后 `event.clear()` 复位并 `loadTasks` 拉取任务。
- **Task 级事件** `polling:task:<taskId>` —— 医院"结果回来了"的通知：云上 `requestTask` 落库后 `taskEvent.clear()` 再 `taskEvent.wait(timeout)` 阻塞等待；医院端 `handleTaskResult` 落库结果后 `event.set()` 唤醒等待者。

与 [pubsub 示例](../pubsub/README.md) 的区别：pubsub 用 `Lock.releaseHead(data)` 把消息体直接塞进锁数据下发（一对多广播）；本示例用 `Event` 只传信号，**任务和结果数据全部走 MongoDB**，因为任务有独立的生命周期（领取、处理、超时、审计）且单次轮询可批量返回多个任务。`Event` 语义更轻、更适合"通知 + 自取数据"的请求/响应模式。

## 技术栈

- **Spring Boot 4.1.0 / Java 21** —— Web 框架
- **MongoDB** —— 持久化 `app`、`polling_task`、`polling_task_result` 三张集合
- **jaslock-spring-boot-starter 0.1.4** —— 分布式锁 + 事件通知（`Event` / `@Lock`）
- **springdoc-openapi 2.6.0** —— Swagger UI
- **Lombok** —— 样板代码消除

> 本示例**不依赖 Redis / RabbitMQ**，中间件只需 slock 服务 + MongoDB，部署比 pubsub 更轻。

## 架构

```
   云上调用方                      本服务 (polling-request)                       医院内部系统 (轮询端)
   ─────────                      ──────────────────────                       ──────────────────

POST /backend/app/v1/request
─────────────────────────►  CallController.handleCloudRequest
                            │ PollingServiceImpl.requestTask
                            │   ├─ PollingTaskEntity 落库 (Mongo)
                            │   ├─ Event(polling:app:<appId>).set() ──► 唤醒轮询
                            │   └─ Event(polling:task:<id>).clear()
                            │      └─ taskEvent.wait(timeout) ◄─ 阻塞等待结果
                            │
                            │                              GET /api/app/v1/polling (长轮询, DeferredResult)
                            │  Event(polling:app:<appId>)  ◄────────────────────────  PollingController.polling
                            │   .waitAndTimeoutRetryClear(timeout, callback)
                            │        │ 被唤醒 → event.clear()
                            │        └─ loadTasks(@Lock per app) 从 Mongo 取未领取任务
                            │        └─ 返回 List<PollingTaskDTO> ─────────────────►  处理任务
                            │
                            │                              POST /api/app/v1/result?appId
                            │  PollingServiceImpl.handleTaskResult ◄────────────────  提交结果
                            │   │ @Lock(docking_cloud_polling_submit_task_result_{taskId}) 幂等
                            │   ├─ 校验任务存在 + 结果未提交
                            │   ├─ PollingTaskResultEntity 落库 (含 costTime)
                            │   └─ Event(polling:task:<id>).set() ──► 唤醒等待的 requestTask
                            │
                            ◄─ taskEvent.wait() 返回
                            │  读 PollingTaskResultEntity → CallResponseDTO
◄── CallResponseDTO ───────


POST /backend/app/v1/push    (fire-and-forget，不等结果)
─────────────────────────►  CallController.handleCloudPush
                            │ PollingServiceImpl.pushTask
                            │   ├─ PollingTaskEntity 落库 (timeout 默认 1800s)
                            │   └─ Event(polling:app:<appId>).set() ──► 唤醒轮询
                            ◄── return true
```

## 项目结构

```
examples/polling-request/
├── pom.xml
├── mvnw / mvnw.cmd
├── src/main/
│   ├── resources/application.properties
│   └── java/io/github/snower/jaslock/spring/example/pollingrequest/
│       ├── PollingRequestApplication.java          # Spring Boot 入口
│       ├── config/
│       │   └── ObjectMapperConfiguration.java      # Jackson ObjectMapper Bean
│       ├── controller/
│       │   ├── api/PollingController.java          # 医院端：长轮询 + 提交结果
│       │   └── backend/CallController.java         # 云上端：同步请求 / 推送 / ping
│       ├── service/
│       │   ├── PollingService.java                 # 接口
│       │   └── impl/PollingServiceImpl.java        # 核心：requestTask / pushTask / loadTasks / handleTaskResult
│       ├── entity/
│       │   ├── AppEntity.java                      # app 集合
│       │   ├── PollingTaskEntity.java              # polling_task 集合
│       │   └── PollingTaskResultEntity.java        # polling_task_result 集合
│       ├── repository/
│       │   ├── AppRepository.java                  # MongoRepository
│       │   ├── PollingTaskRepository.java          # MongoRepository
│       │   └── PollingTaskResultRepository.java    # MongoRepository
│       └── dto/
│           ├── CallRequestDTO.java                 # 云上请求 (appId/bizType/method/payload/timeout)
│           ├── CallResponseDTO.java                # 云上响应 (data/code/msg/costTime)
│           ├── PollingTaskDTO.java                 # 下发给医院的任务
│           └── TaskResultDTO.java                  # 医院回写的结果
└── src/test/java/.../PollingRequestApplicationTests.java
```

## 数据模型

三张 MongoDB 集合，`_id` 均为 `ObjectId`：

- **`app`** —— `AppEntity`：`name`、`app_key`、`app_secret`、`is_deleted`（软删）。代表一个已注册的医院/接入方，`appId` 是其余两张表的外键。
- **`polling_task`** —— `PollingTaskEntity`：`appId`（索引）、`bizType`、`method`、`payload`（JSON 字符串）、`clientId`、`createTime`、`timeout`、`expireTime`。
  - `clientId` 语义：`0L` = 未领取；`-1L` = 已取消（云上等待超时或异常时回写）；正数 = 已被该轮询端领取。
  - `expireTime = createTime + timeout*1000`，领取与查询都以 `expireTime > now` 为过滤条件，过期任务自动作废。
- **`polling_task_result`** —— `PollingTaskResultEntity`：`appId`（索引）、`bizType`、`method`、`data`、`code`、`msg`、`costTime`、`createTime`。`_id` 与 `polling_task._id` 一一对应（同一 `taskId`）。

## API

### 云上端（`/backend/app`） —— `CallController`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/ping?appId` | ping 测试。内部构造 `bizType=STATUS, method=ping, payload=PING` 的请求，走 `requestTask` 同步等待结果并返回 `data` |
| POST | `/v1/request` | 提交一次同步请求，阻塞等待医院处理结果后返回 `CallResponseDTO` |
| POST | `/v1/push` | 提交一次推送任务（fire-and-forget），不等待结果，立即返回 `true` |

### 医院端（`/api/app`） —— `PollingController`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/polling?appId&clientId&lastTaskId&timeout=45` | 长轮询拉取待处理任务。`DeferredResult` 异步响应，被云上 `Event.set` 唤醒后调用 `loadTasks` 返回 `List<PollingTaskDTO>`；超时返回 `504 GATEWAY_TIMEOUT` |
| POST | `/v1/result?appId` | 提交任务处理结果 `TaskResultDTO`，成功返回 `true`，任务不存在/已超时返回 `500` |

`CallRequestDTO` 关键字段：

```java
private ObjectId appId;      // 目标应用
private String bizType;      // 业务类型
private String method;       // 业务方法
private String payload;      // 请求参数（JSON 字符串）
private Integer timeout = 45;// 超时秒数，默认 45（编程式 pushTask 重载默认 1800）
```

`CallResponseDTO`：

```java
private String data;     // 响应数据
private Integer code;    // 0=成功, 4401=等待超时, 4402=结果未知, 5401=系统错误
private String msg;
private Long costTime;   // 医院处理耗时（毫秒）
```

## jaslock 使用详解

### 1. `@Lock` 声明式锁 —— 任务领取串行化 & 结果提交幂等

`loadTasks` 按 `appId` 加锁，保证同一 App 同一时刻只有一个轮询端在领取任务，避免重复下发：

```java
@Lock("docking_cloud_polling_load_tasks_{appId}")
@Override
public List<PollingTaskDTO> loadTasks(ObjectId appId, Long clientId, String lastTaskId) {
    // 查询 expireTime > now 的待领取任务，clientId==0L 的改为本 clientId（领取）
    // lastTaskId 非空：_id > lastTaskId 且 clientId in (0L, clientId) —— 增量续拉
    // lastTaskId 为空：_id > (now - 2h) 且 clientId == 0L —— 首次拉取近 2 小时未领取
}
```

`handleTaskResult` 按 `taskId` 加锁，配合"结果已存在"校验，保证同一任务结果不会被重复提交：

```java
@Lock("docking_cloud_polling_submit_task_result_{result.taskId}")
@Override
public boolean handleTaskResult(ObjectId appId, TaskResultDTO result) {
    // 校验任务存在 + 结果未提交 → 落库 PollingTaskResultEntity → Event.set() 唤醒等待者
}
```

`{appId}` / `{result.taskId}` 是 SpEL 表达式，按业务维度分锁，不同 App / 不同任务互不阻塞。

### 2. `Event` —— 跨进程唤醒信号（核心）

jaslock 的 `Event` 提供 `set()` / `wait()` / `clear()` 三个原语，语义类似自动复位事件：`set()` 发信号，`wait()` 阻塞至收到信号，`clear()` 复位以便下次等待。本示例用它打通云上↔医院的双向通知。

#### 2.1 App 级事件 —— 通知医院"有新任务"

`PollingServiceImpl.requestTask` / `pushTask` 落库后发信号：

```java
String hospitalKey = SLOCK_APP_PREFIX + appId.toHexString();   // polling:app:<appId>
Event event = slockTemplate.newEvent(hospitalKey, 5, 60, true);
event.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
// ... PollingTaskEntity 落库 ...
event.set();   // 唤醒正在 wait 的医院轮询端
```

医院端 `PollingController.polling` 异步等待：

```java
String hospitalKey = SLOCK_APP_PREFIX + appId;
Event event = slockTemplate.newEvent(hospitalKey, 5, timeout * 2, true);
event.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
DeferredResult<List<PollingTaskDTO>> deferredResult = new DeferredResult<>((timeout + 15) * 1000L);
// ... onTimeout / onCompletion / onError 设置 ...
event.waitAndTimeoutRetryClear(timeout, e -> {
    if (!isCompletion.compareAndSet(false, true)) return;
    try {
        e.getResult();                          // 被唤醒成功
        try { event.clear(); } catch (Exception ignored) {}  // 复位，下次轮询重新阻塞
        List<PollingTaskDTO> tasks = pollingService.loadTasks(new ObjectId(appId), clientId, lastTaskId);
        deferredResult.setResult(tasks);
    } catch (EventWaitTimeoutException ex) {
        deferredResult.setErrorResult(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "等待超时"));
    } catch (Throwable ex) {
        deferredResult.setErrorResult(ex);
    }
});
return deferredResult;
```

关键点：

- `waitAndTimeoutRetryClear(timeout, callback)` —— **异步**回调，容器线程立即返回 `DeferredResult`，slock 事件循环在事件被 `set` 或超时时回调；方法名暗示其内部在超时时自动重试并在唤醒后清除事件，适配长轮询场景。
- `AtomicBoolean isCompletion` + `DeferredResult` 的 `onTimeout` / `onCompletion` / `onError` —— 防止回调与 Spring 超时机制竞态下重复设置结果。
- `event.clear()` 在唤醒后复位，确保下一次 `/v1/polling` 调用能再次阻塞等待新任务。

#### 2.2 Task 级事件 —— 通知云上"结果回来了"

`requestTask` 先复位再阻塞等待：

```java
String taskKey = SLOCK_KEY_PREFIX + requestId.toHexString();   // polling:task:<taskId>
Event taskEvent = slockTemplate.newEvent(taskKey, 5, request.getTimeout() * 2, true);
taskEvent.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
taskEvent.clear();   // 先复位，确保后续 wait 会真正阻塞

// ... PollingTaskEntity 落库、app Event.set() 唤醒医院 ...
try {
    taskEvent.wait(request.getTimeout());   // 阻塞等待医院回写结果
} catch (EventWaitTimeoutException e) {
    taskEntity.setClientId(-1L);            // 标记任务取消
    pollingTaskRepository.save(taskEntity);
    return CallResponseDTO.builder().code(4401).msg("等待执行结果超时").build();
}

// 被唤醒 → 从 Mongo 读取结果
PollingTaskResultEntity result = pollingTaskResultRepository.findById(requestId).orElse(null);
```

`handleTaskResult` 落库结果后发信号唤醒：

```java
String taskKey = SLOCK_KEY_PREFIX + result.getTaskId();   // polling:task:<taskId>
Event event = slockTemplate.newEvent(taskKey, 5, 60, true);
event.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
event.set();   // 唤醒 requestTask 中等待的 taskEvent.wait(...)
```

- `taskEvent.clear()` 是关键：`newEvent(..., defaultSeted=true)` 初始为已 set 状态，若不先 `clear()`，`wait()` 会立即返回。先复位让等待真正生效。
- 异常路径（`requestTask` 捕获 `Throwable`）会把 `clientId` 置 `-1L` 并返回 `code=5401`，避免医院端后续还来领取一个已放弃的任务。
- `code=4402`（结果未知）：`taskEvent.wait` 正常返回但 Mongo 中查不到结果实体——极端竞态下的兜底返回。

#### 2.3 推送（push）—— 只发信号不等结果

`pushTask` 与 `requestTask` 的前半段完全一致（落库 + `app Event.set()`），但**不创建 task 事件、不 `wait`**，直接返回。医院端领取、处理、回写结果的流程不变，只是云上不等待。HTTP `/v1/push` 用 `CallRequestDTO` 里的 `timeout`（DTO 默认 45 秒）；编程式重载 `pushTask(appId, bizType, method, payload)` 内部固定 `timeout=1800` 秒（30 分钟），适合对时延不敏感的异步通知。

### jaslock API 速查

| API | 用处 |
|---|---|
| `@Lock("..._{var}")` | SpEL Key 声明式锁，`timeout=5` / `expried=120` 默认值 |
| `SlockTemplate.newEvent(key, timeout, expried, defaultSeted)` | 编程式事件 |
| `Event.set()` | 发信号，唤醒所有 `wait` 的等待者 |
| `Event.wait(timeout)` | 阻塞等待 `set`，超时抛 `EventWaitTimeoutException` |
| `Event.waitAndTimeoutRetryClear(timeout, callback)` | 异步回调式等待，超时自动重试并在唤醒后清除，适配长轮询 |
| `Event.clear()` | 复位事件，使下次 `wait` 重新阻塞 |
| `Event.setExpriedFlag(EXPRIED_FLAG_UNLIMITED_AOF_TIME)` | 不限 AOF 持久化时间，保证事件信号在跨进程间可靠留存 |

## 配置

`src/main/resources/application.properties` 仅两行：

```properties
spring.application.name=polling-request
spring.profiles.active=local
```

MongoDB 与 slock 均走默认值（`localhost:27017` / `127.0.0.1:5658`）。如需指向自己的中间件，按主项目 [README](../../README.md#configuration) 添加：

```properties
# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/polling_request

# jaslock
spring.slock.enabled=true
spring.slock.host=127.0.0.1
spring.slock.port=5658
```

## 运行

**前置依赖**：slock 服务 + MongoDB（无需 Redis / RabbitMQ）。

- slock 服务端：参见 https://github.com/snower/slock
- MongoDB 可用本地实例或 docker：

```bash
docker run -d -p 27017:27017 --name mongo mongo:7
```

**启动示例**：

```bash
cd examples/polling-request
./mvnw spring-boot:run
# 或 Windows
mvnw.cmd spring-boot:run
```

启动后 Swagger UI：`http://localhost:8080/swagger-ui.html`

**端到端演示**（需先在 `app` 集合插入一条记录拿到 `appId`，例如 `_id= ObjectId(...)`）：

1. 医院端开启长轮询（阻塞等待）：
   ```bash
   curl "http://localhost:8080/api/app/v1/polling?appId=<APPID>&clientId=1&timeout=45"
   ```
2. 云上发起同步请求（立即返回医院处理结果）：
   ```bash
   curl -X POST http://localhost:8080/backend/app/v1/request \
        -H 'Content-Type: application/json' \
        -d '{"appId":"<APPID>","bizType":"STATUS","method":"ping","payload":"PING","timeout":45}'
   ```
   - 步骤 1 的长轮询会先收到 `PollingTaskDTO` 列表并返回。
3. 医院端提交结果（步骤 1 拿到任务后处理并回写，唤醒步骤 2 的等待）：
   ```bash
   curl -X POST "http://localhost:8080/api/app/v1/result?appId=<APPID>" \
        -H 'Content-Type: application/json' \
        -d '{"taskId":"<TASKID>","data":"PONG","code":0,"msg":""}'
   ```
   - 步骤 2 的 `requestTask` 被唤醒，返回 `CallResponseDTO{data:"PONG", code:0}`。
4. 推送（不等结果）：
   ```bash
   curl -X POST http://localhost:8080/backend/app/v1/push \
        -H 'Content-Type: application/json' \
        -d '{"appId":"<APPID>","bizType":"NOTIFY","method":"sync","payload":"{}"}'
   ```
   - 立即返回 `true`，任务由医院端下一次轮询领取。

## 关键要点

1. **Event 即跨进程唤醒信号**：云上→医院用 `polling:app:<appId>` 通知"有任务"，医院→云上用 `polling:task:<taskId>` 通知"有结果"，两条单向信号配合 MongoDB 持久化，把不可达的内网系统变成"主动拉取"的 RPC 对端。
2. **Event 只传信号、数据走 Mongo**：与 pubsub 用 `Lock.releaseHead(data)` 直接下发数据不同，本示例任务有领取/处理/超时/审计的完整生命周期，且单次轮询可批量返回多任务，因此数据落库、`Event` 只负责唤醒。
3. **`clear()` 后再 `wait()`**：`newEvent(defaultSeted=true)` 初始为已 set 状态，`requestTask` 先 `taskEvent.clear()` 再 `wait()`，确保等待真正生效；轮询端在被唤醒后 `event.clear()` 复位，保证下次轮询能重新阻塞。
4. **`@Lock` 分维度串行化**：`loadTasks` 按 `appId` 加锁防止重复下发任务，`handleTaskResult` 按 `taskId` 加锁保证结果幂等提交——不同 App / 不同任务互不阻塞。
5. **clientId 三态语义**：`0L` 未领取 / `-1L` 已取消（云上超时或异常回写）/ 正数已领取；`loadTasks` 用 `clientId in (0L, clientId)` 既领新任务又续拉自己之前未处理完的，`-1L` 自然被排除。
6. **`expireTime` 驱动作废**：任务按 `createTime + timeout` 计算过期时间，查询与领取都以 `expireTime > now` 过滤，过期任务无需额外清理即自动失效。
7. **`DeferredResult` + `AtomicBoolean` 防竞态**：长轮询用 Spring 异步响应释放容器线程，`isCompletion` 原子位 + `onTimeout` / `onCompletion` / `onError` 防止 slock 回调与 Spring 超时机制重复设置结果。
8. **同步请求与推送同构**：`requestTask` 与 `pushTask` 前半段（落库 + `app Event.set()`）完全一致，前者多一步 `taskEvent.wait()`，按业务时延要求选择同步等待或异步通知。

## License

随主项目 [MIT](../../LICENSE)。
