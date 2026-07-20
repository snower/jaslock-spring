package io.github.snower.jaslock.spring.example.pubsub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PollMessageDto {
    private ObjectId topicId;
    private Long clientId;
    private List<MessageDto> messages;
    private Long lastMessageId;
}
