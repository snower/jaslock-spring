package io.github.snower.jaslock.spring.example.pubsub.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "t_pubsub_messages")
public class PubsubMessage {
    @MongoId(FieldType.OBJECT_ID)
    @Field(name = "_id")
    protected ObjectId id;

    @Field(name = "topic_id")
    private ObjectId topicId;

    @Field(name = "message_id")
    private Long messageId;

    @Field(name = "message_name")
    private String messageName;

    @Field(name = "message_data")
    private Map<String, Object> messageData;

    @Field(name = "message_time")
    private Date messageTime;
}
