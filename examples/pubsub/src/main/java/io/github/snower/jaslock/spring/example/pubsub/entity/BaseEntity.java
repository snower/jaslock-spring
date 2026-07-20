package io.github.snower.jaslock.spring.example.pubsub.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity implements Persistable<ObjectId> {
    @MongoId(FieldType.OBJECT_ID)
    @Field(name = "_id")
    protected ObjectId id;

    @Field(name = "is_deleted")
    @JsonIgnore
    protected Integer isDeleted = 0;
    @CreatedDate
    @Field(name = "create_time")
    protected Date createTime;
    @LastModifiedDate
    @Field(name = "update_time")
    protected Date updateTime;
    @Field(name = "row_version")
    @Version
    @JsonIgnore
    protected Integer rowVersion = 0;

    @Override
    public ObjectId getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return id == null;
    }
}
