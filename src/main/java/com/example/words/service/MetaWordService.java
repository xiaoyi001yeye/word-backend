package com.example.words.service;

import com.example.words.model.Dictionary;
import com.example.words.model.MetaWord;
import com.example.words.repository.MetaWordRepository;
import com.example.words.dto.MetaWordSearchRequest;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class MetaWordService {

    private static final Logger log = LoggerFactory.getLogger(MetaWordService.class);
    private static final String BOOKS_DIR = "/app/books";

    private final MetaWordRepository metaWordRepository;
    private final DictionaryService dictionaryService;
    private final DictionaryWordService dictionaryWordService;

    public MetaWordService(MetaWordRepository metaWordRepository,
                          DictionaryService dictionaryService,
                          DictionaryWordService dictionaryWordService) {
        this.metaWordRepository = metaWordRepository;
        this.dictionaryService = dictionaryService;
        this.dictionaryWordService = dictionaryWordService;
    }

    public List<MetaWord> findAll() {
        return metaWordRepository.findAll();
    }

    public Optional<MetaWord> findById(Long id) {
        return metaWordRepository.findById(id);
    }

    public Optional<MetaWord> findByWord(String word) {
        return metaWordRepository.findByWord(word);
    }

    public List<MetaWord> findByDifficulty(Integer difficulty) {
        return metaWordRepository.findByDifficulty(difficulty);
    }

    public List<MetaWord> findByWordStartingWith(String prefix) {
        return metaWordRepository.findByWordStartingWith(prefix);
    }

    @Transactional
    public MetaWord save(MetaWord metaWord) {
        return metaWordRepository.save(metaWord);
    }

    @Transactional
    public MetaWord saveIfNotExists(String word, String phonetic, String definition, String partOfSpeech) {
        Optional<MetaWord> existing = metaWordRepository.findByWord(word);
        if (existing.isPresent()) {
            return existing.get();
        }
        MetaWord metaWord = new MetaWord(word, phonetic, definition, partOfSpeech);
        return metaWordRepository.save(metaWord);
    }

    @Transactional
    public MetaWord saveWordIfNotExists(String word) {
        Optional<MetaWord> existing = metaWordRepository.findByWord(word);
        if (existing.isPresent()) {
            return existing.get();
        }
        MetaWord metaWord = new MetaWord();
        metaWord.setWord(word);
        metaWord.setDifficulty(2);
        return metaWordRepository.save(metaWord);
    }

    @Transactional
    public void deleteAll() {
        dictionaryWordService.deleteAll();
        dictionaryService.deleteAll();
        metaWordRepository.deleteAll();
    }

    @Transactional
    public int importFromBooksDirectory() {
        dictionaryWordService.deleteAll();
        dictionaryService.deleteAll();
        metaWordRepository.deleteAll();

        java.io.File dir = new java.io.File(BOOKS_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            log.error("Directory not found: {}", BOOKS_DIR);
            return 0;
        }

        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) {
            log.warn("No CSV files found in directory: {}", BOOKS_DIR);
            return 0;
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting import from {} files", files.length);

        java.util.HashMap<String, MetaWord> wordCache = new java.util.HashMap<>();
        int totalWordCount = 0;
        for (java.io.File file : files) {
            int count = importFromFile(file, wordCache);
            totalWordCount += count;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Import completed: {} words, {} dictionaries, time: {}ms ({}s)",
                totalWordCount, files.length, elapsedTime, elapsedTime / 1000);
        return totalWordCount;
    }

    @Transactional
    public int importFromFile(java.io.File file, java.util.HashMap<String, MetaWord> wordCache) {
        int wordCount = 0;

        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            List<String[]> lines = reader.readAll();

            String dictionaryName = file.getName().replace(".csv", "");
            String category = dictionaryService.extractCategory(dictionaryName);

            Dictionary dictionary = new Dictionary(
                    dictionaryName,
                    file.getAbsolutePath(),
                    file.length(),
                    category
            );
            dictionary = dictionaryService.save(dictionary);
            Long dictionaryId = dictionary.getId();

            java.util.List<Long> metaWordIds = new java.util.ArrayList<>();

            for (String[] line : lines) {
                if (line.length < 2) {
                    log.warn("Invalid line (less than 2 columns) in {}: {}", file.getName(), java.util.Arrays.toString(line));
                    continue;
                }

                String word = line[0].trim();
                String definition = line[1].trim();

                if (word.isEmpty()) {
                    log.warn("Empty word in {}: {}", file.getName(), java.util.Arrays.toString(line));
                    continue;
                }

                if (definition.isEmpty()) {
                    log.warn("Empty definition in {}: word='{}', raw line: {}", file.getName(), word, java.util.Arrays.toString(line));
                }

                MetaWord metaWord = wordCache.get(word.toLowerCase());
                if (metaWord == null) {
                    metaWord = new MetaWord();
                    metaWord.setWord(word);
                    metaWord.setDefinition(definition);
                    metaWord.setDifficulty(estimateDifficulty(category));
                    metaWord = metaWordRepository.save(metaWord);
                    wordCache.put(word.toLowerCase(), metaWord);
                }

                metaWordIds.add(metaWord.getId());
                wordCount++;
            }

            if (!metaWordIds.isEmpty()) {
                dictionaryWordService.saveAllBatch(dictionaryId, metaWordIds);
            }

            dictionaryService.updateWordCount(dictionaryId, wordCount);
            log.info("Imported {} words from {}", wordCount, file.getName());

        } catch (IOException | CsvException e) {
            log.error("Error importing file: {}", file.getName(), e);
            return 0;
        }

        return wordCount;
    }

    private int estimateDifficulty(String category) {
        return switch (category) {
            case "小学", "初中", "中考" -> 1;
            case "高中", "四级", "高考" -> 2;
            case "六级", "考研", "雅思", "托福" -> 3;
            case "GRE", "GMAT", "考博", "SAT" -> 4;
            default -> 2;
        };
    }

    public Page<MetaWord> searchByKeyword(String keyword, Pageable pageable) {
        return metaWordRepository.findByWordStartingWith(keyword, pageable);
    }

    public Page<MetaWord> searchByDictionaryIdAndKeyword(Long dictionaryId, String keyword, Pageable pageable) {
        return metaWordRepository.findByDictionaryIdAndWordStartingWith(dictionaryId, keyword, pageable);
    }

    public Page<MetaWord> search(MetaWordSearchRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 10;
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Long dictionaryId = request.getDictionaryId();
        String keyword = request.getKeyword();
        
        if (dictionaryId != null) {
            if (keyword != null && !keyword.trim().isEmpty()) {
                return searchByDictionaryIdAndKeyword(dictionaryId, keyword.trim(), pageable);
            } else {
                return metaWordRepository.findByDictionaryId(dictionaryId, pageable);
            }
        } else {
            if (keyword != null && !keyword.trim().isEmpty()) {
                return searchByKeyword(keyword.trim(), pageable);
            } else {
                return metaWordRepository.findAll(pageable);
            }
        }
    }
}
