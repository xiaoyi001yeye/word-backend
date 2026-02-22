package com.example.words.service;

import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.repository.DictionaryWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DictionaryWordService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryWordService.class);

    private final DictionaryWordRepository dictionaryWordRepository;

    public DictionaryWordService(DictionaryWordRepository dictionaryWordRepository) {
        this.dictionaryWordRepository = dictionaryWordRepository;
    }

    public List<DictionaryWord> findByDictionaryId(Long dictionaryId) {
        return dictionaryWordRepository.findByDictionaryId(dictionaryId);
    }

    public List<DictionaryWord> findByMetaWordId(Long metaWordId) {
        return dictionaryWordRepository.findByMetaWordId(metaWordId);
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
}
