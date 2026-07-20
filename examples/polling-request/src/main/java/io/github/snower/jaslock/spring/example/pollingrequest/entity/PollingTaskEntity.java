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
 * 轮询任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "polling_task")
public class PollingTaskEntity {

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
     * 请求参数（JSON格式）
     */
    private String payload;

    /**
     * 已下发客户端ID
     */
    Long clientId;

    /**
     * 任务创建时间
     */
    private Date createTime;

    /**
     * 超时时间（秒）
     */
    private Integer timeout;

    /**
     * 过期时间
     */
    private Date expireTime;
}
