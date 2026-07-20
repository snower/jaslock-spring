package io.github.snower.jaslock.spring.example.pollingrequest.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.snower.jaslock.Event;
import io.github.snower.jaslock.commands.ICommand;
import io.github.snower.jaslock.exceptions.EventWaitTimeoutException;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.boot.annotations.Lock;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.CallRequestDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.CallResponseDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.PollingTaskDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.TaskResultDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.entity.PollingTaskEntity;
import io.github.snower.jaslock.spring.example.pollingrequest.entity.PollingTaskResultEntity;
import io.github.snower.jaslock.spring.example.pollingrequest.repository.PollingTaskRepository;
import io.github.snower.jaslock.spring.example.pollingrequest.repository.PollingTaskResultRepository;
import io.github.snower.jaslock.spring.example.pollingrequest.service.PollingService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PollingServiceImpl implements PollingService {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SlockTemplate slockTemplate;

    @Autowired
    private PollingTaskRepository pollingTaskRepository;

    @Autowired
    private PollingTaskResultRepository pollingTaskResultRepository;

    private static final String SLOCK_KEY_PREFIX = "polling:task:";
    private static final String SLOCK_APP_PREFIX = "polling:app:";

    @Override
    public <T> T requestTask(ObjectId appId, String bizType, String method, Object payload, TypeReference<T> valueTypeRef) {
        try {
            CallRequestDTO callRequestDTO = new CallRequestDTO();
            callRequestDTO.setAppId(appId);
            callRequestDTO.setBizType(bizType);
            callRequestDTO.setMethod(method);
            if (payload instanceof String) {
                callRequestDTO.setPayload((String) payload);
            } else {
                callRequestDTO.setPayload(objectMapper.writeValueAsString(payload));
            }
            callRequestDTO.setTimeout(45);
            CallResponseDTO response = requestTask(callRequestDTO);
            if (response.getCode() != 0) {
                throw new HttpServerErrorException(HttpStatusCode.valueOf(response.getCode()), response.getMsg());
            }
            if (valueTypeRef.getType() == String.class) {
                return (T) response.getData();
            }
            return objectMapper.readValue(response.getData(), valueTypeRef);
        } catch (JsonProcessingException e) {
            throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, e.toString());
        }
    }

    @Override
    public <T> T requestTask(ObjectId appId, String bizType, String method, Object payload, Class<T> resultType) {
        try {
            CallRequestDTO callRequestDTO = new CallRequestDTO();
            callRequestDTO.setAppId(appId);
            callRequestDTO.setBizType(bizType);
            callRequestDTO.setMethod(method);
            if (payload instanceof String) {
                callRequestDTO.setPayload((String) payload);
            } else {
                callRequestDTO.setPayload(objectMapper.writeValueAsString(payload));
            }
            callRequestDTO.setTimeout(45);
            CallResponseDTO response = requestTask(callRequestDTO);
            if (response.getCode() != 0) {
                throw new HttpServerErrorException(HttpStatusCode.valueOf(response.getCode()), response.getMsg());
            }
            if (resultType == String.class) {
                return resultType.cast(response.getData());
            }
            // 判断返回值是否为空
            if (response.getData() == null || response.getData().trim().isEmpty()) {
                return null;
            }
            return objectMapper.readValue(response.getData(), resultType);
        } catch (JsonProcessingException e) {
            throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, e.toString());
        }
    }

    @Override
    public void pushTask(ObjectId appId, String bizType, String method, Object payload) {
        try {
            CallRequestDTO callRequestDTO = new CallRequestDTO();
            callRequestDTO.setAppId(appId);
            callRequestDTO.setBizType(bizType);
            callRequestDTO.setMethod(method);
            if (payload instanceof String) {
                callRequestDTO.setPayload((String) payload);
            } else {
                callRequestDTO.setPayload(objectMapper.writeValueAsString(payload));
            }
            callRequestDTO.setTimeout(1800);
            pushTask(callRequestDTO);
        } catch (JsonProcessingException e) {
            throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, e.toString());
        }
    }

    @Override
    public CallResponseDTO requestTask(CallRequestDTO request) {
        ObjectId appId = request.getAppId();
        ObjectId requestId = new ObjectId();
        try {
            String hospitalKey = SLOCK_APP_PREFIX + appId.toHexString();
            Event event = slockTemplate.newEvent(hospitalKey, 5, 60, true);
            event.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
            String taskKey = SLOCK_KEY_PREFIX + requestId.toHexString();
            Event taskEvent = slockTemplate.newEvent(taskKey, 5, request.getTimeout() * 2, true);
            taskEvent.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
            taskEvent.clear();

            PollingTaskEntity taskEntity = PollingTaskEntity.builder()
                    .id(requestId)
                    .appId(appId)
                    .bizType(request.getBizType())
                    .method(request.getMethod())
                    .payload(request.getPayload())
                    .clientId(0L)
                    .createTime(new Date())
                    .timeout(request.getTimeout())
                    .expireTime(new Date(System.currentTimeMillis() + request.getTimeout() * 1000))
                    .build();
            pollingTaskRepository.save(taskEntity);
            event.set();
            try {
                taskEvent.wait(request.getTimeout());
            } catch (EventWaitTimeoutException e) {
                taskEntity.setClientId(-1L);
                pollingTaskRepository.save(taskEntity);
                return CallResponseDTO.builder()
                        .code(4401)
                        .msg("等待执行结果超时")
                        .build();
            }

            PollingTaskResultEntity pollingTaskResultEntity = pollingTaskResultRepository.findById(requestId).orElse(null);
            if (pollingTaskResultEntity == null) {
                return CallResponseDTO.builder()
                        .code(4402)
                        .msg("结果未知")
                        .build();
            }
            return CallResponseDTO.builder()
                    .data(pollingTaskResultEntity.getData())
                    .code(pollingTaskResultEntity.getCode() == null ? 0 : pollingTaskResultEntity.getCode())
                    .msg(pollingTaskResultEntity.getMsg() == null ? "" : pollingTaskResultEntity.getMsg())
                    .costTime(pollingTaskResultEntity.getCostTime())
                    .build();
        } catch (Throwable e) {
            PollingTaskEntity taskEntity = pollingTaskRepository.findById(requestId).orElse(null);
            if (taskEntity != null) {
                taskEntity.setClientId(-1L);
                pollingTaskRepository.save(taskEntity);
            }
            log.error("提交任务失败: orgId={} biztype={}", appId, request.getBizType(), e);
            return CallResponseDTO.builder()
                    .code(5401)
                    .msg("系统错误: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public void pushTask(CallRequestDTO request) {
        ObjectId appId = request.getAppId();
        ObjectId requestId = new ObjectId();
        try {
            String hospitalKey = SLOCK_APP_PREFIX + appId.toHexString();
            Event event = slockTemplate.newEvent(hospitalKey, 5, 60, true);
            event.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);

            PollingTaskEntity taskEntity = PollingTaskEntity.builder()
                    .id(requestId)
                    .appId(appId)
                    .bizType(request.getBizType())
                    .method(request.getMethod())
                    .payload(request.getPayload())
                    .clientId(0L)
                    .createTime(new Date())
                    .timeout(request.getTimeout())
                    .expireTime(new Date(System.currentTimeMillis() + request.getTimeout() * 1000))
                    .build();
            pollingTaskRepository.save(taskEntity);
            event.set();
        } catch (Throwable e) {
            PollingTaskEntity taskEntity = pollingTaskRepository.findById(requestId).orElse(null);
            if (taskEntity != null) {
                taskEntity.setClientId(-1L);
                pollingTaskRepository.save(taskEntity);
            }
            log.error("推送任务失败: orgId={} biztype={}", appId, request.getBizType(), e);
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "系统错误: " + e.getMessage());
        }
    }

    @Lock("docking_cloud_polling_load_tasks_{appId}")
    @Override
    public List<PollingTaskDTO> loadTasks(ObjectId appId, Long clientId, String lastTaskId) {
        Date now = new Date();
        Criteria criteria = Criteria.where("appId").is(appId).and("expireTime").gt(now);
        if (StringUtils.hasText(lastTaskId)) {
            criteria = criteria.and("_id").gt(new ObjectId(lastTaskId))
                    .and("clientId").in(0L, clientId);
        } else {
            criteria = criteria.and("_id").gt(new ObjectId(new Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)))
                    .and("clientId").is(0L);
        }
        List<PollingTaskEntity> pendingTasks = mongoTemplate.find(Query.query(criteria), PollingTaskEntity.class);
        return pendingTasks.stream()
                .peek(task -> {
                    if (task.getClientId() == 0L) {
                        task.setClientId(clientId);
                        pollingTaskRepository.save(task);
                    }
                })
                .filter(task -> now.getTime() < task.getExpireTime().getTime())
                .map(task -> PollingTaskDTO.builder()
                        .taskId(task.getId().toHexString())
                        .bizType(task.getBizType())
                        .method(task.getMethod())
                        .payload(task.getPayload())
                        .createTime(task.getCreateTime())
                        .expireTime(task.getExpireTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Lock("docking_cloud_polling_submit_task_result_{result.taskId}")
    @Override
    public boolean handleTaskResult(ObjectId appId, TaskResultDTO result) {
        ObjectId taskId = new ObjectId(result.getTaskId());
        try {
            PollingTaskEntity pollingTaskEntity = pollingTaskRepository.findById(taskId).orElse(null);
            if (pollingTaskEntity == null || !Objects.equals(pollingTaskEntity.getAppId(), appId)) {
                log.warn("任务不存在: {}", result);
                throw new HttpServerErrorException(HttpStatus.NOT_FOUND, "任务不存在");
            }
            PollingTaskResultEntity pollingTaskResultEntity = pollingTaskResultRepository.findById(taskId).orElse(null);
            if (pollingTaskResultEntity != null) {
                log.warn("任务结果已存在: {}", result);
                throw new HttpServerErrorException(HttpStatus.NOT_FOUND, "任务结果已存在");
            }
            long costTime = System.currentTimeMillis() - pollingTaskEntity.getCreateTime().getTime();
            pollingTaskResultEntity = new PollingTaskResultEntity();
            pollingTaskResultEntity.setId(taskId);
            pollingTaskResultEntity.setAppId(pollingTaskEntity.getAppId());
            pollingTaskResultEntity.setBizType(pollingTaskEntity.getBizType());
            pollingTaskResultEntity.setMethod(pollingTaskEntity.getMethod());
            pollingTaskResultEntity.setData(result.getData());
            pollingTaskResultEntity.setCode(result.getCode());
            pollingTaskResultEntity.setMsg(result.getMsg());
            pollingTaskResultEntity.setCostTime(costTime);
            pollingTaskResultEntity.setCreateTime(new Date());
            pollingTaskResultRepository.save(pollingTaskResultEntity);

            String taskKey = SLOCK_KEY_PREFIX + result.getTaskId();
            Event event = slockTemplate.newEvent(taskKey, 5, 60, true);
            event.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
            event.set();

            log.info("任务完成: taskId={}, costTime={}ms", result.getTaskId(), costTime);
            return true;
        } catch (Exception e) {
            log.error("处理任务结果失败: taskId={}", result.getTaskId(), e);
            return false;
        }
    }
}