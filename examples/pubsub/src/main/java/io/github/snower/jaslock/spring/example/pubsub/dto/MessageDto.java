package io.github.snower.jaslock.spring.example.pubsub.dto;

import io.github.snower.jaslock.spring.example.pubsub.entity.PubsubMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
public class MessageDto {
    @Schema(description = "消息ID")
    private Long messageId;

    @Schema(description = "消息名称")
    private String messageName;

    @Schema(description = "消息数据")
    private Map<String, Object> messageData;

    @Schema(description = "消息时间")
    private Date messageTime;

    public static MessageDto fromEntity(PubsubMessage pubsubMessage) {
        MessageDto messageDto = new MessageDto();
        messageDto.setMessageId(pubsubMessage.getMessageId());
        messageDto.setMessageName(pubsubMessage.getMessageName());
        messageDto.setMessageData(pubsubMessage.getMessageData());
        messageDto.setMessageTime(pubsubMessage.getMessageTime());
        return  messageDto;
    }
}
