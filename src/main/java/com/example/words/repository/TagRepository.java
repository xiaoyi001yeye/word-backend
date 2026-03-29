package com.example.words.repository;

import com.example.words.model.Tag;
import com.example.words.model.TagType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByDictionaryIdAndTypeOrderBySortKeyAsc(Long dictionaryId, TagType type);

    List<Tag> findByParentIdOrderBySortOrderAsc(Long parentId);

    boolean existsByParentId(Long parentId);

    Optional<Tag> findByDictionaryIdAndTypeAndParentIdIsNullAndName(Long dictionaryId, TagType type, String name);
}
