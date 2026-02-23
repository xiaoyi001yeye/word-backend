package com.example.words.service;

import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class DictionaryWordService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryWordService.class);

    private final DictionaryWordRepository dictionaryWordRepository;
    private final MetaWordRepository metaWordRepository;

    public DictionaryWordService(DictionaryWordRepository dictionaryWordRepository, MetaWordRepository metaWordRepository) {
        this.dictionaryWordRepository = dictionaryWordRepository;
        this.metaWordRepository = metaWordRepository;
    }

    public List<DictionaryWord> findByDictionaryId(Long dictionaryId) {
        return dictionaryWordRepository.findByDictionaryId(dictionaryId);
    }

    public List<DictionaryWord> findByMetaWordId(Long metaWordId) {
        return dictionaryWordRepository.findByMetaWordId(metaWordId);
    }

    public Page<MetaWord> findMetaWordsByDictionaryId(Long dictionaryId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return metaWordRepository.findByDictionaryId(dictionaryId, pageable);
    }

    public Optional<DictionaryWord> findByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId) {
        return dictionaryWordRepository.findByDictionaryIdAndMetaWordId(dictionaryId, metaWordId);
    }

    public boolean existsByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId) {
        return dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(dictionaryId, metaWordId);
    }

    @Transactional
    public DictionaryWord save(DictionaryWord dictionaryWord) {
        return dictionaryWordRepository.save(dictionaryWord);
    }

    @Transactional
    public void saveIfNotExists(Long dictionaryId, Long metaWordId) {
        if (!dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(dictionaryId, metaWordId)) {
            DictionaryWord dictionaryWord = new DictionaryWord(dictionaryId, metaWordId);
            dictionaryWordRepository.save(dictionaryWord);
            log.debug("Saved dictionary-word relation: dictionary={}, word={}", dictionaryId, metaWordId);
        }
    }

    @Transactional
    public void deleteByDictionaryId(Long dictionaryId) {
        dictionaryWordRepository.deleteByDictionaryId(dictionaryId);
    }

    @Transactional
    public void deleteAll() {
        dictionaryWordRepository.deleteAll();
    }

    @Transactional
    public void saveAllBatch(Long dictionaryId, List<Long> metaWordIds) {
        List<DictionaryWord> associations = metaWordIds.stream()
                .map(metaWordId -> new DictionaryWord(dictionaryId, metaWordId))
                .toList();
        dictionaryWordRepository.saveAll(associations);
    }

    @Transactional
    public WordListProcessResult processWordList(Long dictionaryId, List<String> words) {
        Set<String> uniqueWords = new HashSet<>(words);
        int total = uniqueWords.size();
        int existed = 0;
        int created = 0;
        int added = 0;
        int failed = 0;
        
        List<Long> metaWordIds = new ArrayList<>();
        
        for (String word : uniqueWords) {
            if (word == null || word.trim().isEmpty()) {
                failed++;
                continue;
            }
            
            String trimmedWord = word.trim();
            try {
                MetaWord metaWord = metaWordRepository.findByWord(trimmedWord)
                        .orElseGet(() -> {
                            MetaWord newMetaWord = new MetaWord();
                            newMetaWord.setWord(trimmedWord);
                            newMetaWord.setDifficulty(2);
                            return metaWordRepository.save(newMetaWord);
                        });
                
                if (metaWordRepository.findByWord(trimmedWord).isPresent()) {
                    existed++;
                } else {
                    created++;
                }
                
                metaWordIds.add(metaWord.getId());
            } catch (Exception e) {
                log.error("Failed to process word: {}", trimmedWord, e);
                failed++;
            }
        }
        
        if (!metaWordIds.isEmpty()) {
            List<DictionaryWord> existingAssociations = dictionaryWordRepository.findByDictionaryId(dictionaryId);
            Set<Long> existingMetaWordIds = existingAssociations.stream()
                    .map(DictionaryWord::getMetaWordId)
                    .collect(java.util.stream.Collectors.toSet());
            
            List<DictionaryWord> newAssociations = new ArrayList<>();
            for (Long metaWordId : metaWordIds) {
                if (!existingMetaWordIds.contains(metaWordId)) {
                    newAssociations.add(new DictionaryWord(dictionaryId, metaWordId));
                    added++;
                }
            }
            
            if (!newAssociations.isEmpty()) {
                dictionaryWordRepository.saveAll(newAssociations);
            }
        }
        
        return new WordListProcessResult(total, existed, created, added, failed);
    }
    
    public static class WordListProcessResult {
        private final int total;
        private final int existed;
        private final int created;
        private final int added;
        private final int failed;
        
        public WordListProcessResult(int total, int existed, int created, int added, int failed) {
            this.total = total;
            this.existed = existed;
            this.created = created;
            this.added = added;
            this.failed = failed;
        }
        
        public int getTotal() { return total; }
        public int getExisted() { return existed; }
        public int getCreated() { return created; }
        public int getAdded() { return added; }
        public int getFailed() { return failed; }
    }
}
