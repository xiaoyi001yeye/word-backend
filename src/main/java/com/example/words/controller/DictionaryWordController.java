package com.example.words.controller;

import com.example.words.dto.AddWordsToDictionaryRequest;
import com.example.words.dto.AddWordListRequest;
import com.example.words.dto.DictionaryWordEntryResponse;
import com.example.words.dto.GenerateDictionaryWordWithAiRequest;
import com.example.words.dto.GenerateDictionaryWordWithAiResponse;
import com.example.words.dto.MetaWordSuggestionDto;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.service.CsvImportService;
import com.example.words.service.AccessControlService;
import com.example.words.service.CurrentUserService;
import com.example.words.service.DictionaryService;
import com.example.words.service.DictionaryWordService;
import com.example.words.service.AiGenerationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionary-words")
@Validated
public class DictionaryWordController {

    private final DictionaryWordService dictionaryWordService;
    private final CsvImportService csvImportService;
    private final DictionaryService dictionaryService;
    private final CurrentUserService currentUserService;
    private final AccessControlService accessControlService;
    private final AiGenerationService aiGenerationService;

    public DictionaryWordController(DictionaryWordService dictionaryWordService,
                                  CsvImportService csvImportService,
                                  DictionaryService dictionaryService,
                                  CurrentUserService currentUserService,
                                  AccessControlService accessControlService,
                                  AiGenerationService aiGenerationService) {
        this.dictionaryWordService = dictionaryWordService;
        this.csvImportService = csvImportService;
        this.dictionaryService = dictionaryService;
        this.currentUserService = currentUserService;
        this.accessControlService = accessControlService;
        this.aiGenerationService = aiGenerationService;
    }

    @GetMapping("/dictionary/{dictionaryId}/words")
    public Page<MetaWord> getWordsByDictionary(
            @PathVariable Long dictionaryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        ensureCanViewDictionary(dictionaryId);
        return dictionaryWordService.findMetaWordsByDictionaryId(dictionaryId, page, size);
    }

    @GetMapping("/dictionary/{dictionaryId}")
    public List<DictionaryWord> getByDictionary(@PathVariable Long dictionaryId) {
        ensureCanViewDictionary(dictionaryId);
        return dictionaryWordService.findByDictionaryId(dictionaryId);
    }

    @GetMapping("/dictionary/{dictionaryId}/entries")
    public Page<DictionaryWordEntryResponse> getEntriesByDictionary(
            @PathVariable Long dictionaryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "entryOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        ensureCanViewDictionary(dictionaryId);
        return dictionaryWordService.findEntriesByDictionaryId(dictionaryId, page, size, keyword, sortBy, sortDir);
    }

    @GetMapping("/word/{metaWordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DictionaryWord> getByWord(@PathVariable Long metaWordId) {
        return dictionaryWordService.findByMetaWordId(metaWordId);
    }

