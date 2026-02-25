# CSV导入功能技术实现规划

## 1. 详细技术架构

### 1.1 后端技术栈适配
基于现有Spring Boot 3.2.0 + PostgreSQL技术栈：

**新增依赖**：
```xml
<!-- CSV文件解析库 -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>
```

### 1.2 核心组件设计

#### 1.2.1 CsvImportController (控制器层)
```java
package com.example.words.controller;

import com.example.words.dto.CsvImportRequest;
import com.example.words.dto.CsvImportResponse;
import com.example.words.dto.ImportStatus;
import com.example.words.service.CsvImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/csv-import")
public class CsvImportController {
    
    private final CsvImportService csvImportService;
    
    // 文件上传接口
    @PostMapping("/upload")
    public ResponseEntity<CsvImportResponse> uploadCsv(
        @RequestParam("file") MultipartFile file,
        @RequestParam("dictionaryId") Long dictionaryId,
        @RequestParam(value = "hasHeader", defaultValue = "true") boolean hasHeader);
    
    // 查询导入状态接口
    @GetMapping("/status/{taskId}")
    public ResponseEntity<ImportStatus> getImportStatus(@PathVariable String taskId);
    
    // 获取最近导入历史
    @GetMapping("/history/{dictionaryId}")
    public ResponseEntity<List<ImportStatus>> getImportHistory(@PathVariable Long dictionaryId);
}
```

#### 1.2.2 CsvImportService (服务层)
```java
package com.example.words.service;

import com.example.words.dto.MetaWordEntryDto;
import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CsvImportService {
    
    private final DictionaryWordService dictionaryWordService;
    private final DictionaryService dictionaryService;
    
    // 导入任务状态缓存
    // 注意：实际实现应考虑添加定期清理机制，避免内存泄漏
    // 例如：定时清理24小时前的已完成或失败的任务状态
    private final ConcurrentHashMap<String, ImportStatus> importStatusMap = new ConcurrentHashMap<>();
    
    /**
     * 异步处理CSV导入
     */
    @Async
    @Transactional
    public String processCsvImport(MultipartFile file, Long dictionaryId, boolean hasHeader) {
        String taskId = UUID.randomUUID().toString();
        ImportStatus status = new ImportStatus(taskId);
        importStatusMap.put(taskId, status);
        
        try {
            status.setPhase(ImportPhase.PARSING);
            
            // 解析CSV文件
            List<MetaWordEntryDto> wordEntries = parseCsvFile(file, hasHeader);
            status.setTotalRecords(wordEntries.size());
            
            status.setPhase(ImportPhase.PROCESSING);
            
            // 调用现有服务处理单词列表
            DictionaryWordService.WordListProcessResult result = 
                dictionaryWordService.processWordList(dictionaryId, wordEntries);
            
            // 更新状态
            status.setSuccessCount(result.getCreated() + result.getExisted());
            status.setFailureCount(result.getFailed());
            status.setProcessedRecords(wordEntries.size());
            status.setPhase(ImportPhase.COMPLETED);
            status.setEndTime(LocalDateTime.now());
            
        } catch (Exception e) {
            status.setPhase(ImportPhase.FAILED);
            status.setErrorMessage(e.getMessage());
            status.setEndTime(LocalDateTime.now());
            log.error("CSV import failed for task: " + taskId, e);
        }
        
        return taskId;
    }
    
    /**
     * 解析CSV文件
     */
    private List<MetaWordEntryDto> parseCsvFile(MultipartFile file, boolean hasHeader) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream())) {
            CsvToBean<MetaWordEntryDto> csvToBean = new CsvToBeanBuilder<MetaWordEntryDto>(reader)
                .withType(MetaWordEntryDto.class)
                .withIgnoreLeadingWhiteSpace(true)
                .withSkipLines(hasHeader ? 1 : 0) // 跳过表头
                .build();
            
            return csvToBean.parse();
        }
    }
    
    /**
     * 获取导入状态
     */
    public ImportStatus getImportStatus(String taskId) {
        return importStatusMap.get(taskId);
    }
}
```

