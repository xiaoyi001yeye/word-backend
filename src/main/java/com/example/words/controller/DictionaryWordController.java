package com.example.words.controller;

import com.example.words.dto.AddWordsToDictionaryRequest;
import com.example.words.dto.AddWordListRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.service.DictionaryWordService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionary-words")
@Validated
public class DictionaryWordController {

    private final DictionaryWordService dictionaryWordService;

    public DictionaryWordController(DictionaryWordService dictionaryWordService) {
        this.dictionaryWordService = dictionaryWordService;
    }

    @GetMapping("/dictionary/{dictionaryId}/words")
    public Page<MetaWord> getWordsByDictionary(
            @PathVariable Long dictionaryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return dictionaryWordService.findMetaWordsByDictionaryId(dictionaryId, page, size);
    }

    @GetMapping("/dictionary/{dictionaryId}")
    public List<DictionaryWord> getByDictionary(@PathVariable Long dictionaryId) {
        return dictionaryWordService.findByDictionaryId(dictionaryId);
    }

    @GetMapping("/word/{metaWordId}")
    public List<DictionaryWord> getByWord(@PathVariable Long metaWordId) {
        return dictionaryWordService.findByMetaWordId(metaWordId);
    }

    @PostMapping("/{dictionaryId}/{metaWordId}")
    public ResponseEntity<DictionaryWord> create(
            @PathVariable Long dictionaryId,
            @PathVariable Long metaWordId) {
        dictionaryWordService.saveIfNotExists(dictionaryId, metaWordId);
        return dictionaryWordService.findByDictionaryIdAndMetaWordId(dictionaryId, metaWordId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{dictionaryId}/batch")
    public ResponseEntity<Map<String, Object>> addWordsToDictionary(
            @PathVariable Long dictionaryId,
            @RequestBody AddWordsToDictionaryRequest request) {
        dictionaryWordService.saveAllBatch(dictionaryId, request.getMetaWordIds());
        return ResponseEntity.ok(Map.of(
                "message", "Words added to dictionary successfully",
                "dictionaryId", dictionaryId,
                "wordCount", request.getMetaWordIds().size()
        ));
    }

    @PostMapping("/{dictionaryId}/words/list")
    public ResponseEntity<Map<String, Object>> addWordListToDictionary(
            @PathVariable Long dictionaryId,
            @Valid @RequestBody AddWordListRequest request) {
        DictionaryWordService.WordListProcessResult result = dictionaryWordService.processWordList(dictionaryId, request.getWords());
        return ResponseEntity.ok(Map.of(
                "message", "Word list processed successfully",
                "dictionaryId", dictionaryId,
                "total", result.getTotal(),
                "existed", result.getExisted(),
                "created", result.getCreated(),
                "added", result.getAdded(),
                "failed", result.getFailed()
        ));
    }

    @DeleteMapping("/dictionary/{dictionaryId}")
    public ResponseEntity<Void> deleteByDictionary(@PathVariable Long dictionaryId) {
        dictionaryWordService.deleteByDictionaryId(dictionaryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        dictionaryWordService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
