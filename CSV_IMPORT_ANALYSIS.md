# CSV文件导入功能分析文档

## 1. 项目现状分析

### 1.1 现有功能概述
当前系统已经具备：
- **辞书管理**：支持创建、查询、删除辞书
- **单词管理**：支持单词的增删查改
- **批量导入**：支持JSON格式的批量单词导入
- **文件存储**：系统内置books目录用于存储辞书文件

### 1.2 现有导入机制
```java
// 当前后端导入流程
1. 用户通过AddWordListModal组件提交JSON格式单词列表
2. 前端调用 dictionaryWordApi.addWordList(dictionaryId, words)
3. 后端DictionaryWordController.processWordList方法处理
4. 系统根据单词是否存在决定创建新词或更新现有单词
5. 建立辞书与单词的关联关系
```

### 1.3 CSV文件格式分析
通过分析books目录下的CSV文件，发现格式为：
```
单词,定义
格式示例：
apple,n. 苹果，苹果树，苹果似的东西；炸弹，手榴弹...
book,"n. 书籍；卷；帐簿；名册；工作簿
vt. 预订；登记"
```

特点：
- 第一列为单词（word）
- 第二列为定义（definition）
- 定义可能包含换行符，需要用双引号包围
- 使用逗号分隔

## 2. 功能需求规格

### 2.1 核心功能需求
1. **文件上传**：用户可以通过前端界面上传CSV文件
2. **格式解析**：系统能够正确解析CSV文件内容
3. **数据验证**：对上传的数据进行有效性验证
4. **批量处理**：支持大批量单词的高效导入
5. **进度反馈**：提供导入进度和结果反馈
6. **错误处理**：妥善处理导入过程中的各种异常情况

### 2.2 用户体验需求
1. **界面友好**：提供清晰的操作指引和状态显示
2. **实时反馈**：显示上传进度、处理进度
3. **结果展示**：详细展示导入统计信息（成功、失败、重复等）
4. **错误提示**：明确指出格式错误或数据问题

## 3. 技术方案设计

### 3.1 后端架构设计

#### 3.1.1 控制器层
```java
@RestController
@RequestMapping("/api/csv-import")
public class CsvImportController {
    
    @PostMapping("/upload")
    public ResponseEntity<CsvImportResponse> uploadCsv(
        @RequestParam("file") MultipartFile file,
        @RequestParam("dictionaryId") Long dictionaryId);
    
    @GetMapping("/status/{taskId}")
    public ResponseEntity<ImportStatus> getImportStatus(@PathVariable String taskId);
}
```

#### 3.1.2 服务层
```java
@Service
public class CsvImportService {
    
    @Async
    public String processCsvImport(MultipartFile file, Long dictionaryId);
    
    public ImportStatus getImportStatus(String taskId);
    
    private List<MetaWordEntryDto> parseCsvFile(MultipartFile file);
    
    private void validateCsvData(List<MetaWordEntryDto> entries);
}
```

#### 3.1.3 数据传输对象
```java
@Data
public class CsvImportResponse {
    private String taskId;
    private String message;
    private ImportStatus status;
}

@Data
public class ImportStatus {
    private String taskId;
    private ImportPhase phase; // UPLOADING, PARSING, PROCESSING, COMPLETED, FAILED
    private int totalRecords;
    private int processedRecords;
    private int successCount;
    private int failureCount;
    private List<String> errors;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

public enum ImportPhase {
    UPLOADING, PARSING, PROCESSING, COMPLETED, FAILED
}
```

### 3.2 前端架构设计

#### 3.2.1 组件设计
```typescript
// CSV导入模态框组件
interface CsvImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  dictionary: Dictionary;
  onSuccess?: () => void;
}

// 导入状态组件
interface ImportProgressProps {
  taskId: string;
  onCompleted: () => void;
}
```

#### 3.2.2 API接口
```typescript
export const csvImportApi = {
  upload: (file: File, dictionaryId: number) => 
    fetchJson<CsvImportResponse>(`${API_BASE}/csv-import/upload`, {
      method: 'POST',
      body: createFormData({ file, dictionaryId })
    }),
  
  getStatus: (taskId: string) => 
    fetchJson<ImportStatus>(`${API_BASE}/csv-import/status/${taskId}`)
};
```

