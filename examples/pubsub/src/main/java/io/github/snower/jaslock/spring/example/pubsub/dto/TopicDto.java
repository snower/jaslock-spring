package io.github.snower.jaslock.spring.example.pubsub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Valid
public class TopicDto {
    @Schema(description = "房间KEY")
    @NotBlank
    private String topicKey;
}
