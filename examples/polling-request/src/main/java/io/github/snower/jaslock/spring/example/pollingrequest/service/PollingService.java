package io.github.snower.jaslock.spring.example.pollingrequest.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.CallRequestDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.CallResponseDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.PollingTaskDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.TaskResultDTO;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public interface PollingService {
    <T> T requestTask(ObjectId appId, String bizType, String method, Object payload, TypeReference<T> valueTypeRef);

    <T> T requestTask(ObjectId appId, String bizType, String method, Object payload, Class<T> resultType);

    void pushTask(ObjectId appId, String bizType, String method, Object payload);

    CallResponseDTO requestTask(CallRequestDTO request);

    void pushTask(CallRequestDTO request);

    List<PollingTaskDTO> loadTasks(ObjectId appId, Long clientId, String lastTaskId);

    boolean handleTaskResult(ObjectId appId, TaskResultDTO result);
}
