package com.example.words.controller;

import com.example.words.model.MetaWord;
import com.example.words.dto.BooksImportJobResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.MetaWordService;
import com.example.words.service.BooksImportJobService;
import com.example.words.dto.MetaWordDetailResponse;
import com.example.words.dto.MetaWordSearchRequest;
import com.example.words.dto.MetaWordEntryDtoV2;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta-words")
public class MetaWordController {

    private final MetaWordService metaWordService;
    private final BooksImportJobService booksImportJobService;
    private final CurrentUserService currentUserService;

    public MetaWordController(
            MetaWordService metaWordService,
            BooksImportJobService booksImportJobService,
            CurrentUserService currentUserService) {
        this.metaWordService = metaWordService;
        this.booksImportJobService = booksImportJobService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<MetaWord> list() {
        return metaWordService.findAll();
    }

    @PostMapping("/search")
    public Page<MetaWord> search(@RequestBody MetaWordSearchRequest request) {
        return metaWordService.search(request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetaWord> get(@PathVariable Long id) {
        return metaWordService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<MetaWordDetailResponse> getDetail(@PathVariable Long id) {
        return metaWordService.findDetailById(id)
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MetaWord> create(@RequestBody MetaWord metaWord) {
        MetaWord saved = metaWordService.save(metaWord);
        return ResponseEntity.ok(saved);
    }
    
    @PostMapping("/v2")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MetaWord> createV2(@RequestBody MetaWordEntryDtoV2 metaWordDto) {
        // Convert DTO to entity and save
        MetaWord metaWord = new MetaWord(
            metaWordDto.getWord(),
            metaWordDto.getPhonetic() != null ? 
                new com.example.words.model.Phonetic(
                    metaWordDto.getPhonetic().getUk(),
                    metaWordDto.getPhonetic().getUs()
                ) : null,
            metaWordDto.getPartOfSpeech() != null ? 
                metaWordDto.getPartOfSpeech().stream().map(posDto -> {
                    com.example.words.model.PartOfSpeech pos = new com.example.words.model.PartOfSpeech();
                    pos.setPos(posDto.getPos());
                    // Convert other fields as needed
                    return pos;
                }).toList() : null
        );
        metaWord.setDifficulty(metaWordDto.getDifficulty());
        
        MetaWord saved = metaWordService.save(metaWord);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAll() {
        metaWordService.deleteAll();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BooksImportJobResponse> importFromBooks() {
        return ResponseEntity.accepted().body(booksImportJobService.createAndStart(currentUserService.getCurrentUser().getId()));
    }
}