#### 1.2.3 DTO定义
```java
package com.example.words.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class CsvImportRequest {
    @NotNull(message = "辞书ID不能为空")
    private Long dictionaryId;
    
    @NotBlank(message = "文件不能为空")
    private MultipartFile file;
    
    private boolean hasHeader = true;
}

@Data
public class CsvImportResponse {
    private String taskId;
    private String message;
    private ImportStatus status;
}

@Data
public class ImportStatus {
    private String taskId;
    private ImportPhase phase;
    private int totalRecords;
    private int processedRecords;
    private int successCount;
    private int failureCount;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    public ImportStatus(String taskId) {
        this.taskId = taskId;
        this.startTime = LocalDateTime.now();
        this.phase = ImportPhase.UPLOADING;
    }
}

public enum ImportPhase {
    UPLOADING, PARSING, PROCESSING, COMPLETED, FAILED
}
```

### 1.3 前端组件设计

#### 1.3.1 CsvImportModal 组件
```typescript
// frontend/src/components/CsvImportModal.tsx
import React, { useState } from 'react';
import { csvImportApi } from '../api';
import type { Dictionary } from '../types';

interface CsvImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  dictionary: Dictionary;
  onSuccess?: () => void;
}

export function CsvImportModal({ isOpen, onClose, dictionary, onSuccess }: CsvImportModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [hasHeader, setHasHeader] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [taskId, setTaskId] = useState<string | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      // 验证文件类型和大小
      if (!selectedFile.name.endsWith('.csv')) {
        setError('请选择CSV格式的文件');
        return;
      }
      if (selectedFile.size > 10 * 1024 * 1024) { // 10MB限制
        setError('文件大小不能超过10MB');
        return;
      }
      setFile(selectedFile);
      setError(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('请选择要上传的文件');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await csvImportApi.upload(file, dictionary.id, hasHeader);
      setTaskId(response.taskId);
      
      // 开始轮询状态
      pollImportStatus(response.taskId);
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setLoading(false);
    }
  };

  const pollImportStatus = async (taskId: string) => {
    const interval = setInterval(async () => {
      try {
        const status = await csvImportApi.getStatus(taskId);
        if (status.phase === 'COMPLETED' || status.phase === 'FAILED') {
          clearInterval(interval);
          if (status.phase === 'COMPLETED' && onSuccess) {
            onSuccess();
          }
          // 显示最终结果
          showImportResult(status);
        }
      } catch (error) {
        console.error('获取导入状态失败:', error);
      }
    }, 1000); // 每秒轮询一次
  };

  const showImportResult = (status: any) => {
    const message = status.phase === 'COMPLETED' 
      ? `导入完成！成功: ${status.successCount}, 失败: ${status.failureCount}`
      : `导入失败: ${status.errorMessage}`;
    
    alert(message);
    handleClose();
  };

  const handleClose = () => {
    setFile(null);
    setError(null);
    setTaskId(null);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <h2 className="modal__title">导入CSV文件到辞书</h2>
          <button className="modal__close" onClick={handleClose}>&times;</button>
        </div>
        
        <form onSubmit={handleSubmit} className="modal__form">
          {/* 文件选择区域 */}
          <div className="form__group">
            <label className="form__label">选择CSV文件 *</label>
            <div className="file-upload-area">
              <input
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                className="file-input"
                disabled={loading}
              />
              {file && (
                <div className="file-info">
                  <span>{file.name}</span>
                  <span>({(file.size / 1024).toFixed(1)} KB)</span>
                </div>
              )}
            </div>
          </div>

          {/* 选项设置 */}
          <div className="form__group">
            <label className="form__checkbox">
              <input
                type="checkbox"
                checked={hasHeader}
                onChange={(e) => setHasHeader(e.target.checked)}
                disabled={loading}
              />
              文件包含表头行
            </label>
            <div className="form__hint">
              如果CSV文件第一行是列名，请勾选此项
            </div>
          </div>

          {/* 格式说明 */}
          <div className="form__group">
            <div className="form__hint">
              <h4>CSV格式要求：</h4>
              <ul>
                <li>编码：UTF-8</li>
                <li>分隔符：逗号(,)</li>
                <li>必需列：单词, 定义</li>
                <li>可选列：音标, 词性, 例句, 翻译, 难度</li>
                <li>示例行：apple,/ˈæpəl/,n. 苹果,名词,I ate an apple.,我吃了一个苹果,2</li>
              </ul>
            </div>
          </div>

          {error && (
            <div className="form__error">
              {error}
            </div>
          )}

          {taskId && (
            <div className="import-progress">
              <div className="progress-bar">
                <div className="progress-fill" style={{ width: '50%' }}></div>
              </div>
              <p>正在处理文件...</p>
            </div>
          )}

          <div className="modal__footer">
            <button
              type="button"
              className="btn btn--secondary"
              onClick={handleClose}
              disabled={loading}
            >
              取消
            </button>
            <button
              type="submit"
              className="btn btn--primary"
              disabled={loading || !file}
            >
              {loading ? '上传中...' : '开始导入'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

#### 1.3.2 前端API接口
```typescript
// frontend/src/api/index.ts
export const csvImportApi = {
  upload: (file: File, dictionaryId: number, hasHeader: boolean = true) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('dictionaryId', dictionaryId.toString());
    formData.append('hasHeader', hasHeader.toString());
    
    return fetchJson<CsvImportResponse>(`${API_BASE}/csv-import/upload`, {
      method: 'POST',
      body: formData,
    });
  },
  
  getStatus: (taskId: string) => 
    fetchJson<ImportStatus>(`${API_BASE}/csv-import/status/${taskId}`),
  
  getHistory: (dictionaryId: number) => 
    fetchJson<ImportStatus[]>(`${API_BASE}/csv-import/history/${dictionaryId}`)
};
```

## 2. CSV格式规范

### 2.1 标准格式定义
```
单词,音标,定义,词性,例句,翻译,难度
apple,/ˈæpəl/,n. 苹果,名词,I ate an apple.,我吃了一个苹果,2
book,/bʊk/,n. 书籍,名词,Read a book every day.,每天读一本书,2
```

### 2.2 最小格式（必需）
```
单词,定义
apple,n. 苹果，苹果树...
book,n. 书籍；卷；帐簿...
```

### 2.3 字段说明
| 列序号 | 字段名 | 是否必需 | 说明 | 示例 |
|--------|--------|----------|------|------|
| 1 | word | 是 | 单词本身 | apple |
| 2 | phonetic | 否 | 音标 | /ˈæpəl/ |
| 3 | definition | 是 | 定义说明 | n. 苹果 |
| 4 | partOfSpeech | 否 | 词性 | 名词 |
| 5 | exampleSentence | 否 | 例句 | I ate an apple. |
| 6 | translation | 否 | 翻译 | 我吃了一个苹果 |
| 7 | difficulty | 否 | 难度(1-5) | 2 |

## 3. 错误处理机制

### 3.1 文件验证错误
```java
private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new IllegalArgumentException("文件不能为空");
    }
    
    String filename = file.getOriginalFilename();
    if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
        throw new IllegalArgumentException("只支持CSV格式文件（扩展名为.csv）");
    }
    
    if (file.getSize() > 10 * 1024 * 1024) { // 10MB
        throw new IllegalArgumentException("文件大小不能超过10MB");
    }
    
    // 检查文件内容类型（可选但推荐）
    String contentType = file.getContentType();
    if (contentType != null && !contentType.startsWith("text/") && 
        !contentType.equals("application/csv") && 
        !contentType.equals("text/csv")) {
        log.warn("文件内容类型可能不是CSV: {}", contentType);
    }
}
```

### 3.2 数据验证错误
```java
private void validateCsvData(List<MetaWordEntryDto> entries) {
    for (int i = 0; i < entries.size(); i++) {
        MetaWordEntryDto entry = entries.get(i);
        StringBuilder errors = new StringBuilder();
        
        if (StringUtils.isBlank(entry.getWord())) {
            errors.append("第").append(i + 1).append("行：单词不能为空\n");
        }
        
        if (StringUtils.isBlank(entry.getDefinition())) {
            errors.append("第").append(i + 1).append("行：定义不能为空\n");
        }
        
        if (entry.getDifficulty() != null && 
            (entry.getDifficulty() < 1 || entry.getDifficulty() > 5)) {
            errors.append("第").append(i + 1).append("行：难度必须在1-5之间\n");
        }
        
        if (errors.length() > 0) {
            throw new ValidationException(errors.toString());
        }
    }
}
```

### 3.3 改进的数据验证（推荐）
```java
/**
 * 改进的CSV数据验证 - 收集所有错误后一次性报告
 */
