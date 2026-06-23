package com.example.words.repository;

import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageConfigStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoStorageConfigRepository extends JpaRepository<VideoStorageConfig, Long> {

    List<VideoStorageConfig> findAllByOrderByUpdatedAtDesc();

    List<VideoStorageConfig> findByStatusOrderByUpdatedAtDesc(VideoStorageConfigStatus status);

    Optional<VideoStorageConfig> findByIsDefaultTrue();

    boolean existsByConfigName(String configName);

    boolean existsByConfigNameAndIdNot(String configName, Long id);

    long countByStatus(VideoStorageConfigStatus status);

    @Modifying
    @Query("update VideoStorageConfig c set c.isDefault = false where c.isDefault = true")
    int clearDefault();

    @Modifying
    @Query("update VideoStorageConfig c set c.isDefault = false where c.id <> :id and c.isDefault = true")
    int clearDefaultByIdNot(@Param("id") Long id);
}
