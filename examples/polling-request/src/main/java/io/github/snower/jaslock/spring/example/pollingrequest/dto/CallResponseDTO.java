package io.github.snower.jaslock.spring.example.pollingrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallResponseDTO {
    @Schema(description = "响应数据")
    private String data;

    @Schema(description = "错误码")
    private Integer code;

    @Schema(description = "错误信息")
    private String msg;

    @Schema(description = "处理耗时（毫秒）")
    private Long costTime;
}
