package io.github.snower.jaslock.spring.example.pollingrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskResultDTO {

    /**
     * 任务ID
     */
    @Schema(description = "任务ID")
    @NotBlank(message = "taskId不能为空")
    private String taskId;
    /**
     * 响应数据
     */
    @Schema(description = "响应数据")
    private String data;

    /**
     * 错误码
     */
    @Schema(description = "错误码")
    private Integer code;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String msg;
}
