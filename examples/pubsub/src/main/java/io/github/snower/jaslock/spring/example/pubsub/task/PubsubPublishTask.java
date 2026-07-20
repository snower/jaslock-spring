package io.github.snower.jaslock.spring.example.pubsub.task;

import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.example.pubsub.dto.PublishMessageDto;
import io.github.snower.jaslock.spring.example.pubsub.service.PubsubMessageService;
import io.github.snower.jaslock.Lock;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RabbitListener(queues = "#{queue.name}")
public class PubsubPublishTask {
    @Autowired
    private SlockTemplate slockTemplate;

    @Autowired
    private PubsubMessageService pubsubMessageService;

    @RabbitHandler
    public void publish(PublishMessageDto messageDto) throws Exception {
        if (StringUtils.hasLength(messageDto.getRequireLockKey())) {
            try (AutoCloseable autoCloseable = slockTemplate.newLock(messageDto.getRequireLockKey(), 30, 300).with()) {
                pubsubMessageService.publish(messageDto);
                return;
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
            pubsubMessageService.publish(messageDto);
        } finally {
            if (StringUtils.hasLength(messageDto.getReleaseLockKey())) {
                Lock lock = slockTemplate.newLock(messageDto.getReleaseLockKey(), 30, 300);
                try {
                    lock.releaseHead();
                } catch (Exception ignored) {}
            }
        }
    }
}
