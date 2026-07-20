package io.github.snower.jaslock.spring.example.pubsub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@Valid
public class PublishMessageDto {
    @Schema(description = "房间KEY")
    @NotBlank
    private String topicKey;

    @Schema(description = "消息名称")
    @NotBlank
    private String messageName;

    @Schema(description = "消息数据")
    private Map<String, Object> messageData;

    @Schema(description = "消息时间")
    private Date messageTime;

    @Schema(description = "加锁Key")
    private String requireLockKey;

    @Schema(description = "释放锁Key")
    private String releaseLockKey;
}
