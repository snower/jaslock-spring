package io.github.snower.jaslock.spring.example.pollingrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class PollingTaskDTO {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务方法")
    private String method;

    @Schema(description = "请求参数")
    private String payload;

    @Schema(description = "任务创建时间")
    private Date createTime;

    @Schema(description = "过期时间")
    private Date expireTime;
}