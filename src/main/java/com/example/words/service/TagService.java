package com.example.words.service;

import com.example.words.dto.CreateTagRequest;
import com.example.words.dto.TagTreeNodeResponse;
import com.example.words.dto.UpdateTagRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.Tag;
import com.example.words.model.TagType;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.TagRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    public static final String DEFAULT_CHAPTER_NAME = "默认章节";

    private final TagRepository tagRepository;
    private final DictionaryWordRepository dictionaryWordRepository;

    public TagService(TagRepository tagRepository, DictionaryWordRepository dictionaryWordRepository) {
        this.tagRepository = tagRepository;
        this.dictionaryWordRepository = dictionaryWordRepository;
    }

    public Optional<Tag> findById(Long id) {
        return tagRepository.findById(id);
    }

    public List<TagTreeNodeResponse> getTagTree(Long dictionaryId, TagType type) {
        List<Tag> tags = tagRepository.findByDictionaryIdAndTypeOrderBySortKeyAsc(dictionaryId, type);
        Map<Long, TagTreeNodeResponse> nodeMap = new LinkedHashMap<>();
        List<TagTreeNodeResponse> roots = new ArrayList<>();
        for (Tag tag : tags) {
            nodeMap.put(tag.getId(), new TagTreeNodeResponse(
                    tag.getId(),
                    tag.getName(),
                    tag.getType(),
                    tag.getSortOrder(),
                    tag.getPathName(),
                    new ArrayList<>()
            ));
        }
        for (Tag tag : tags) {
            TagTreeNodeResponse node = nodeMap.get(tag.getId());
            if (tag.getParentId() == null) {
                roots.add(node);
                continue;
            }
            TagTreeNodeResponse parent = nodeMap.get(tag.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Transactional
    public Tag createChapterTag(Long dictionaryId, CreateTagRequest request) {
        Tag parent = validateParent(dictionaryId, request.getParentId());
        Tag tag = new Tag();
        tag.setName(request.getName().trim());
        tag.setType(TagType.CHAPTER);
        tag.setDictionaryId(dictionaryId);
        tag.setParentId(request.getParentId());
        tag.setSortOrder(normalizeSortOrder(request.getSortOrder()));
        tag.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        tag.setPathName("");
        tag.setPathKey("");
        tag.setSortKey("");
        Tag saved = tagRepository.save(tag);
        refreshTagPath(saved, parent);
        return saved;
    }

    @Transactional
    public Tag updateTag(Long tagId, UpdateTagRequest request) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
        Tag parent = validateParent(tag.getDictionaryId(), request.getParentId());
        if (parent != null && parent.getId().equals(tagId)) {
            throw new BadRequestException("Tag cannot be its own parent");
        }
        if (parent != null && isDescendantOf(parent.getId(), tagId)) {
            throw new BadRequestException("Tag cannot move under its descendant");
        }

        tag.setName(request.getName().trim());
        tag.setParentId(request.getParentId());
        tag.setSortOrder(normalizeSortOrder(request.getSortOrder()));
        tag.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        Tag saved = tagRepository.save(tag);
        refreshTagPath(saved, parent);
        refreshDescendants(saved);
        return saved;
    }

    @Transactional
    public void deleteTag(Long tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
        if (tagRepository.existsByParentId(tagId)) {
            throw new BadRequestException("Tag has child nodes and cannot be deleted");
        }
        if (dictionaryWordRepository.countByChapterTagId(tagId) > 0) {
            throw new BadRequestException("Tag is referenced by dictionary words and cannot be deleted");
        }
        tagRepository.delete(tag);
    }

    @Transactional
    public Long getOrCreateDefaultChapterTagId(Long dictionaryId) {
        return tagRepository.findByDictionaryIdAndTypeAndParentIdIsNullAndName(dictionaryId, TagType.CHAPTER, DEFAULT_CHAPTER_NAME)
                .map(Tag::getId)
                .orElseGet(() -> createDefaultChapterTag(dictionaryId).getId());
    }

    public Tag getChapterTagOrThrow(Long tagId, Long dictionaryId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
        if (tag.getType() != TagType.CHAPTER || !dictionaryId.equals(tag.getDictionaryId())) {
            throw new BadRequestException("Tag does not belong to the dictionary chapter tree");
        }
        return tag;
    }

    private Tag createDefaultChapterTag(Long dictionaryId) {
        Tag tag = new Tag();
        tag.setName(DEFAULT_CHAPTER_NAME);
        tag.setType(TagType.CHAPTER);
        tag.setDictionaryId(dictionaryId);
        tag.setParentId(null);
        tag.setSortOrder(1);
        tag.setLevel(1);
        tag.setPathName("");
        tag.setPathKey("");
        tag.setSortKey("");
        Tag saved = tagRepository.save(tag);
        refreshTagPath(saved, null);
        return saved;
    }

    private Tag validateParent(Long dictionaryId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Tag parent = tagRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent tag not found: " + parentId));
        if (parent.getType() != TagType.CHAPTER || !dictionaryId.equals(parent.getDictionaryId())) {
            throw new BadRequestException("Parent tag does not belong to the dictionary chapter tree");
        }
        return parent;
    }

    private boolean isDescendantOf(Long candidateId, Long ancestorId) {
        Long currentId = candidateId;
        while (currentId != null) {
            Tag current = tagRepository.findById(currentId).orElse(null);
            if (current == null) {
                return false;
            }
            if (ancestorId.equals(current.getParentId())) {
                return true;
            }
            currentId = current.getParentId();
        }
        return false;
    }

    private void refreshDescendants(Tag parent) {
        List<Tag> children = tagRepository.findByParentIdOrderBySortOrderAsc(parent.getId());
        for (Tag child : children) {
            refreshTagPath(child, parent);
            refreshDescendants(child);
        }
    }

    private void refreshTagPath(Tag tag, Tag parent) {
        tag.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        tag.setPathName(parent == null ? tag.getName() : parent.getPathName() + " > " + tag.getName());
        tag.setPathKey(parent == null ? String.valueOf(tag.getId()) : parent.getPathKey() + "/" + tag.getId());
        tag.setSortKey(parent == null
                ? pad(tag.getSortOrder())
                : parent.getSortKey() + "." + pad(tag.getSortOrder()));
        tagRepository.save(tag);
    }

    private int normalizeSortOrder(Integer sortOrder) {
        return sortOrder == null || sortOrder < 1 ? 1 : sortOrder;
    }

    private String pad(Integer sortOrder) {
        return String.format("%06d", normalizeSortOrder(sortOrder));
    }
}
