package com.example.words.service.graph;

import com.example.words.model.WordVocabulary;
import com.example.words.model.graph.CategoryNode;
import com.example.words.model.graph.VocabularyFileNode;
import com.example.words.repository.graph.CategoryGraphRepository;
import com.example.words.repository.graph.VocabularyFileGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GraphSyncService {

    private static final Logger log = LoggerFactory.getLogger(GraphSyncService.class);

    private final VocabularyFileGraphRepository vocabularyFileGraphRepository;
    private final CategoryGraphRepository categoryGraphRepository;

    public GraphSyncService(
            VocabularyFileGraphRepository vocabularyFileGraphRepository,
            CategoryGraphRepository categoryGraphRepository) {
        this.vocabularyFileGraphRepository = vocabularyFileGraphRepository;
        this.categoryGraphRepository = categoryGraphRepository;
    }

    @Transactional
    public int syncFromDatabase(List<WordVocabulary> vocabularies) {
        log.info("Starting sync from database to graph, total records: {}", vocabularies.size());

        int count = 0;
        Set<String> processedCategories = new HashSet<>();

        for (WordVocabulary vocab : vocabularies) {
            try {
                VocabularyFileNode node = vocabularyFileGraphRepository.findByFileName(vocab.getFileName())
                        .orElse(new VocabularyFileNode());

                node.setFileName(vocab.getFileName());
                node.setFilePath(vocab.getFilePath());
                node.setFileSize(vocab.getFileSize());
                node.setCategory(vocab.getCategory());

                String categoryName = vocab.getCategory();
                if (categoryName != null && !categoryName.isEmpty() && !processedCategories.contains(categoryName)) {
                    CategoryNode categoryNode = categoryGraphRepository.findByName(categoryName)
                            .orElse(new CategoryNode(categoryName, "vocabulary"));

                    Set<VocabularyFileNode> files = new HashSet<>();
                    files.add(node);
                    categoryNode.setFiles(files);

                    categoryGraphRepository.save(categoryNode);
                    processedCategories.add(categoryName);
                }

                vocabularyFileGraphRepository.save(node);
                count++;

                if (count % 100 == 0) {
                    log.info("Synced {} records to graph", count);
                }
            } catch (Exception e) {
                log.error("Error syncing record {}: {}", vocab.getFileName(), e.getMessage());
            }
        }

        log.info("Graph sync completed, total records synced: {}", count);
        return count;
    }

    @Transactional
    public int syncFromDirectory(String directoryPath) {
        log.info("Starting sync from directory to graph: {}", directoryPath);
        return 0;
    }

    public long getGraphNodeCount() {
        return vocabularyFileGraphRepository.count();
    }

    public long getCategoryCount() {
        return categoryGraphRepository.count();
    }

    @Transactional
    public void clearGraph() {
        vocabularyFileGraphRepository.deleteAll();
        categoryGraphRepository.deleteAll();
        log.info("Graph database cleared");
    }
}
