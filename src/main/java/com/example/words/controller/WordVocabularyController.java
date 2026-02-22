package com.example.words.controller;

import com.example.words.model.WordVocabulary;
import com.example.words.service.WordVocabularyService;
import com.example.words.service.graph.GraphSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WordVocabularyController {

    private static final Logger log = LoggerFactory.getLogger(WordVocabularyController.class);

    private final WordVocabularyService wordVocabularyService;
    private final GraphSyncService graphSyncService;
    private final JdbcTemplate jdbcTemplate;

    public WordVocabularyController(
            WordVocabularyService wordVocabularyService,
            GraphSyncService graphSyncService,
            JdbcTemplate jdbcTemplate) {
        this.wordVocabularyService = wordVocabularyService;
        this.graphSyncService = graphSyncService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/vocabularies")
    public List<WordVocabulary> list() {
        return wordVocabularyService.findAll();
    }

    @GetMapping("/vocabularies/{id}")
    public ResponseEntity<WordVocabulary> get(@PathVariable Long id) {
        return wordVocabularyService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vocabularies/category/{category}")
    public List<WordVocabulary> getByCategory(@PathVariable String category) {
        return wordVocabularyService.findByCategory(category);
    }

    @PostMapping("/vocabularies/import")
    public ResponseEntity<Map<String, Object>> importFiles() {
        int count = wordVocabularyService.importFilesFromDirectory();
        return ResponseEntity.ok(Map.of(
                "message", "Files imported successfully",
                "count", count
        ));
    }

    @DeleteMapping("/vocabularies")
    public ResponseEntity<Void> deleteAll() {
        wordVocabularyService.deleteAll();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/db/init")
    public ResponseEntity<Map<String, Object>> initDatabase() {
        try {
            String[] sqlStatements = {
                "CREATE SEQUENCE IF NOT EXISTS word_vocabulary_id_seq START WITH 1 INCREMENT BY 1",
                "CREATE TABLE IF NOT EXISTS word_vocabulary (" +
                    "id BIGINT DEFAULT nextval('word_vocabulary_id_seq') PRIMARY KEY, " +
                    "file_name VARCHAR(500) NOT NULL, " +
                    "file_path VARCHAR(1000), " +
                    "file_size BIGINT, " +
                    "category VARCHAR(100), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")",
                "CREATE INDEX IF NOT EXISTS idx_word_vocabulary_file_name ON word_vocabulary(file_name)",
                "CREATE INDEX IF NOT EXISTS idx_word_vocabulary_category ON word_vocabulary(category)",
                "DO $$ " +
                "BEGIN " +
                "    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_word_vocabulary_file_name') THEN " +
                "        ALTER TABLE word_vocabulary ADD CONSTRAINT uk_word_vocabulary_file_name UNIQUE (file_name); " +
                "    END IF; " +
                "END $$"
            };

            for (String sql : sqlStatements) {
                jdbcTemplate.execute(sql);
            }

            log.info("Database initialization completed successfully");
            return ResponseEntity.ok(Map.of(
                    "message", "Database initialized successfully",
                    "status", "success"
            ));
        } catch (Exception e) {
            log.error("Database initialization failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Database initialization failed",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/db/status")
    public ResponseEntity<Map<String, Object>> getDbStatus() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM word_vocabulary", Integer.class);
            return ResponseEntity.ok(Map.of(
                    "status", "connected",
                    "table_exists", true,
                    "record_count", count != null ? count : 0
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "connected",
                    "table_exists", false,
                    "record_count", 0
            ));
        }
    }

    @PostMapping("/graph/sync")
    public ResponseEntity<Map<String, Object>> syncToGraph() {
        try {
            List<WordVocabulary> vocabularies = wordVocabularyService.findAll();
            int count = graphSyncService.syncFromDatabase(vocabularies);
            return ResponseEntity.ok(Map.of(
                    "message", "Graph sync completed",
                    "status", "success",
                    "synced_count", count
            ));
        } catch (Exception e) {
            log.error("Graph sync failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Graph sync failed",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/graph/status")
    public ResponseEntity<Map<String, Object>> getGraphStatus() {
        try {
            long nodeCount = graphSyncService.getGraphNodeCount();
            long categoryCount = graphSyncService.getCategoryCount();
            return ResponseEntity.ok(Map.of(
                    "status", "connected",
                    "node_count", nodeCount,
                    "category_count", categoryCount
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "disconnected",
                    "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/graph/clear")
    public ResponseEntity<Map<String, Object>> clearGraph() {
        try {
            graphSyncService.clearGraph();
            return ResponseEntity.ok(Map.of(
                    "message", "Graph cleared successfully",
                    "status", "success"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Failed to clear graph",
                    "error", e.getMessage()
            ));
        }
    }
}