### 3.3 数据映射规则

#### 3.3.1 CSV到DTO映射
```
CSV列 -> MetaWordEntryDto字段映射：
Column 1 (单词) -> word (必需)
Column 2 (定义) -> definition (必需)
Column 3 (音标) -> phonetic (可选)
Column 4 (词性) -> partOfSpeech (可选)
Column 5 (例句) -> exampleSentence (可选)
Column 6 (翻译) -> translation (可选)
Column 7 (难度) -> difficulty (可选，默认2)
```

#### 3.3.2 默认值设置
- difficulty: 默认值为2（中等难度）
- partOfSpeech: 根据辞书分类自动估算，也可从定义中提取（如定义以"n."开头则设置为"名词"）
- phonetic: 空字符串
- exampleSentence: 空字符串  
- translation: 空字符串
- 注意：实际实现应考虑更智能的词性推断逻辑

## 4. 实现步骤规划

### 4.1 第一阶段：后端基础功能（预计2天）
1. 创建CsvImportController控制器
2. 实现文件上传和基本解析功能
3. 创建CsvImportService服务类
4. 实现CSV解析和数据验证逻辑
5. 添加异步处理支持

### 4.2 第二阶段：导入处理优化（预计2天）
1. 集成现有DictionaryWordService的处理逻辑
2. 实现批量处理和事务管理
3. 添加导入状态跟踪机制
4. 实现错误处理和日志记录

### 4.3 第三阶段：前端界面开发（预计2天）
1. 创建CSV导入模态框组件
2. 实现文件选择和上传功能
3. 开发导入进度显示组件
4. 集成API调用和状态管理

### 4.4 第四阶段：测试和完善（预计1天）
1. 功能测试和边界情况测试
2. 性能测试（大文件导入）
3. 用户体验优化
4. 文档完善

## 5. 技术要点和注意事项

### 5.1 文件处理
- 限制文件大小（建议不超过10MB）
- 支持的编码格式：UTF-8
- 文件格式验证（.csv扩展名）

### 5.2 性能优化
- 分批处理大量数据（每批次1000条记录）
- 使用异步处理避免阻塞主线程
- 实现进度跟踪和状态缓存

### 5.3 错误处理
- 文件格式错误的详细提示
- 数据验证失败的具体原因
- 系统异常的优雅降级

### 5.4 安全考虑
- 文件类型验证
- 内容安全检查
- 权限控制（仅允许用户创建的辞书导入）

## 6. 风险评估和应对策略

### 6.1 主要风险
1. **大数据量处理**：可能导致内存溢出或响应超时
2. **并发导入**：多个用户同时导入可能影响性能
3. **数据一致性**：导入过程中断可能导致数据不一致

### 6.2 应对策略
1. 实施分页处理和内存监控
2. 添加导入队列和并发控制
3. 使用数据库事务确保原子性操作

## 7. 验收标准

### 7.1 功能验收
- [ ] 支持CSV文件上传
- [ ] 正确解析CSV格式数据
- [ ] 提供详细的导入结果统计
- [ ] 支持导入进度实时查看
- [ ] 妥善处理各种错误情况

### 7.2 性能验收
- [ ] 支持至少10,000条记录的导入
- [ ] 单次导入时间不超过5分钟
- [ ] 内存使用合理，不会导致OOM

### 7.3 用户体验验收
- [ ] 界面操作直观易懂
- [ ] 提供清晰的状态反馈
- [ ] 错误信息准确有用
- [ ] 响应速度快，无明显卡顿

## 8. 后续扩展建议

1. **支持更多格式**：Excel、JSON等格式导入
2. **模板下载**：提供标准CSV模板下载
3. **导入历史**：记录用户的导入历史和统计
4. **批量操作**：支持多文件批量导入
5. **数据预览**：导入前预览解析结果

---
*文档版本：1.0*
*最后更新：2024年*