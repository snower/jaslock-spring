package io.github.snower.jaslock.spring.example.pubsub.controller.api;

import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.example.pubsub.dto.MessageDto;
import io.github.snower.jaslock.spring.example.pubsub.dto.PollMessageDto;
import io.github.snower.jaslock.spring.example.pubsub.dto.PollStateDto;
import io.github.snower.jaslock.spring.example.pubsub.service.PubsubMessageService;
import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.commands.ICommand;
import io.github.snower.jaslock.datas.LockUnsetData;
import io.github.snower.jaslock.exceptions.LockLockedException;
import io.github.snower.jaslock.exceptions.LockTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/api/pubsub")
@Validated
public class PubsubController {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SlockTemplate slockTemplate;

    @Autowired
    private PubsubMessageService pubsubMessageService;

    @GetMapping("/message/polling")
    public DeferredResult<PollMessageDto> pollingMessages(@RequestParam @Valid @NotBlank String topicKey,
                                                                            @RequestParam(required = false) Long clientId,
                                                                            @RequestParam(required = false) Long lastMessageId,
                                                                            @RequestParam(defaultValue = "45", required = false) Integer timeout) throws SlockException {

        AtomicBoolean isCompletion = new AtomicBoolean(false);
        DeferredResult<PollMessageDto> deferredResult = new DeferredResult<>((timeout + 15) * 1000L);
        deferredResult.onCompletion(() -> isCompletion.set(true));
        deferredResult.onError(e -> isCompletion.set(true));
        deferredResult.onTimeout(() -> {
            if (!isCompletion.compareAndSet(false, true)) return;
            deferredResult.setErrorResult(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "等待超时") {
            });
        });
        try {
            if (clientId != null && clientId > 0 && lastMessageId != null && lastMessageId >= 0) {
                Lock waitLock = new Lock(slockTemplate.selectDatabase((byte) 0),
                        ("notification_pubsub_message_polling_" + topicKey).getBytes(StandardCharsets.UTF_8),
                        pubsubMessageService.encodeLockId(clientId, lastMessageId), timeout, 0, (short) 0xffff, (byte) 0);
                waitLock.setTimeoutFlag((short) (ICommand.TIMEOUT_FLAG_LESS_LOCK_VERSION_IS_LOCK_SUCCED));
                waitLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
                waitLock.acquire(r -> {
                    if (!isCompletion.compareAndSet(false, true)) return;
                    try {
                        r.getResult();
                        PollMessageDto pollMessageDto = null;
                        if (waitLock.getCurrentLockData() != null) {
                            try {
                                pollMessageDto = objectMapper
                                        .readValue(waitLock.getCurrentLockData().getDataAsBytes(), PollMessageDto.class);
                                if (pollMessageDto != null && pollMessageDto.getLastMessageId() == lastMessageId + 1) {
                                    pollMessageDto.setClientId(clientId);
                                } else {
                                    pollMessageDto = null;
                                }
                            } catch (Exception ignored) {}
                        }
                        if (pollMessageDto == null) {
                            pollMessageDto = pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId, timeout);
                        }
                        deferredResult.setResult(pollMessageDto);
                    } catch (LockTimeoutException ex) {
                        Lock wakupLock = new Lock(slockTemplate.selectDatabase((byte) 0),
                                ("notification_pubsub_message_polling_" + topicKey).getBytes(StandardCharsets.UTF_8),
                                pubsubMessageService.encodeLockId(0, lastMessageId), 0, Math.max(timeout*2, 120), (short) 0, (byte) 0);
                        wakupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
                        try {
                            wakupLock.acquire(ICommand.LOCK_FLAG_UPDATE_WHEN_LOCKED, waitLock.getCurrentLockData() != null ? new LockUnsetData() : null);
                            try {
                                wakupLock.release();
                            } catch (Exception ignored) {}
                            deferredResult.setErrorResult(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "等待超时"));
                        } catch (LockTimeoutException e) {
                            try {
                                PollMessageDto pollMessageDto = null;
                                if (wakupLock.getCurrentLockData() != null) {
                                    try {
                                        pollMessageDto = objectMapper
                                                .readValue(wakupLock.getCurrentLockData().getDataAsBytes(), PollMessageDto.class);
                                        if (pollMessageDto != null && pollMessageDto.getLastMessageId() == lastMessageId + 1) {
                                            pollMessageDto.setClientId(clientId);
                                        } else {
                                            pollMessageDto = null;
                                        }
                                    } catch (Exception ignored) {}
                                }
                                if (pollMessageDto == null) {
                                    pollMessageDto = pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId, timeout);
                                }
                                deferredResult.setResult(pollMessageDto);
                            } catch (SlockException exc) {
                                deferredResult.setErrorResult(exc);
                            }
                        } catch (SlockException e) {
                            deferredResult.setErrorResult(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "等待超时"));
                        }
                    } catch (Throwable ex) {
                        deferredResult.setErrorResult(ex);
                    }
                });
            } else {
                deferredResult.setResult(pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId, timeout));
            }
            return deferredResult;
        } catch (Exception e) {
            deferredResult.setErrorResult(e);
            return deferredResult;
        }
    }

    @GetMapping(value = "/message/sse/polling", produces = "text/event-stream")
    public SseEmitter ssePollingMessages(@RequestParam @Valid @NotBlank String topicKey,
                                         @RequestParam(required = false) Long clientId,
                                         @RequestParam(required = false) Long lastMessageId) {

        AtomicBoolean isCompletion = new AtomicBoolean(false);
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> isCompletion.set(true));
        emitter.onError(e -> isCompletion.set(true));
        emitter.onTimeout(() -> isCompletion.set(true));
        try {
            if (clientId != null && clientId > 0 && lastMessageId != null && lastMessageId >= 0) {
                doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, new AtomicLong(lastMessageId), 45);
            } else {
                PollMessageDto pollMessageDto = pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId, 45);
                pushSseEvent(emitter, isCompletion, "PushState", "0",
                        new PollStateDto(pollMessageDto.getTopicId(), pollMessageDto.getClientId(), pollMessageDto.getLastMessageId()));
                if (pollMessageDto.getMessages() != null && !pollMessageDto.getMessages().isEmpty()) {
                    for (MessageDto messageDto : pollMessageDto.getMessages()) {
                        pushSseEvent(emitter, isCompletion, "PushMessage", String.valueOf(messageDto.getMessageId()), messageDto);
                        if (Objects.equals(messageDto.getMessageName(), "__done__")) {
                            isCompletion.set(true);
                        }
                        if (isCompletion.get()) break;
                    }
                }
                if (isCompletion.get()) {
                    emitter.complete();
                    return emitter;
                }
                clientId = pollMessageDto.getClientId();
                lastMessageId = pollMessageDto.getLastMessageId();
                doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, new AtomicLong(lastMessageId), 45);
            }
            return emitter;
        } catch (Exception e) {
            emitter.complete();
            isCompletion.set(true);
            return emitter;
        }
    }

    private void doSsePollingMessagesLoop(SseEmitter emitter, AtomicBoolean isCompletion, String topicKey, Long clientId, AtomicLong lastMessageId, Integer timeout) {
        try {
            if (isCompletion.get()) return;
            Lock waitLock = new Lock(slockTemplate.selectDatabase((byte) 0),
                    ("notification_pubsub_message_polling_" + topicKey).getBytes(StandardCharsets.UTF_8),
                    pubsubMessageService.encodeLockId(clientId, lastMessageId.get()), timeout, 0, (short) 0xffff, (byte) 0);
            waitLock.setTimeoutFlag((short) (ICommand.TIMEOUT_FLAG_LESS_LOCK_VERSION_IS_LOCK_SUCCED));
            waitLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
            waitLock.acquire(r -> {
                if (isCompletion.get()) return;
                try {
                    r.getResult();
                    PollMessageDto pollMessageDto = null;
                    if (waitLock.getCurrentLockData() != null) {
                        try {
                            pollMessageDto = objectMapper
                                    .readValue(waitLock.getCurrentLockData().getDataAsBytes(), PollMessageDto.class);
                            if (pollMessageDto != null && pollMessageDto.getLastMessageId() == lastMessageId.get() + 1) {
                                pollMessageDto.setClientId(clientId);
                            } else {
                                pollMessageDto = null;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (pollMessageDto == null) {
                        pollMessageDto = pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId.get(), timeout);
                    }
                    lastMessageId.set(pollMessageDto.getLastMessageId());
                    if (pollMessageDto.getMessages() != null && !pollMessageDto.getMessages().isEmpty()) {
                        for (MessageDto messageDto : pollMessageDto.getMessages()) {
                            pushSseEvent(emitter, isCompletion, "PushMessage", String.valueOf(messageDto.getMessageId()), messageDto);
                            if (Objects.equals(messageDto.getMessageName(), "__done__")) {
                                isCompletion.set(true);
                                emitter.complete();
                                return;
                            }
                            if (isCompletion.get()) break;
                        }
                        if (!isCompletion.get()) {
                            doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, lastMessageId, timeout);
                        }
                        return;
                    }
                } catch (LockTimeoutException ex) {
                    Lock wakupLock = new Lock(slockTemplate.selectDatabase((byte) 0),
                            ("notification_pubsub_message_polling_" + topicKey).getBytes(StandardCharsets.UTF_8),
                            pubsubMessageService.encodeLockId(0, lastMessageId.get()), 0, Math.max(timeout*2, 120), (short) 0, (byte) 0);
                    wakupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
                    try {
                        wakupLock.acquire(ICommand.LOCK_FLAG_UPDATE_WHEN_LOCKED, waitLock.getCurrentLockData() != null ? new LockUnsetData() : null);
                        try {
                            wakupLock.release();
                        } catch (Exception ignored) {}
                    } catch (LockTimeoutException e) {
                        try {
                            PollMessageDto pollMessageDto = null;
                            if (wakupLock.getCurrentLockData() != null) {
                                try {
                                    pollMessageDto = objectMapper
                                            .readValue(wakupLock.getCurrentLockData().getDataAsBytes(), PollMessageDto.class);
                                    if (pollMessageDto != null && pollMessageDto.getLastMessageId() == lastMessageId.get() + 1) {
                                        pollMessageDto.setClientId(clientId);
                                    } else {
                                        pollMessageDto = null;
                                    }
                                } catch (Exception ignored) {}
                            }
                            if (pollMessageDto == null) {
                                pollMessageDto = pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId.get(), timeout);
                            }
                            lastMessageId.set(pollMessageDto.getLastMessageId());
                            if (pollMessageDto.getMessages() != null && !pollMessageDto.getMessages().isEmpty()) {
                                for (MessageDto messageDto : pollMessageDto.getMessages()) {
                                    pushSseEvent(emitter, isCompletion, "PushMessage", String.valueOf(messageDto.getMessageId()), messageDto);
                                    if (Objects.equals(messageDto.getMessageName(), "__done__")) {
                                        isCompletion.set(true);
                                        emitter.complete();
                                        return;
                                    }
                                    if (isCompletion.get()) break;
                                }
                                if (!isCompletion.get()) {
                                    doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, lastMessageId, timeout);
                                }
                                return;
                            }
                        } catch (Throwable ee) {
                            pushSseEvent(emitter, isCompletion, "Error", "", new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, ee.toString()));
                            if (!isCompletion.get()) {
                                doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, lastMessageId, timeout);
                            }
                            return;
                        }
                    } catch (LockLockedException ignored) {
                    } catch (Throwable e) {
                        pushSseEvent(emitter, isCompletion, "Error", "", new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, e.toString()));
                        if (!isCompletion.get()) {
                            doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, lastMessageId, timeout);
                        }
                        return;
                    }
                } catch (Throwable ex) {
                    pushSseEvent(emitter, isCompletion, "Error", "", new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, ex.toString()));
                    if (!isCompletion.get()) {
                        doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, lastMessageId, timeout);
                    }
                    return;
                }

                if (!isCompletion.get()) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("Ping"));
                        if (!isCompletion.get()) {
                            doSsePollingMessagesLoop(emitter, isCompletion, topicKey, clientId, lastMessageId, timeout);
                        }
                    } catch (IOException e) {
                        emitter.complete();
                        isCompletion.set(true);
                    }
                }
            });
        } catch (Exception e) {
            emitter.complete();
            isCompletion.set(true);
        }
    }

    private void pushSseEvent(SseEmitter emitter, AtomicBoolean isCompletion, String eventName, String eventId, Object eventData) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(eventId)
                    .data(eventData, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.complete();
            isCompletion.set(true);
        }
    }
}
