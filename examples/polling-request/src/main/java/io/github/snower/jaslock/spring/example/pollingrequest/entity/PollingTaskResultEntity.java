package io.github.snower.jaslock.spring.example.pollingrequest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;


/**
 * 轮询任务结果实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "polling_task_result")
public class PollingTaskResultEntity {

    @Id
    private ObjectId id;

    /**
     * 目标医院编码
     */
    @Indexed
    private ObjectId appId;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务方法
     */
    private String method;

    /**
     * 响应数据（JSON格式）
     */
    private String data;

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误信息
     */
    private String msg;

    /**
     * 处理耗时（毫秒）
     */
    private Long costTime;

    /**
     * 任务创建时间
     */
    private Date createTime;
}
