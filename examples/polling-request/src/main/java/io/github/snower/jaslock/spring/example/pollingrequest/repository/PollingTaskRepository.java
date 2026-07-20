package io.github.snower.jaslock.spring.example.pollingrequest.repository;

import io.github.snower.jaslock.spring.example.pollingrequest.entity.PollingTaskEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 轮询任务数据访问层
 */
@Repository
public interface PollingTaskRepository extends MongoRepository<PollingTaskEntity, ObjectId> {

}
