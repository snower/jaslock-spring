package io.github.snower.jaslock.spring.example.pubsub.service;

import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.example.pubsub.dto.MessageDto;
import io.github.snower.jaslock.spring.example.pubsub.dto.PollMessageDto;
import io.github.snower.jaslock.spring.example.pubsub.dto.PollStateDto;
import io.github.snower.jaslock.spring.example.pubsub.dto.PublishMessageDto;
import io.github.snower.jaslock.spring.example.pubsub.entity.PubsubMessage;
import io.github.snower.jaslock.spring.example.pubsub.entity.PubsubTopic;
import io.github.snower.jaslock.spring.example.pubsub.repository.PubsubMessageRepository;
import io.github.sequence.snowflake.SnowflakeConfig;
import io.github.sequence.snowflake.core.DefaultSnowflakeSequence;
import io.github.sequence.snowflake.core.SnowflakeSequence;
import io.github.snower.jaslock.Lock;
import io.github.snower.jaslock.ReadWriteLock;
import io.github.snower.jaslock.commands.CapacityByteArrayOutputStream;
import io.github.snower.jaslock.commands.ICommand;
import io.github.snower.jaslock.datas.LockSetData;
import io.github.snower.jaslock.exceptions.LockUnlockedException;
import io.github.snower.jaslock.exceptions.SlockException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PubsubMessageService {
    private static final SnowflakeSequence snowflakeSequence = new DefaultSnowflakeSequence(SnowflakeConfig.millisSnowflakeConfig(1288834974657L, 30));

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SlockTemplate slockTemplate;

    @Autowired
    private JedisPool redisPoolFactory;

    @Autowired
    private PubsubMessageRepository pubsubMessageRepository;

    @Autowired
    private PubsubTopicService pubsubTopicService;

    public Long publish(PublishMessageDto messageDto) throws SlockException {
        String lockKey = "notification_pubsub_message_" + messageDto.getTopicKey();
        ReadWriteLock readWriteLock = slockTemplate.newReadWriteLock(lockKey, 15, 300);
        readWriteLock.acquireWrite();
        try {
            String cacheKey = "notification:pubsub:message:publish:latest:" + messageDto.getTopicKey();
            ObjectId topicId = null;
            Long latestMessageId = null;
            try (Jedis jedis = redisPoolFactory.getResource()) {
                String cacheValue = jedis.get(cacheKey);
                if (StringUtils.hasLength(cacheValue)) {
                    try {
                        PublishDto publishDto = objectMapper.readValue(cacheValue, PublishDto.class);
                        topicId = publishDto.getTopicId();
                        latestMessageId = publishDto.getLatestMessageId();
                    } catch (Exception ignored) {
                    }
                }
            }
            if (topicId == null) {
                PubsubTopic pubsubTopic = pubsubTopicService.createTopic(messageDto.getTopicKey());
                topicId = pubsubTopic.getId();
            }
            if (latestMessageId == null) {
                List<PubsubMessage> pubsubMessages = pubsubMessageRepository.getPubsubMessages(topicId, -1L,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "message_id")));
                if (pubsubMessages != null && !pubsubMessages.isEmpty()) {
                    latestMessageId = pubsubMessages.get(0).getMessageId();
                } else {
                    latestMessageId = 0L;
                }
            }

            PubsubMessage pubsubMessage = new PubsubMessage();
            pubsubMessage.setTopicId(topicId);
            pubsubMessage.setMessageId(latestMessageId + 1);
            pubsubMessage.setMessageName(messageDto.getMessageName());
            pubsubMessage.setMessageData(messageDto.getMessageData());
            pubsubMessage.setMessageTime(messageDto.getMessageTime() == null ? new Date() : messageDto.getMessageTime());
            pubsubMessage = pubsubMessageRepository.save(pubsubMessage);

            try (Jedis jedis = redisPoolFactory.getResource()) {
                try {
                    jedis.set(cacheKey, objectMapper
                            .writeValueAsString(new PublishDto(topicId, pubsubMessage.getMessageId())), SetParams.setParams().ex(86400));
                } catch (Exception ignored) {
                    jedis.del(cacheKey);
                }
            }

            byte[] wakeupLockKey = ("notification_pubsub_message_polling_" + messageDto.getTopicKey()).getBytes(StandardCharsets.UTF_8);
            LockSetData lockSetData = null;
            try {
                lockSetData = new LockSetData(objectMapper
                        .writeValueAsBytes(new PollMessageDto(topicId, 0L, Collections.singletonList(MessageDto.fromEntity(pubsubMessage)), pubsubMessage.getMessageId())));
            } catch (Exception ignored) {}
            Lock wakeupLock = new Lock(slockTemplate.selectDatabase((byte) 0), wakeupLockKey,
                    encodeLockId(0, 0), 0, 0, (short) 0, (byte) 0);
            wakeupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
            try {
                wakeupLock.releaseHead(lockSetData);
            } catch (LockUnlockedException ignored) {}
            wakeupLock = new Lock(slockTemplate.selectDatabase((byte) 0), wakeupLockKey,
                    encodeLockId(0, pubsubMessage.getMessageId()), 5, 300, (short) 0, (byte) 0);
            wakeupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
            wakeupLock.acquire();
            return pubsubMessage.getMessageId();
        } finally {
            try {
                readWriteLock.releaseWrite();
            } catch (SlockException ignored) {}
        }
    }
    
    public PollStateDto getCurrentState(String topicKey) throws SlockException {
        Long clientId = snowflakeSequence.nextId();
        String lockKey = "notification_pubsub_message_" + topicKey;
        ReadWriteLock readWriteLock = slockTemplate.newReadWriteLock(lockKey, 15, 300);
        readWriteLock.acquireRead();
        try {
            String cacheKey = "notification:pubsub:message:publish:latest:" + topicKey;
            ObjectId topicId = null;
            Long latestMessageId = null;
            try (Jedis jedis = redisPoolFactory.getResource()) {
                String cacheValue = jedis.get(cacheKey);
                if (StringUtils.hasLength(cacheValue)) {
                    try {
                        PublishDto publishDto = objectMapper.readValue(cacheValue, PublishDto.class);
                        topicId = publishDto.getTopicId();
                        latestMessageId = publishDto.getLatestMessageId();
                    } catch (Exception ignored) {}
                }
            }
            if (topicId == null) {
                PubsubTopic pubsubTopic = pubsubTopicService.getTopic(topicKey);
                if (pubsubTopic == null) {
                    return new PollStateDto(null, clientId, 0L);
                }
                topicId = pubsubTopic.getId();
            }
            if (latestMessageId == null) {
                List<PubsubMessage> pubsubMessages = pubsubMessageRepository.getPubsubMessages(topicId, 0L,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "message_id")));
                latestMessageId = pubsubMessages.get(0).getMessageId();
            }
            return new PollStateDto(topicId, clientId, latestMessageId);
        } finally {
            try {
                readWriteLock.releaseRead();
            } catch (SlockException ignored) {}
        } 
    }

    public PollMessageDto fetchMessages(String topicKey, Long clientId, Long lastMessageId, Integer timeout) throws SlockException {
        if (clientId == null || clientId <= 0) {
            clientId = snowflakeSequence.nextId();
        }
        String lockKey = "notification_pubsub_message_" + topicKey;
        ReadWriteLock readWriteLock = slockTemplate.newReadWriteLock(lockKey, 15, 300);
        readWriteLock.acquireRead();
        try {
            String cacheKey = "notification:pubsub:message:publish:latest:" + topicKey;
            ObjectId topicId = null;
            Long latestMessageId = null;
            try (Jedis jedis = redisPoolFactory.getResource()) {
                String cacheValue = jedis.get(cacheKey);
                if (StringUtils.hasLength(cacheValue)) {
                    try {
                        PublishDto publishDto = objectMapper.readValue(cacheValue, PublishDto.class);
                        topicId = publishDto.getTopicId();
                        latestMessageId = publishDto.getLatestMessageId();
                    } catch (Exception ignored) {}
                }
            }
            if (topicId == null) {
                PubsubTopic pubsubTopic = pubsubTopicService.getTopic(topicKey);
                if (pubsubTopic == null) {
                    lastMessageId = 0L;
                    return new PollMessageDto(null, clientId, new ArrayList<>(), lastMessageId);
                }
                topicId = pubsubTopic.getId();
            }
            if (lastMessageId == null || lastMessageId < 0) {
                if (latestMessageId == null) {
                    List<PubsubMessage> pubsubMessages = pubsubMessageRepository.getPubsubMessages(topicId, 0L,
                            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "message_id")));
                    latestMessageId = pubsubMessages.get(0).getMessageId();
                }
                lastMessageId = latestMessageId;
                return new PollMessageDto(topicId, clientId, new ArrayList<>(), lastMessageId);
            }
            List<PubsubMessage> pubsubMessages = pubsubMessageRepository.getPubsubMessages(topicId, lastMessageId,
                    PageRequest.of(0, 2000, Sort.by(Sort.Direction.ASC, "message_id")));
            if (!pubsubMessages.isEmpty()) {
                lastMessageId = pubsubMessages.get(pubsubMessages.size() - 1).getMessageId();
            }
            return new PollMessageDto(topicId, clientId, pubsubMessages.stream()
                    .map(MessageDto::fromEntity).collect(Collectors.toList()), lastMessageId);
        } finally {
            if (lastMessageId != null && lastMessageId >= 0) {
                Lock wakeupLock = new Lock(slockTemplate.selectDatabase((byte) 0),
                        ("notification_pubsub_message_polling_" + topicKey).getBytes(StandardCharsets.UTF_8),
                        encodeLockId(0, lastMessageId), 0, Math.max(timeout*2, 120), (short) 0, (byte) 0);
                wakeupLock.setExpriedFlag((short) ICommand.EXPRIED_FLAG_UNLIMITED_AOF_TIME);
                try {
                    wakeupLock.acquire();
                } catch (SlockException ignored) {}
            }
            try {
                readWriteLock.releaseRead();
            } catch (SlockException ignored) {}
        }
    }

    public byte[] encodeLockId(long clientId, long versionId) {
        ByteArrayOutputStream byteArrayOutputStream = new CapacityByteArrayOutputStream(16);
        byteArrayOutputStream.write((byte) (versionId & 0xff));
        byteArrayOutputStream.write((byte) ((versionId >> 8) & 0xff));
        byteArrayOutputStream.write((byte) ((versionId >> 16) & 0xff));
        byteArrayOutputStream.write((byte) ((versionId >> 24) & 0xff));
        byteArrayOutputStream.write((byte) ((versionId >> 32) & 0xff));
        byteArrayOutputStream.write((byte) ((versionId >> 40) & 0xff));
        byteArrayOutputStream.write((byte) ((versionId >> 48) & 0xff));
        byteArrayOutputStream.write((byte) ((versionId >> 56) & 0xff));
        byteArrayOutputStream.write((byte) (clientId & 0xff));
        byteArrayOutputStream.write((byte) ((clientId >> 8) & 0xff));
        byteArrayOutputStream.write((byte) ((clientId >> 16) & 0xff));
        byteArrayOutputStream.write((byte) ((clientId >> 24) & 0xff));
        byteArrayOutputStream.write((byte) ((clientId >> 32) & 0xff));
        byteArrayOutputStream.write((byte) ((clientId >> 40) & 0xff));
        byteArrayOutputStream.write((byte) ((clientId >> 48) & 0xff));
        byteArrayOutputStream.write((byte) ((clientId >> 56) & 0xff));
        return byteArrayOutputStream.toByteArray();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class PublishDto {
        private ObjectId topicId;
        private Long latestMessageId;
    }
}
