package io.github.snower.jaslock.spring.example.pubsub.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "t_pubsub_topic")
public class PubsubTopic extends BaseEntity {
    @Field(name = "topic_key")
    private String topicKey;
}
