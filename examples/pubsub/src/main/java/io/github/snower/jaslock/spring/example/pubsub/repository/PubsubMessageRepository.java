package io.github.snower.jaslock.spring.example.pubsub.repository;

import io.github.snower.jaslock.spring.example.pubsub.entity.PubsubMessage;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface PubsubMessageRepository extends MongoRepository<PubsubMessage, ObjectId> {
    @Query("{topic_id: ?0, message_id: {$gt: ?1}, is_deleted: 0}")
    List<PubsubMessage> getPubsubMessages(ObjectId topicId, Long lastMessageId, Pageable pageable);
}
