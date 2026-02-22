package com.example.words.controller;

import com.example.words.model.Dictionary;
import com.example.words.service.DictionaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping
    public List<Dictionary> list() {
        return dictionaryService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dictionary> get(@PathVariable Long id) {
        return dictionaryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    public List<Dictionary> getByCategory(@PathVariable String category) {
        return dictionaryService.findByCategory(category);
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importDictionaries() {
        int count = dictionaryService.importFromDirectory();
        return ResponseEntity.ok(Map.of(
                "message", "Dictionaries imported successfully",
                "count", count
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        dictionaryService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
