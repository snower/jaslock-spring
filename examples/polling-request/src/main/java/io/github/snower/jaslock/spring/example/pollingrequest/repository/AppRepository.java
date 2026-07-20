package io.github.snower.jaslock.spring.example.pollingrequest.repository;

import io.github.snower.jaslock.spring.example.pollingrequest.entity.AppEntity;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppRepository extends MongoRepository<AppEntity, ObjectId> {
    @Query("{is_deleted: 0}")
    Page<AppEntity> getApps(Pageable pageable);

    @Query("{name: {$regex: ?0}, is_deleted: 0}")
    Page<AppEntity> queryApps(String name, Pageable pageable);

    @Query("{_id: ?0, is_deleted: 0}")
    AppEntity getApp(ObjectId appId);

    @Query("{_id: {$in: ?0}, is_deleted: 0}")
    List<AppEntity> getAppsByIds(List<ObjectId> appIds);

    @Query("{app_key: ?0, is_deleted: 0}")
    AppEntity getAppByAppKey(String appKey);
}
