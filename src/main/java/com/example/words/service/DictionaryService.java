package com.example.words.service;

import com.example.words.model.Dictionary;
import com.example.words.repository.DictionaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DictionaryService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryService.class);
    private static final String TRANSLATION_DIR = "/opt/translation";

    private final DictionaryRepository dictionaryRepository;

    public DictionaryService(DictionaryRepository dictionaryRepository) {
        this.dictionaryRepository = dictionaryRepository;
    }

    public List<Dictionary> findAll() {
        return dictionaryRepository.findAll();
    }

    public Optional<Dictionary> findById(Long id) {
        return dictionaryRepository.findById(id);
    }

    public Optional<Dictionary> findByName(String name) {
        return dictionaryRepository.findByName(name);
    }

    public List<Dictionary> findByCategory(String category) {
        return dictionaryRepository.findByCategory(category);
    }

    @Transactional
    public Dictionary save(Dictionary dictionary) {
        return dictionaryRepository.save(dictionary);
    }

    @Transactional
    public int importFromDirectory() {
        java.io.File dir = new java.io.File(TRANSLATION_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            log.error("Directory not found: {}", TRANSLATION_DIR);
            return 0;
        }

        java.io.File[] files = dir.listFiles();
        if (files == null) {
            log.warn("No files found in directory: {}", TRANSLATION_DIR);
            return 0;
        }

        int count = 0;
        for (java.io.File file : files) {
            if (file.isFile() && file.getName().endsWith(".csv")) {
                if (!dictionaryRepository.existsByName(file.getName())) {
                    String category = extractCategory(file.getName());
                    Dictionary dictionary = new Dictionary(
                            file.getName(),
                            file.getAbsolutePath(),
                            file.length(),
                            category
                    );
                    dictionaryRepository.save(dictionary);
                    count++;
                    log.info("Imported dictionary: {}", file.getName());
                }
            }
        }

        log.info("Total dictionaries imported: {}", count);
        return count;
    }

    @Transactional
    public void updateWordCount(Long dictionaryId, int wordCount) {
        dictionaryRepository.findById(dictionaryId).ifPresent(dictionary -> {
            dictionary.setWordCount(wordCount);
            dictionaryRepository.save(dictionary);
        });
    }

    @Transactional
    public void deleteAll() {
        dictionaryRepository.deleteAll();
    }

    public String extractCategory(String fileName) {
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
        } else if (fileName.contains("托福") || fileName.contains("TOEFL")) {
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
        } else if (fileName.contains("IELTS")) {
            return "雅思";
        } else if (fileName.contains("MBA")) {
            return "MBA";
        } else if (fileName.contains("考博")) {
            return "考博";
        }
        return "其他";
    }
}
