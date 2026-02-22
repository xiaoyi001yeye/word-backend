package com.example.words.service;

import com.example.words.model.WordVocabulary;
import com.example.words.repository.WordVocabularyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Optional;

@Service
public class WordVocabularyService {

    private static final Logger log = LoggerFactory.getLogger(WordVocabularyService.class);
    private static final String TRANSLATION_DIR = "/opt/translation";

    private final WordVocabularyRepository wordVocabularyRepository;

    public WordVocabularyService(WordVocabularyRepository wordVocabularyRepository) {
        this.wordVocabularyRepository = wordVocabularyRepository;
    }

    public List<WordVocabulary> findAll() {
        return wordVocabularyRepository.findAll();
    }

    public Optional<WordVocabulary> findById(Long id) {
        return wordVocabularyRepository.findById(id);
    }

    public Optional<WordVocabulary> findByFileName(String fileName) {
        return wordVocabularyRepository.findByFileName(fileName);
    }

    public List<WordVocabulary> findByCategory(String category) {
        return wordVocabularyRepository.findByCategory(category);
    }

    @Transactional
    public int importFilesFromDirectory() {
        File dir = new File(TRANSLATION_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            log.error("Directory not found: {}", TRANSLATION_DIR);
            return 0;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            log.warn("No files found in directory: {}", TRANSLATION_DIR);
            return 0;
        }

        int count = 0;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".csv")) {
                if (!wordVocabularyRepository.existsByFileName(file.getName())) {
                    String category = extractCategory(file.getName());
                    WordVocabulary vocabulary = new WordVocabulary(
                            file.getName(),
                            file.getAbsolutePath(),
                            file.length(),
                            category
                    );
                    wordVocabularyRepository.save(vocabulary);
                    count++;
                    log.info("Imported file: {}", file.getName());
                }
            }
        }

        log.info("Total files imported: {}", count);
        return count;
    }

    private String extractCategory(String fileName) {
        if (fileName.contains("高考")) {
            return "高考";
        } else if (fileName.contains("考研")) {
            return "考研";
        } else if (fileName.contains("四级")) {
            return "四级";
        } else if (fileName.contains("六级")) {
            return "六级";
        } else if (fileName.contains("雅思")) {
            return "雅思";
        } else if (fileName.contains("托福")) {
            return "托福";
        } else if (fileName.contains("GRE")) {
            return "GRE";
        } else if (fileName.contains("中考")) {
            return "中考";
        } else if (fileName.contains("专四")) {
            return "专四";
        } else if (fileName.contains("专八")) {
            return "专八";
        } else if (fileName.contains("初中")) {
            return "初中";
        } else if (fileName.contains("高中")) {
            return "高中";
        } else if (fileName.contains("小学")) {
            return "小学";
        } else if (fileName.contains("BEC")) {
            return "BEC";
        } else if (fileName.contains("PETS")) {
            return "PETS";
        } else if (fileName.contains("SAT")) {
            return "SAT";
        } else if (fileName.contains("GMAT")) {
            return "GMAT";
        } else if (fileName.contains("托福") || fileName.contains("TOEFL")) {
            return "托福";
        } else if (fileName.contains("IELTS")) {
            return "雅思";
        } else if (fileName.contains("MBA")) {
            return "MBA";
        } else if (fileName.contains("考博")) {
            return "考博";
        }
        return "其他";
    }

    @Transactional
    public void deleteAll() {
        wordVocabularyRepository.deleteAll();
    }
}
