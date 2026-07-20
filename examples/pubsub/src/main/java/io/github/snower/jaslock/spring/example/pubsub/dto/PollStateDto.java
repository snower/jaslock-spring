package io.github.snower.jaslock.spring.example.pubsub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PollStateDto {
    private ObjectId topicId;
    private Long clientId;
    private Long lastMessageId;
}
