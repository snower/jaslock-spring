package io.github.snower.jaslock.spring.example.pollingrequest.controller.api;

import io.github.snower.jaslock.Event;
import io.github.snower.jaslock.commands.ICommand;
import io.github.snower.jaslock.exceptions.EventWaitTimeoutException;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.PollingTaskDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.TaskResultDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.service.PollingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Tag(name = "PollingController", description = "轮询调用服务")
@RestController
@RequestMapping("/api/app")
public class PollingController {
    @Autowired
    private PollingService pollingService;

    @Autowired
    private SlockTemplate slockTemplate;

    private static final String SLOCK_APP_PREFIX = "polling:app:";

    @GetMapping(value = "/v1/polling")
    @Operation(summary = "长轮询获取待处理任务", description = "医院内部系统调用此接口，使用 atomicTemplate 等待新任务")
    public DeferredResult<List<PollingTaskDTO>> polling(@RequestParam String appId,
                                                        @RequestParam Long clientId,
                                                        @RequestParam(defaultValue = "", required = false) String lastTaskId,
                                                        @RequestParam(defaultValue = "45", required = false) Integer timeout) {
        if (clientId == null || clientId <= 0) {
            throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, "参数错误");
        }
        String hospitalKey = SLOCK_APP_PREFIX + appId;
        Event event = slockTemplate.newEvent(hospitalKey, 5, timeout * 2, true);
        event.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
        AtomicBoolean isCompletion = new AtomicBoolean(false);
        DeferredResult<List<PollingTaskDTO>> deferredResult = new DeferredResult<>((timeout + 15) * 1000L);
        deferredResult.onCompletion(() -> isCompletion.set(true));
        deferredResult.onError(e -> isCompletion.set(true));
        deferredResult.onTimeout(() -> {
            if (!isCompletion.compareAndSet(false, true)) return;
            deferredResult.setErrorResult(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "等待超时"));
        });
        try {
            event.waitAndTimeoutRetryClear(timeout, e -> {
                if (!isCompletion.compareAndSet(false, true)) return;
                try {
                    e.getResult();
                    try {
                        event.clear();
                    } catch (Exception ignored) {}
                    List<PollingTaskDTO> tasks = pollingService.loadTasks(new ObjectId(appId), clientId, lastTaskId);
                    deferredResult.setResult(tasks);
                } catch (EventWaitTimeoutException ex) {
                    deferredResult.setErrorResult(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "等待超时"));
                } catch (Throwable ex) {
                    deferredResult.setErrorResult(ex);
                }
            });
            return deferredResult;
        } catch (Exception e) {
            deferredResult.setErrorResult(e);
            return deferredResult;
        }
    }

    @PostMapping(value = "/v1/result")
    @Operation(summary = "提交任务处理结果")
    public Boolean submitResult(@RequestParam String appId,
                                @RequestBody @Validated TaskResultDTO result) {
        boolean success = pollingService.handleTaskResult(new ObjectId(appId), result);
        if (success) {
            return true;
        } else {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "任务不存在或已超时");
        }
    }
}
