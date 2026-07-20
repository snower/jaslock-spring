package io.github.snower.jaslock.spring.example.pubsub.service;

import io.github.snower.jaslock.spring.boot.annotations.Lock;
import io.github.snower.jaslock.spring.example.pubsub.entity.PubsubTopic;
import io.github.snower.jaslock.spring.example.pubsub.repository.PubsubTopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PubsubTopicService {
    @Autowired
    private PubsubTopicRepository pubsubTopicRepository;

    public PubsubTopic getTopic(String topicKey) {
        return pubsubTopicRepository.getTopic(topicKey);
    }

    @Lock("notification_pubsub_topic_{topicKey}")
    public PubsubTopic createTopic(String topicKey) {
        PubsubTopic pubsubTopic = pubsubTopicRepository.getTopic(topicKey);
        if (pubsubTopic != null) {
            return pubsubTopic;
        }

        pubsubTopic = new PubsubTopic();
        pubsubTopic.setTopicKey(topicKey);
        pubsubTopic = pubsubTopicRepository.save(pubsubTopic);
        return pubsubTopic;
    }
}
