package io.github.snower.jaslock.spring.example.pubsub.controller.backend;

import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.example.pubsub.dto.PollMessageDto;
import io.github.snower.jaslock.spring.example.pubsub.dto.PollStateDto;
import io.github.snower.jaslock.spring.example.pubsub.dto.PublishMessageDto;
import io.github.snower.jaslock.spring.example.pubsub.service.PubsubMessageService;
import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.exceptions.SlockException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/backend/pubsub/message")
@Validated
public class PubsubMessageController {
    @Autowired
    private SlockTemplate slockTemplate;

    @Autowired
    private PubsubMessageService pubsubMessageService;

    @PostMapping("/publish")
    public Long publishMessage(@RequestBody @Valid PublishMessageDto messageDto) throws Exception {
        if (StringUtils.hasLength(messageDto.getRequireLockKey())) {
            try (AutoCloseable autoCloseable = slockTemplate.newLock(messageDto.getRequireLockKey(), 30, 300).with()) {
                return pubsubMessageService.publish(messageDto);
            } finally {
                if (StringUtils.hasLength(messageDto.getReleaseLockKey())) {
                    Lock lock = slockTemplate.newLock(messageDto.getReleaseLockKey(), 30, 300);
                    try {
                        lock.releaseHead();
                    } catch (Exception ignored) {}
                }
            }
        }
        try {
            return pubsubMessageService.publish(messageDto);
        } finally {
            if (StringUtils.hasLength(messageDto.getReleaseLockKey())) {
                Lock lock = slockTemplate.newLock(messageDto.getReleaseLockKey(), 30, 300);
                try {
                    lock.releaseHead();
                } catch (Exception ignored) {}
            }
        }
    }

    @GetMapping("/current")
    public PollStateDto getCurrentState(@RequestParam @Valid @NotBlank String topicKey) throws SlockException {
        return pubsubMessageService.getCurrentState(topicKey);
    }

    @GetMapping("/fetch")
    public PollMessageDto fetchMessages(@RequestParam @Valid @NotBlank String topicKey,
                                                          @RequestParam(required = false) Long clientId,
                                                          @RequestParam(required = false) Long lastMessageId,
                                                          @RequestParam(defaultValue = "45", required = false) Integer timeout) throws SlockException {
        return pubsubMessageService.fetchMessages(topicKey, clientId, lastMessageId, timeout);
    }
}
