package com.example.words.controller;

import com.example.words.model.MetaWord;
import com.example.words.service.MetaWordService;
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
@RequestMapping("/api/meta-words")
public class MetaWordController {

    private final MetaWordService metaWordService;

    public MetaWordController(MetaWordService metaWordService) {
        this.metaWordService = metaWordService;
    }

    @GetMapping
    public List<MetaWord> list() {
        return metaWordService.findAll();
    }

    @GetMapping("/search")
    public List<MetaWord> search(@RequestParam String prefix) {
        return metaWordService.findByWordStartingWith(prefix);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetaWord> get(@PathVariable Long id) {
        return metaWordService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/word/{word}")
    public ResponseEntity<MetaWord> getByWord(@PathVariable String word) {
        return metaWordService.findByWord(word)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/difficulty/{difficulty}")
    public List<MetaWord> getByDifficulty(@PathVariable Integer difficulty) {
        return metaWordService.findByDifficulty(difficulty);
    }

    @PostMapping
    public ResponseEntity<MetaWord> create(@RequestBody MetaWord metaWord) {
        MetaWord saved = metaWordService.save(metaWord);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        metaWordService.deleteAll();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromBooks() {
        int count = metaWordService.importFromBooksDirectory();
        return ResponseEntity.ok(Map.of(
                "message", "Words imported successfully",
                "count", count
        ));
    }
}
