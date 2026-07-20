package io.github.snower.jaslock.spring.example.pubsub.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "t_pubsub_topic")
public class PubsubTopic {
    @MongoId(FieldType.OBJECT_ID)
    @Field(name = "_id")
    protected ObjectId id;

    @Field(name = "topic_key")
    private String topicKey;
}
