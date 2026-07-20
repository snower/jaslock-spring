package io.github.snower.jaslock.spring.example.pollingrequest.repository;

import io.github.snower.jaslock.spring.example.pollingrequest.entity.PollingTaskResultEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 轮询任务结果访问层
 */
@Repository
public interface PollingTaskResultRepository extends MongoRepository<PollingTaskResultEntity, ObjectId> {

}
