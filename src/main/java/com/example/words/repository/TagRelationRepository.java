package com.example.words.repository;

import com.example.words.model.TagRelation;
import com.example.words.model.TagResourceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRelationRepository extends JpaRepository<TagRelation, Long> {

    List<TagRelation> findByTagId(Long tagId);

    List<TagRelation> findByResourceTypeAndResourceId(TagResourceType resourceType, Long resourceId);
}
