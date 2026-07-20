package io.github.snower.jaslock.spring.example.pubsub.repository;

import io.github.snower.jaslock.spring.example.pubsub.entity.PubsubTopic;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface PubsubTopicRepository extends MongoRepository<PubsubTopic, ObjectId> {
    @Query("{topic_key: '?0', is_deleted: 0}")
    PubsubTopic getTopic(String topicKey);
}
