package io.github.snower.jaslock.spring.example.pollingrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.bson.types.ObjectId;

@Data
public class CallRequestDTO {

    @Schema(description = "应用id")
    @NotNull(message = "应用id不能为空")
    private ObjectId appId;

    @Schema(description = "业务类型")
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    @Schema(description = "业务方法")
    @NotBlank(message = "方法名不能为空")
    private String method;

    @Schema(description = "请求参数（JSON格式）")
    private String payload;

    @Schema(description = "超时时间（秒），默认45秒")
    private Integer timeout = 45;
}