private ValidationResult validateCsvDataWithDetails(List<MetaWordEntryDto> entries) {
    List<String> errors = new ArrayList<>();
    List<Integer> errorLines = new ArrayList<>();
    
    for (int i = 0; i < entries.size(); i++) {
        MetaWordEntryDto entry = entries.get(i);
        int lineNumber = i + 1; // CSV行号（从1开始）
        boolean hasError = false;
        
        // 单词验证
        if (StringUtils.isBlank(entry.getWord())) {
            errors.add("第" + lineNumber + "行：单词不能为空");
            errorLines.add(lineNumber);
            hasError = true;
        } else if (entry.getWord().length() > 100) {
            errors.add("第" + lineNumber + "行：单词长度不能超过100字符");
            errorLines.add(lineNumber);
            hasError = true;
        }
        
        // 定义验证
        if (StringUtils.isBlank(entry.getDefinition())) {
            errors.add("第" + lineNumber + "行：定义不能为空");
            if (!hasError) errorLines.add(lineNumber);
            hasError = true;
        }
        
        // 难度验证
        if (entry.getDifficulty() != null && 
            (entry.getDifficulty() < 1 || entry.getDifficulty() > 5)) {
            errors.add("第" + lineNumber + "行：难度必须在1-5之间");
            if (!hasError) errorLines.add(lineNumber);
            hasError = true;
        }
        
        // 音标格式验证（可选）
        if (StringUtils.isNotBlank(entry.getPhonetic()) && 
            !entry.getPhonetic().matches("^/[^/]+/$")) {
            errors.add("第" + lineNumber + "行：音标格式建议使用国际音标，如 /ˈæpəl/");
            if (!hasError) errorLines.add(lineNumber);
        }
    }
    
    return new ValidationResult(errors, errorLines);
}