    @PostMapping("/{dictionaryId}/{metaWordId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<DictionaryWord> create(
            @PathVariable Long dictionaryId,
            @PathVariable Long metaWordId) {
        ensureCanManageDictionary(dictionaryId);
        dictionaryWordService.saveIfNotExists(dictionaryId, metaWordId);
        return dictionaryWordService.findByDictionaryIdAndMetaWordId(dictionaryId, metaWordId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/dictionary/{dictionaryId}/meta-word-suggestions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<MetaWordSuggestionDto> getMetaWordSuggestions(
            @PathVariable Long dictionaryId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "8") Integer limit) {
        ensureCanManageDictionary(dictionaryId);
        return dictionaryWordService.findSuggestionsForDictionary(dictionaryId, keyword, limit);
    }

    @PostMapping("/{dictionaryId}/batch")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> addWordsToDictionary(
            @PathVariable Long dictionaryId,
            @RequestBody AddWordsToDictionaryRequest request) {
        ensureCanManageDictionary(dictionaryId);
        int addedCount = dictionaryWordService.saveAllBatch(dictionaryId, request.getMetaWordIds());
        return ResponseEntity.ok(Map.of(
                "message", "Words added to dictionary successfully",
                "dictionaryId", dictionaryId,
                "wordCount", addedCount
        ));
    }

    @PostMapping("/{dictionaryId}/words/list")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> addWordListToDictionary(
            @PathVariable Long dictionaryId,
            @Valid @RequestBody AddWordListRequest request) {
        ensureCanManageDictionary(dictionaryId);
        DictionaryWordService.WordListProcessResult result = dictionaryWordService.processWordList(dictionaryId, request.getWords());
        return ResponseEntity.ok(Map.of(
                "message", "Word list processed successfully",
                "dictionaryId", dictionaryId,
                "total", result.getTotal(),
                "existed", result.getExisted(),
                "created", result.getCreated(),
                "added", result.getAdded(),
                "failed", result.getFailed()
        ));
    }
    
    @PostMapping("/{dictionaryId}/words/list/v2")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> addWordListToDictionaryV2(
            @PathVariable Long dictionaryId,
            @Valid @RequestBody List<MetaWordEntryDtoV2> wordList) {
        ensureCanManageDictionary(dictionaryId);
        DictionaryWordService.WordListProcessResult result = dictionaryWordService.processWordListV2(dictionaryId, wordList);
        return ResponseEntity.ok(Map.of(
                "message", "Word list processed successfully (V2)",
                "dictionaryId", dictionaryId,
                "total", result.getTotal(),
                "existed", result.getExisted(),
                "created", result.getCreated(),
                "added", result.getAdded(),
                "failed", result.getFailed()
        ));
    }

    @PostMapping("/{dictionaryId}/words/ai-generate")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<GenerateDictionaryWordWithAiResponse> generateWordWithAi(
            @PathVariable Long dictionaryId,
            @Valid @RequestBody GenerateDictionaryWordWithAiRequest request) {
        ensureCanManageDictionary(dictionaryId);
        AiGenerationService.GeneratedWordEntryV2 generated = aiGenerationService.generateWordEntryV2(
                new com.example.words.dto.GenerateWordDetailsRequest(request.getConfigId(), request.getWord())
        );
        GenerateDictionaryWordWithAiResponse response = dictionaryWordService.saveGeneratedWordV2(
                dictionaryId,
                request.getMetaWordId(),
                generated.configId(),
                generated.providerName(),
                generated.modelName(),
                generated.entry()
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * CSV文件导入端点
     */
    @PostMapping("/{dictionaryId}/words/import-csv")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> importWordsFromCsv(
            @PathVariable Long dictionaryId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "hasHeader", defaultValue = "true") boolean hasHeader) {
        ensureCanManageDictionary(dictionaryId);
        
        try {
            DictionaryWordService.WordListProcessResult result = 
                csvImportService.processCsvImport(file, dictionaryId, hasHeader);
            
            return ResponseEntity.ok(Map.of(
                    "message", "CSV文件导入成功",
                    "dictionaryId", dictionaryId,
                    "fileName", file.getOriginalFilename(),
                    "total", result.getTotal(),
                    "existed", result.getExisted(),
                    "created", result.getCreated(),
                    "added", result.getAdded(),
                    "failed", result.getFailed()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "CSV文件导入失败: " + e.getMessage(),
                    "dictionaryId", dictionaryId,
                    "fileName", file.getOriginalFilename()
            ));
        }
    }
    
    /**
     * JSON数据导入端点
     */
    @PostMapping("/{dictionaryId}/words/import-json")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> importWordsFromJson(
            @PathVariable Long dictionaryId,
            @RequestBody String jsonData) {
        ensureCanManageDictionary(dictionaryId);
        
        try {
            DictionaryWordService.WordListProcessResult result = 
                csvImportService.processJsonImport(jsonData, dictionaryId);
            
            return ResponseEntity.ok(Map.of(
                    "message", "JSON数据导入成功",
                    "dictionaryId", dictionaryId,
                    "total", result.getTotal(),
                    "existed", result.getExisted(),
                    "created", result.getCreated(),
                    "added", result.getAdded(),
                    "failed", result.getFailed()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "JSON数据导入失败: " + e.getMessage(),
                    "dictionaryId", dictionaryId
            ));
        }
    }
    

    @DeleteMapping("/dictionary/{dictionaryId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> deleteByDictionary(@PathVariable Long dictionaryId) {
        ensureCanManageDictionary(dictionaryId);
        dictionaryWordService.deleteByDictionaryId(dictionaryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAll() {
        dictionaryWordService.deleteAll();
        return ResponseEntity.noContent().build();
    }

    private void ensureCanViewDictionary(Long dictionaryId) {
        AppUser actor = currentUserService.getCurrentUser();
        Dictionary dictionary = findDictionary(dictionaryId);
        accessControlService.ensureCanViewDictionary(actor, dictionary);
    }

    private void ensureCanManageDictionary(Long dictionaryId) {
        AppUser actor = currentUserService.getCurrentUser();
        Dictionary dictionary = findDictionary(dictionaryId);
        accessControlService.ensureCanManageDictionary(actor, dictionary);
    }

    private Dictionary findDictionary(Long dictionaryId) {
        return dictionaryService.findById(dictionaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionaryId));
    }
}
