package com.example.words.repository;

import com.example.words.model.AiConfig;
import com.example.words.model.AiConfigStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {

    List<AiConfig> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<AiConfig> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, AiConfigStatus status);

    Optional<AiConfig> findByUserIdAndIsDefaultTrue(Long userId);

    boolean existsByUserIdAndProviderNameAndApiUrlAndModelName(
            Long userId,
            String providerName,
            String apiUrl,
            String modelName);

    boolean existsByUserIdAndProviderNameAndApiUrlAndModelNameAndIdNot(
            Long userId,
            String providerName,
            String apiUrl,
            String modelName,
            Long id);

    long countByUserId(Long userId);

    @Modifying
    @Query("update AiConfig c set c.isDefault = false where c.userId = :userId and c.isDefault = true")
    int clearDefaultByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update AiConfig c set c.isDefault = false where c.userId = :userId and c.id <> :id and c.isDefault = true")
    int clearDefaultByUserIdAndIdNot(@Param("userId") Long userId, @Param("id") Long id);
}