/**
 * 验证结果包装类
 */
@Data
public class ValidationResult {
    private final List<String> errorMessages;
    private final List<Integer> errorLineNumbers;
    private final boolean isValid;
    
    public ValidationResult(List<String> errorMessages, List<Integer> errorLineNumbers) {
        this.errorMessages = errorMessages;
        this.errorLineNumbers = errorLineNumbers;
        this.isValid = errorMessages.isEmpty();
    }
}
```

## 4. 性能优化策略

### 4.1 批量处理
```java
private static final int BATCH_SIZE = 1000;

private void processInBatches(List<MetaWordEntryDto> entries, Long dictionaryId) {
    for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
        int endIndex = Math.min(i + BATCH_SIZE, entries.size());
        List<MetaWordEntryDto> batch = entries.subList(i, endIndex);
        
        DictionaryWordService.WordListProcessResult result = 
            dictionaryWordService.processWordList(dictionaryId, batch);
        
        // 更新进度
        updateProgress(batch.size(), result);
    }
}
```

### 4.2 内存管理
```java
// 使用流式处理减少内存占用 - 返回处理统计信息而不是所有数据
private ImportStatistics parseAndProcessCsvStream(MultipartFile file, Long dictionaryId, boolean hasHeader) throws Exception {
    ImportStatistics stats = new ImportStatistics();
    
    try (InputStream inputStream = file.getInputStream();
         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
         CSVReader csvReader = new CSVReader(reader)) {
        
        if (hasHeader) {
            csvReader.readNext(); // 跳过表头
        }
        
        List<MetaWordEntryDto> batch = new ArrayList<>(BATCH_SIZE);
        String[] line;
        int lineNumber = hasHeader ? 2 : 1; // 行号计数（用于错误报告）
        
        while ((line = csvReader.readNext()) != null) {
            try {
                if (line.length >= 2) {
                    MetaWordEntryDto dto = mapCsvLineToDto(line);
                    batch.add(dto);
                    
                    // 分批处理以控制内存使用
                    if (batch.size() >= BATCH_SIZE) {
                        processBatch(batch, dictionaryId, stats);
                        batch.clear();
                    }
                } else {
                    stats.incrementFailed();
                    log.warn("CSV第{}行：字段数量不足（至少需要2个字段）", lineNumber);
                }
            } catch (Exception e) {
                stats.incrementFailed();
                log.error("CSV第{}行处理失败：{}", lineNumber, e.getMessage());
            }
            
            lineNumber++;
            stats.incrementTotal();
        }
        
        // 处理剩余数据
        if (!batch.isEmpty()) {
            processBatch(batch, dictionaryId, stats);
        }
        
        return stats;
    }
}

