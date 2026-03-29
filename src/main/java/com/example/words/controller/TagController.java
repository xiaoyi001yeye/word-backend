package com.example.words.controller;

import com.example.words.dto.CreateTagRequest;
import com.example.words.dto.TagTreeNodeResponse;
import com.example.words.dto.UpdateTagRequest;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.Tag;
import com.example.words.model.TagType;
import com.example.words.service.AccessControlService;
import com.example.words.service.CurrentUserService;
import com.example.words.service.DictionaryService;
import com.example.words.service.TagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TagController {

    private final TagService tagService;
    private final DictionaryService dictionaryService;
    private final CurrentUserService currentUserService;
    private final AccessControlService accessControlService;

    public TagController(
            TagService tagService,
            DictionaryService dictionaryService,
            CurrentUserService currentUserService,
            AccessControlService accessControlService) {
        this.tagService = tagService;
        this.dictionaryService = dictionaryService;
        this.currentUserService = currentUserService;
        this.accessControlService = accessControlService;
    }

    @GetMapping("/dictionaries/{dictionaryId}/tags/tree")
    public List<TagTreeNodeResponse> getTagTree(
            @PathVariable Long dictionaryId,
            @RequestParam(defaultValue = "CHAPTER") TagType type) {
        ensureCanViewDictionary(dictionaryId);
        return tagService.getTagTree(dictionaryId, type);
    }

    @PostMapping("/dictionaries/{dictionaryId}/tags")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Tag> createTag(
            @PathVariable Long dictionaryId,
            @Valid @RequestBody CreateTagRequest request) {
        ensureCanManageDictionary(dictionaryId);
        return ResponseEntity.ok(tagService.createChapterTag(dictionaryId, request));
    }

    @PatchMapping("/tags/{tagId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Tag> updateTag(
            @PathVariable Long tagId,
            @Valid @RequestBody UpdateTagRequest request) {
        Tag tag = tagService.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
        ensureCanManageDictionary(tag.getDictionaryId());
        return ResponseEntity.ok(tagService.updateTag(tagId, request));
    }

    @DeleteMapping("/tags/{tagId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> deleteTag(@PathVariable Long tagId) {
        Tag tag = tagService.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
        ensureCanManageDictionary(tag.getDictionaryId());
        tagService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }

    private void ensureCanViewDictionary(Long dictionaryId) {
        AppUser actor = currentUserService.getCurrentUser();
        Dictionary dictionary = findDictionary(dictionaryId);
        accessControlService.ensureCanViewDictionary(actor, dictionary);
    }

    private void ensureCanManageDictionary(Long dictionaryId) {
        AppUser actor = currentUserService.getCurrentUser();
        Dictionary dictionary = findDictionary(dictionaryId);
        accessControlService.ensureCanManageDictionary(actor, dictionary);
    }

    private Dictionary findDictionary(Long dictionaryId) {
        return dictionaryService.findById(dictionaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionaryId));
    }
}
