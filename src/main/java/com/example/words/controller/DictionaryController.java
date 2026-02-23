package com.example.words.controller;

import com.example.words.model.Dictionary;
import com.example.words.service.DictionaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    public ResponseEntity<Dictionary> create(@RequestBody Dictionary dictionary) {
        Dictionary savedDictionary = dictionaryService.save(dictionary);
        return ResponseEntity.ok(savedDictionary);
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

    @DeleteMapping("/user-created")
    public ResponseEntity<Map<String, Object>> deleteUserCreatedDictionaries() {
        int deletedCount = dictionaryService.deleteUserCreatedDictionaries();
        return ResponseEntity.ok(Map.of(
                "message", "User-created dictionaries deleted successfully",
                "deletedCount", deletedCount
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteById(@PathVariable Long id) {
        boolean deleted = dictionaryService.deleteById(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of(
                    "message", "Dictionary deleted successfully",
                    "id", id
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Cannot delete imported dictionary",
                    "id", id
            ));
        }
    }
}
