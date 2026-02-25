package com.example.words.service;

import com.example.words.dto.MetaWordEntryDto;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.List;

@Slf4j
@Service
public class CsvImportService {

    private final DictionaryWordService dictionaryWordService;

    public CsvImportService(DictionaryWordService dictionaryWordService) {
        this.dictionaryWordService = dictionaryWordService;
    }

    /**
     * 解析CSV文件并导入到指定词典
     * 
     * @param file CSV文件
     * @param dictionaryId 词典ID
     * @param hasHeader 是否包含表头
     * @return 处理结果
     */
    public DictionaryWordService.WordListProcessResult processCsvImport(
            MultipartFile file, Long dictionaryId, boolean hasHeader) {
        
        log.info("开始处理CSV文件导入，词典ID: {}, 文件名: {}, 大小: {} bytes, 包含表头: {}", 
                dictionaryId, file.getOriginalFilename(), file.getSize(), hasHeader);
        
        try {
            // 验证文件
            validateFile(file);
            
            // 解析CSV文件
            List<MetaWordEntryDto> wordEntries = parseCsvFile(file, hasHeader);
            log.info("CSV文件解析完成，共 {} 条记录", wordEntries.size());
            
            // 验证数据
            ValidationResult validation = validateCsvData(wordEntries);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("CSV数据验证失败:\n" + 
                    String.join("\n", validation.getErrorMessages()));
            }
            
            // 调用现有服务处理单词列表
            DictionaryWordService.WordListProcessResult result = 
                dictionaryWordService.processWordList(dictionaryId, wordEntries);
            
            log.info("CSV导入完成: 总计={}, 已存在={}, 新建={}, 添加={}, 失败={}",
                    result.getTotal(), result.getExisted(), result.getCreated(), 
                    result.getAdded(), result.getFailed());
            
            return result;
            
        } catch (Exception e) {
            log.error("CSV导入失败，词典ID: {}, 文件名: {}", dictionaryId, file.getOriginalFilename(), e);
            throw new RuntimeException("CSV导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证上传的文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("只支持CSV格式文件（扩展名为.csv）");
        }
        
        if (file.getSize() > 10 * 1024 * 1024) { // 10MB限制
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }
        
        // 检查文件内容类型
        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("text/") && 
            !contentType.equals("application/csv") && 
            !contentType.equals("text/csv")) {
            log.warn("文件内容类型可能不是CSV: {}", contentType);
        }
    }
    
    /**
     * 解析CSV文件
     */
    private List<MetaWordEntryDto> parseCsvFile(MultipartFile file, boolean hasHeader) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream())) {
            CsvToBeanBuilder<MetaWordEntryDto> builder = new CsvToBeanBuilder<MetaWordEntryDto>(reader)
                .withType(MetaWordEntryDto.class)
                .withIgnoreLeadingWhiteSpace(true);
            
            // 设置跳过行数
            if (hasHeader) {
                builder.withSkipLines(1);
            }
            
            return builder.build().parse();
        }
    }
    
    /**
     * 验证CSV数据
     */
    private ValidationResult validateCsvData(List<MetaWordEntryDto> entries) {
        ValidationResult result = new ValidationResult();
        
        for (int i = 0; i < entries.size(); i++) {
            MetaWordEntryDto entry = entries.get(i);
            int lineNumber = i + 1; // 行号（从1开始）
            
            // 验证必需字段
            if (entry.getWord() == null || entry.getWord().trim().isEmpty()) {
                result.addError("第" + lineNumber + "行：单词不能为空");
                continue; // 跳过其他验证
            }
            
            if (entry.getWord().length() > 100) {
                result.addError("第" + lineNumber + "行：单词长度不能超过100字符");
            }
            
            if (entry.getDefinition() == null || entry.getDefinition().trim().isEmpty()) {
                result.addError("第" + lineNumber + "行：定义不能为空");
            }
            
            // 验证可选字段
            if (entry.getPhonetic() != null && entry.getPhonetic().length() > 200) {
                result.addError("第" + lineNumber + "行：音标长度不能超过200字符");
            }
            
            if (entry.getPartOfSpeech() != null && entry.getPartOfSpeech().length() > 50) {
                result.addError("第" + lineNumber + "行：词性长度不能超过50字符");
            }
            
            if (entry.getExampleSentence() != null && entry.getExampleSentence().length() > 1000) {
                result.addError("第" + lineNumber + "行：例句长度不能超过1000字符");
            }
            
            if (entry.getTranslation() != null && entry.getTranslation().length() > 500) {
                result.addError("第" + lineNumber + "行：翻译长度不能超过500字符");
            }
            
            if (entry.getDifficulty() != null && 
                (entry.getDifficulty() < 1 || entry.getDifficulty() > 5)) {
                result.addError("第" + lineNumber + "行：难度必须在1-5之间");
            }
        }
        
        return result;
    }
    
    /**
     * 验证结果包装类
     */
    public static class ValidationResult {
        private final java.util.List<String> errorMessages = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errorMessages.add(error);
        }
        
        public boolean isValid() {
            return errorMessages.isEmpty();
        }
        
        public java.util.List<String> getErrorMessages() {
            return new java.util.ArrayList<>(errorMessages);
        }
        
        public String getErrorMessageString() {
            return String.join("\n", errorMessages);
        }
    }
}