/**
 * 导入统计信息类
 */
@Data
class ImportStatistics {
    private int totalRecords;
    private int successCount;
    private int failedCount;
    
    public void incrementTotal() { totalRecords++; }
    public void incrementSuccess() { successCount++; }
    public void incrementFailed() { failedCount++; }
}
```

## 5. 测试用例设计

### 5.1 单元测试
```java
@Test
void testCsvParsing() {
    // 测试正常CSV解析
    String csvContent = "word,definition\napple,n. 苹果";
    MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
    
    List<MetaWordEntryDto> result = csvImportService.parseCsvFile(file, true);
    
    assertEquals(1, result.size());
    assertEquals("apple", result.get(0).getWord());
    assertEquals("n. 苹果", result.get(0).getDefinition());
}

@Test
void testInvalidFileFormat() {
    // 测试无效文件格式
    MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "invalid".getBytes());
    
    assertThrows(IllegalArgumentException.class, () -> {
        csvImportService.validateFile(file);
    });
}
```

### 5.2 集成测试
```java
@Test
void testCompleteImportFlow() throws Exception {
    // 准备测试数据
    String csvContent = "word,phonetic,definition,difficulty\napple,/ˈæpəl/,n. 苹果,2\nbook,/bʊk/,n. 书籍,2";
    MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
    
    // 执行导入
    String taskId = csvImportService.processCsvImport(file, testDictionaryId, true);
    
    // 验证结果
    Thread.sleep(1000); // 等待异步处理完成
    
    ImportStatus status = csvImportService.getImportStatus(taskId);
    assertEquals(ImportPhase.COMPLETED, status.getPhase());
    assertEquals(2, status.getSuccessCount());
    assertEquals(0, status.getFailureCount());
}
```

## 6. 部署和配置

### 6.1 环境变量配置
```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
      
logging:
  level:
    com.example.words.service.CsvImportService: DEBUG
```

### 6.2 监控指标
```java
@Component
public class CsvImportMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordImportDuration(long durationMs) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("csv.import.duration")
            .register(meterRegistry));
    }
    
    public void recordImportSuccess(int count) {
        Counter.builder("csv.import.success")
            .register(meterRegistry)
            .increment(count);
    }
    
    public void recordImportFailure(int count) {
        Counter.builder("csv.import.failure")
            .register(meterRegistry)
            .increment(count);
    }
}
```

这个技术实现规划提供了完整的CSV导入功能开发指南，包括后端服务、前端组件、数据格式、错误处理和性能优化等方面的详细设计。