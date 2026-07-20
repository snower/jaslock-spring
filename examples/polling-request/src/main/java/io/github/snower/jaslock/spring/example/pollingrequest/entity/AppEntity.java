package io.github.snower.jaslock.spring.example.pollingrequest.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "app")
public class AppEntity {
    @MongoId(FieldType.OBJECT_ID)
    @Field(name = "_id")
    @Schema(title = "ID", implementation = String.class)
    protected ObjectId id;

    @Field(name = "name")
    private String name;

    @Field(name = "app_key")
    private String appKey;

    @Field(name = "app_secret")
    private String appSecret;

    @Field(name = "is_deleted")
    @JsonIgnore
    protected Integer isDeleted = 0;
}
