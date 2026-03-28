# books 导入性能优化设计

## 1. 背景

当前 `books` 导入入口是 [`DictionaryController`](../src/main/java/com/example/words/controller/DictionaryController.java)
的 `POST /api/dictionaries/import`，它会同步调用 [`MetaWordService#importBooksData`](../src/main/java/com/example/words/service/MetaWordService.java)。

结合当前代码实现和仓库数据规模，现状已经不适合继续沿用“同步接口 + JPA 逐词查重”的模型：

1. `books/` 目录当前大约有 `1134` 个文件。
2. 总体积约 `176 MB`。
3. 总行数约 `4,795,038`。
4. 导入过程中还会涉及 `dictionaries`、`meta_words`、`dictionary_words`、默认章节 `tags`、计数更新等多张表。

这类全量导入的主要矛盾已经不是“batch 调大一点”，而是导入模型本身需要升级。

## 2. 当前实现分析

### 2.1 当前主链路

当前导入主路径在 [`MetaWordService`](../src/main/java/com/example/words/service/MetaWordService.java)：

1. `importBooksData()`
   - 先 `resetImportData()`，直接删除所有 `dictionary_words`、`dictionaries`、`meta_words`
   - 扫描 `/app/books`
   - 按文件名排序
   - 单线程逐个调用 `importFromFile`

2. `importFromCsvFile() / importFromJsonFile()`
   - 流式读取文件
   - 每 `500` 行组成一个 batch
   - 调用 `persistCsvBatch() / persistJsonBatch()`

3. `persistCsvBatch() / persistJsonBatch()`
   - 对 batch 中每一行逐条调用 `resolveCsvMetaWordId() / resolveJsonMetaWordId()`
   - 每个单词都可能触发：
     - `metaWordRepository.findByWord(...)`
     - `metaWordRepository.save(...)`
   - 然后调用 `dictionaryWordService.saveAllBatchIgnoringDuplicates(...)`

4. `DictionaryWordService#saveAllBatchIgnoringDuplicates()`
   - 查询默认章节 tag
   - 查询当前最大 `entry_order`
   - `saveAll(entries)`
   - `refreshDictionaryCounts()`，再次触发聚合统计

### 2.2 主要瓶颈

#### 瓶颈 A：HTTP 请求绑定整个导入生命周期

- 接口同步执行，客户端必须一直等待导入完成
- 前端无法展示进度
- 没有失败重试、取消、恢复、明细追踪
- 超时和 500 很难区分“系统忙”“卡住”“部分失败”

#### 瓶颈 B：热路径逐词查库

当前 CSV/JSON 导入都会在热路径里反复执行 `findByWord`。  
对于 480 万行级别的数据，这会制造海量 SQL 往返。

即使有 `wordCache`，也仍然存在几个问题：

- 只对单 JVM 进程有效
- 容量只有 `10_000`
- 无法覆盖跨文件的大量重复词
- 无法消除首次命中的查库成本

#### 瓶颈 C：JPA 不适合导入热点

当前热点依赖：

- `JpaRepository#save`
- `JpaRepository#saveAll`
- 每 batch 一个事务
- 每 batch 更新计数

JPA 适合常规业务写入，不适合百万级导入热路径。  
这里更适合：

- `JdbcTemplate.batchUpdate`
- PostgreSQL `COPY`
- `INSERT ... SELECT`
- `INSERT ... ON CONFLICT`

#### 瓶颈 D：计数更新太频繁

当前几乎每个 batch 都会更新一次 dictionary 统计。  
这类聚合应该挪到：

- 单本 dictionary 发布时统一更新
- 或整个发布阶段统一更新

#### 瓶颈 E：文件级并行度为 1

当前是串行逐文件导入。  
在 1134 个文件、176MB 数据量下，CPU 解析和数据库写入都没有被充分利用。

#### 瓶颈 F：导入开始即删空旧数据

`resetImportData()` 会在导入前直接清空现有导入数据：

- 导入一半失败，系统处于空数据状态
- 用户导入过程中看到的数据不完整
- 没有预览、审核、发布阶段

#### 瓶颈 G：导入、合并、发布混在一起

当前实现默认把以下事情塞进同一条链路：

1. 读文件
2. 标准化
3. 去重
4. 更新元单词
5. 更新 dictionary 词条
6. 更新计数
7. 直接让新数据对外生效

这会导致任何一个环节慢或失败，都会拖垮整个导入体验。

## 3. 设计目标

### 3.1 产品与交互目标

1. 提交导入请求后 `1s` 内返回导入批次 ID。
2. 用户可以看到批次状态、进度、文件级错误、冲突数量、发布状态。
3. 导入结束后不必立即生效，支持“先导入、后合并、再发布”。
4. 支持通过页面人工处理少量冲突词，而不是人工处理全量数据。
5. 支持管理员手动触发：
   - 自动合并
   - 冲突复核
   - 发布
   - 废弃批次

### 3.2 性能目标

以下是建议目标，不是当前实测结果：

1. 总导入耗时较现状下降 `70%` 以上。
2. 导入热路径 SQL 往返次数下降 `90%` 以上。
3. 导入热路径完全避免逐词 `findByWord`。
4. 导入阶段达到“万行/秒级”吞吐。
5. 将“是否更新正式词库”的开销从导入热路径中剥离出去。

### 3.3 数据一致性目标

1. 导入过程中旧数据持续可读。
2. 发布前的脏数据、冲突数据不能直接污染正式词库。
3. 现有依赖 `dictionary_id` 的业务关系必须稳定，不因重新导入而失效。
4. 支持失败重跑、人工审核、重复发布保护。

## 4. 核心设计原则

### 4.1 异步导入的核心不是 merge 本身

异步导入要解决的是三类问题：

1. 任务编排
   - 长任务从 HTTP 中拆出去
   - 进度、状态、错误可观测

2. 数据合并
   - 相同单词怎么去重、怎么合并、怎么减少 SQL

3. 数据生效
   - 什么时候发布
   - 发布失败如何处理
   - 发布时如何保证旧数据仍可读

因此，“异步”不等于“相同单词 merge”。  
对于这个项目，更准确的模型是：

`STAGE -> AUTO_MERGE -> MANUAL_REVIEW -> PUBLISH`

### 4.2 导入热路径只做追加写入

真正决定导入吞吐的阶段，应该只负责：

1. 读取文件
2. 标准化
3. 写入 staging

不应该在热路径里做：

- 元单词逐条查库
- 正式表更新
- 计数刷新
- 正式版本切换

### 4.3 自动 merge 处理大多数数据，页面只处理少数冲突

页面可以成为 merge 的入口和审核台，但不能成为主处理引擎。

推荐策略：

1. 自动规则优先处理大部分词
2. 只有真正冲突的词进入人工处理页
3. 发布前要求冲突清零或显式忽略

### 4.4 正式业务引用必须稳定

当前系统里有多处直接依赖 `dictionary_id`：

- `dictionary_assignments`
- `study_plans`
- `exams`
- 以及多个读取接口

所以不能在每次导入时简单地“新建一批 dictionary 行，再切换 active”。  
更稳妥的方案是：

1. 对已存在的 imported dictionary 复用原有 `dictionary_id`
2. 导入和 merge 都在 staging 进行
3. 发布时再把同一 `dictionary_id` 的内容替换掉

这样现有 assignment、study plan、exam 都不会失效。

### 4.5 单词唯一性要建立在规范化键上

当前 `meta_words.word` 的唯一约束是基于原始 `word`，不能彻底解决大小写或空白差异。  
推荐引入统一规范化字段：

- `normalized_word`

规范化规则建议：

1. `trim`
2. `toLowerCase(Locale.ROOT)`
3. 视业务决定是否压缩多余空白

正式唯一约束应落在 `normalized_word` 上，而不是显示字段 `word` 上。

## 5. 总体方案

### 5.1 推荐导入流水线

### Phase A：异步导入到 staging

提交导入批次后，后台只做：

1. 扫描 `books/`
2. 并行解析 CSV/JSON
3. 用 `COPY` 写入 `book_import_stage`
4. 记录文件级统计和错误

此阶段结束后，状态为：

- `STAGED`

这一步不更新正式 `meta_words` 和 `dictionary_words`。

### Phase B：自动 merge

管理员在页面点击“自动合并”后，后台执行：

1. 按 `normalized_word` 聚合 staging 数据
2. 生成候选元单词
3. 与正式 `meta_words` 对比
4. 自动判定：
   - 可直接新增
   - 可直接补全字段
   - 存在冲突，需要人工处理

自动 merge 完成后，批次状态为：

- 无冲突：`READY_TO_PUBLISH`
- 有冲突：`WAITING_REVIEW`

### Phase C：人工审核

管理员在页面处理冲突项：

1. 保留正式库现有值
2. 使用导入值覆盖
3. 手工编辑最终值
4. 忽略本次导入候选

所有冲突处理完成后，状态进入：

- `READY_TO_PUBLISH`

### Phase D：发布

管理员点击“发布”后，后台执行：

1. 将已确认的候选批量应用到 `meta_words`
2. 按 dictionary 替换正式 `dictionary_words`
3. 更新 dictionary 计数
4. 记录发布日志

发布完成后，状态为：

- `SUCCEEDED`

### Phase E：清理

异步清理：

1. `book_import_stage`
2. 已完成批次的中间候选
3. 过期冲突记录
4. 历史日志按保留期归档

### 5.2 推荐状态机

建议批次状态：

- `PENDING`
- `SCANNING`
- `STAGING`
- `STAGED`
- `AUTO_MERGING`
- `WAITING_REVIEW`
- `READY_TO_PUBLISH`
- `PUBLISHING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`
- `DISCARDED`

### 5.3 推荐工作模式

### 模式 A：先导入，后人工触发 merge

适合第一阶段上线：

1. 导入只负责写 staging
2. 页面人工点击“自动合并”
3. 冲突人工审核
4. 页面点击“发布”

优点：

- 实现路径清晰
- 热路径性能收益最大
- 发布时机由管理员控制

### 模式 B：夜间自动导入 + 自动合并

适合后续增强：

1. 定时发起导入
2. 自动执行 auto-merge
3. 若无冲突则自动发布
4. 若有冲突则停在待审核状态

## 6. 页面与交互设计

### 6.1 导入管理页

页面展示：

- 批次 ID
- 创建时间
- 状态
- 总文件数
- 已处理文件数
- 总行数
- 成功/失败行数
- 候选元单词数
- 冲突数
- 发布时间

页面动作：

- 创建导入批次
- 查看详情
- 开始自动合并
- 查看冲突
- 发布
- 废弃批次

### 6.2 批次详情页

展示：

- 当前阶段
- 当前文件
- 文件级耗时
- 文件级错误
- 导入样例预览
- dictionary 级统计
- 候选变化摘要

变化摘要建议包括：

- 新增 meta word 数
- 更新 meta word 数
- 待审核冲突数
- 受影响 dictionary 数

### 6.3 冲突处理页

冲突页展示的不是全部词，而是少量“自动规则无法确定”的词。

冲突类型建议：

- `FIELD_CONFLICT`
  - 正式库和导入值都非空，且内容不一致

- `MULTI_SOURCE_CONFLICT`
  - 同一批次内不同来源文件给出了不一致的定义或音标

- `NORMALIZATION_COLLISION`
  - 不同原始词映射到同一个 `normalized_word`

- `INVALID_DATA`
  - 数据结构合法，但字段质量不满足发布要求

页面动作建议：

- 保留现有正式值
- 使用导入值
- 手工编辑最终值
- 忽略当前候选
- 查看来源文件和原始样本

### 6.4 元单词维护页

建议补一个“元单词维护”页面，但它不是导入主流程，而是运维工具：

适用场景：

- 修正历史脏数据
- 统一某类词的显示形式
- 人工修复音标、词性等详情

这个页面可以复用冲突处理组件，但不要要求人工在这里处理全量导入数据。

### 6.5 发布确认页

发布前展示：

- 本次将新增多少 meta words
- 将更新多少 meta words
- 将覆盖多少 dictionaries 的词条
- 是否仍有未处理冲突
- 是否存在失败文件

严格模式下：

- 有未处理冲突则禁止发布

宽松模式下：

- 允许发布，但必须显式勾选“忽略未处理项”

推荐默认使用严格模式。

## 7. 接口设计

### 7.1 创建导入批次

`POST /api/books-import/batches`

返回：

```json
{
  "batchId": "9c80e2c2-18c2-4d90-92aa-d6d2b512d1d8",
  "status": "PENDING",
  "message": "Books import batch accepted"
}
```

建议返回 `202 Accepted`。

### 7.2 查询批次状态

`GET /api/books-import/batches/{batchId}`

### 7.3 查询文件明细

`GET /api/books-import/batches/{batchId}/files`

### 7.4 触发自动 merge

`POST /api/books-import/batches/{batchId}/auto-merge`

### 7.5 查询冲突

`GET /api/books-import/batches/{batchId}/conflicts`

支持筛选：

- 冲突类型
- dictionary 名称
- 是否已处理

### 7.6 处理冲突

`POST /api/books-import/batches/{batchId}/conflicts/{conflictId}/resolve`

请求示例：

```json
{
  "resolution": "USE_IMPORTED",
  "finalWord": "abandon",
  "finalDefinition": "放弃；遗弃",
  "finalDifficulty": 3,
  "comment": "Use imported definition from latest source"
}
```

### 7.7 发布

`POST /api/books-import/batches/{batchId}/publish`

### 7.8 废弃批次

`POST /api/books-import/batches/{batchId}/discard`

## 8. 数据模型设计

### 8.1 导入批次表

建议新增：

#### `import_batches`

字段示例：

- `id UUID`
- `batch_type VARCHAR(32)`，如 `BOOKS_FULL`
- `status VARCHAR(32)`
- `total_files INT`
- `processed_files INT`
- `total_rows BIGINT`
- `processed_rows BIGINT`
- `success_rows BIGINT`
- `failed_rows BIGINT`
- `current_file VARCHAR(500)`
- `candidate_count BIGINT`
- `conflict_count BIGINT`
- `publish_started_at TIMESTAMP`
- `publish_finished_at TIMESTAMP`
- `error_message TEXT`
- `created_by BIGINT`
- `created_at TIMESTAMP`
- `updated_at TIMESTAMP`

#### `import_batch_files`

字段示例：

- `id BIGSERIAL`
- `batch_id UUID`
- `file_name VARCHAR(500)`
- `dictionary_name VARCHAR(500)`
- `status VARCHAR(32)`
- `row_count BIGINT`
- `success_rows BIGINT`
- `failed_rows BIGINT`
- `duration_ms BIGINT`
- `error_message TEXT`

### 8.2 staging 表

建议新增一张 `UNLOGGED` 暂存表：

#### `book_import_stage`

字段示例：

- `batch_id UUID`
- `file_name VARCHAR(500)`
- `dictionary_name VARCHAR(500)`
- `category VARCHAR(100)`
- `source_row_no BIGINT`
- `entry_order INT`
- `word TEXT`
- `normalized_word TEXT`
- `definition TEXT`
- `difficulty INT`
- `phonetic_detail JSONB`
- `part_of_speech_detail JSONB`
- `raw_payload JSONB`

索引建议：

```sql
CREATE INDEX idx_book_import_stage_batch_word
    ON book_import_stage (batch_id, normalized_word);

CREATE INDEX idx_book_import_stage_batch_dictionary
    ON book_import_stage (batch_id, dictionary_name);
```

### 8.3 候选表

#### `import_meta_word_candidates`

用于保存自动 merge 生成的候选结果。

字段示例：

- `id BIGSERIAL`
- `batch_id UUID`
- `normalized_word TEXT`
- `display_word TEXT`
- `definition TEXT`
- `difficulty INT`
- `phonetic_detail JSONB`
- `part_of_speech_detail JSONB`
- `source_count INT`
- `merge_status VARCHAR(32)`
- `matched_meta_word_id BIGINT`
- `resolution_source VARCHAR(32)`，如 `AUTO`、`MANUAL`
- `created_at TIMESTAMP`
- `updated_at TIMESTAMP`

### 8.4 冲突表

#### `import_conflicts`

字段示例：

- `id BIGSERIAL`
- `batch_id UUID`
- `candidate_id BIGINT`
- `conflict_type VARCHAR(32)`
- `dictionary_names TEXT`
- `existing_meta_word_id BIGINT`
- `existing_payload JSONB`
- `imported_payload JSONB`
- `resolution VARCHAR(32)`
- `resolved_payload JSONB`
- `resolved_by BIGINT`
- `resolved_at TIMESTAMP`
- `comment TEXT`

### 8.5 发布日志表

#### `import_publish_logs`

字段示例：

- `id BIGSERIAL`
- `batch_id UUID`
- `dictionary_id BIGINT`
- `dictionary_name VARCHAR(500)`
- `published_entry_count BIGINT`
- `published_word_count BIGINT`
- `status VARCHAR(32)`
- `error_message TEXT`
- `started_at TIMESTAMP`
- `finished_at TIMESTAMP`

### 8.6 `meta_words` 正式唯一键

建议给 `meta_words` 增加：

- `normalized_word TEXT NOT NULL`

并把唯一约束从 `word` 迁移到 `normalized_word`：

```sql
CREATE UNIQUE INDEX uk_meta_words_normalized_word
    ON meta_words (normalized_word);
```

说明：

- `word` 用于展示
- `normalized_word` 用于去重、merge、join

如果历史数据已经存在大小写冲突，需要先做一次数据清洗迁移。

## 9. 正式 dictionary 引用模型

### 9.1 保持 `dictionary_id` 稳定

推荐规则：

1. imported dictionary 仍然保存在 `dictionaries` 表
2. 同名 imported dictionary 复用同一条 `dictionary` 记录
3. 不在每次导入时重新创建一套新 dictionary 行

这样可以保证以下关系不失效：

- `dictionary_assignments`
- `study_plans`
- `exams`
- 基于 `dictionary_id` 的查询与权限逻辑

### 9.2 为什么不再推荐 active 切换字典行

如果每次导入都重新创建 dictionary 行，再切换 `active`：

- 旧的 assignment 会指向旧 ID
- 旧的 study plan 会指向旧 ID
- 旧的 exam/history 会指向旧 ID

这会引入额外的数据迁移和兼容成本。

### 9.3 发布时如何保持旧数据可读

发布时对单本 dictionary 使用单事务替换：

1. 查到稳定的 `dictionary_id`
2. 在同一事务中删除旧 `dictionary_words`
3. 插入新 `dictionary_words`
4. 更新 `word_count` / `entry_count`
5. 提交事务

PostgreSQL 的 MVCC 能保证：

- 提交前，读者看到旧的已提交数据
- 提交后，读者看到新的已提交数据

不会出现“半删半插”的中间可见状态。

## 10. 执行流程设计

### 10.1 创建批次并扫描文件

1. 创建 `import_batches`
2. 获取全局导入锁，避免多个全量导入互相踩踏
3. 扫描 `books/`
4. 记录总文件数和预估行数

并发控制建议使用 PostgreSQL advisory lock：

```sql
SELECT pg_try_advisory_lock(2026032801);
```

### 10.2 并行解析并写入 staging

推荐使用有界线程池：

- 文件解析线程数：`min(4, CPU 核数)`
- `COPY` writer 线程数：`1 ~ 2`

处理流程：

1. 解析 CSV/JSON
2. 规范化 `normalized_word`
3. 记录原始字段和 `raw_payload`
4. 按批次写入 `book_import_stage`

### 10.3 生成候选

从 staging 按 `normalized_word` 聚合，生成候选元单词：

```sql
INSERT INTO import_meta_word_candidates (
    batch_id,
    normalized_word,
    display_word,
    definition,
    difficulty,
    phonetic_detail,
    part_of_speech_detail,
    source_count,
    merge_status,
    created_at,
    updated_at
)
SELECT s.batch_id,
       s.normalized_word,
       MIN(s.word) AS display_word,
       MAX(s.definition) AS definition,
       MAX(s.difficulty) AS difficulty,
       MAX(s.phonetic_detail) AS phonetic_detail,
       MAX(s.part_of_speech_detail) AS part_of_speech_detail,
       COUNT(*) AS source_count,
       'PENDING',
       NOW(),
       NOW()
FROM book_import_stage s
WHERE s.batch_id = :batchId
GROUP BY s.batch_id, s.normalized_word;
```

### 10.4 自动 merge 判定

自动规则建议：

1. 正式库无此词：直接标记 `AUTO_CREATE`
2. 正式库有此词，导入值只是在补全空字段：标记 `AUTO_UPDATE`
3. 同字段双方都有值且不一致：写入 `import_conflicts`
4. 同批次内多来源不一致：写入 `import_conflicts`

### 10.5 人工审核

人工审核的输出应该落回候选表，不直接改正式表。

审核结束后，候选状态统一进入：

- `MANUALLY_RESOLVED`
- `IGNORED`

### 10.6 发布元单词

发布时只处理这些候选：

- `AUTO_CREATE`
- `AUTO_UPDATE`
- `MANUALLY_RESOLVED`

SQL 示例：

```sql
INSERT INTO meta_words (
    normalized_word,
    word,
    definition,
    difficulty,
    phonetic_detail,
    part_of_speech_detail,
    created_at,
    updated_at
)
SELECT c.normalized_word,
       c.display_word,
       c.definition,
       c.difficulty,
       c.phonetic_detail,
       c.part_of_speech_detail,
       NOW(),
       NOW()
FROM import_meta_word_candidates c
WHERE c.batch_id = :batchId
  AND c.merge_status IN ('AUTO_CREATE', 'AUTO_UPDATE', 'MANUALLY_RESOLVED')
ON CONFLICT (normalized_word) DO UPDATE
SET word = EXCLUDED.word,
    definition = EXCLUDED.definition,
    difficulty = EXCLUDED.difficulty,
    phonetic_detail = EXCLUDED.phonetic_detail,
    part_of_speech_detail = EXCLUDED.part_of_speech_detail,
    updated_at = NOW()
WHERE meta_words.word IS DISTINCT FROM EXCLUDED.word
   OR meta_words.definition IS DISTINCT FROM EXCLUDED.definition
   OR meta_words.difficulty IS DISTINCT FROM EXCLUDED.difficulty
   OR meta_words.phonetic_detail IS DISTINCT FROM EXCLUDED.phonetic_detail
   OR meta_words.part_of_speech_detail IS DISTINCT FROM EXCLUDED.part_of_speech_detail;
```

关键点：

- 不是无脑 update 所有冲突行
- 只更新真正变化的记录，减少 WAL 和写放大

### 10.7 发布 dictionary 内容

发布到正式 dictionary 时，按 dictionary 逐本处理：

1. 查或创建稳定的 imported dictionary
2. 确保默认章节 tag 存在
3. 在单事务中替换 `dictionary_words`
4. 更新 counts

示例思路：

```sql
WITH dedup_entries AS (
    SELECT DISTINCT ON (s.dictionary_name, s.normalized_word)
           s.dictionary_name,
           s.normalized_word,
           MIN(s.word) OVER (
               PARTITION BY s.dictionary_name, s.normalized_word
           ) AS display_word,
           MIN(s.entry_order) OVER (
               PARTITION BY s.dictionary_name, s.normalized_word
           ) AS first_entry_order
    FROM book_import_stage s
    WHERE s.batch_id = :batchId
),
resolved_entries AS (
    SELECT d.id AS dictionary_id,
           mw.id AS meta_word_id,
           t.id AS chapter_tag_id,
           ROW_NUMBER() OVER (
               PARTITION BY d.id
               ORDER BY de.first_entry_order, mw.id
           ) AS entry_order
    FROM dedup_entries de
    JOIN dictionaries d
      ON d.name = de.dictionary_name
    JOIN meta_words mw
      ON mw.normalized_word = de.normalized_word
    JOIN tags t
      ON t.dictionary_id = d.id
     AND t.type = 'CHAPTER'
     AND t.parent_id IS NULL
     AND t.name = '默认章节'
)
SELECT * FROM resolved_entries;
```

在事务中执行：

1. `DELETE FROM dictionary_words WHERE dictionary_id = :dictionaryId`
2. `INSERT INTO dictionary_words ... SELECT ...`
3. `UPDATE dictionaries ...`

### 10.8 统一更新 counts

不要在 staging 阶段更新 counts。  
在单本 dictionary 发布结束时统一更新：

```sql
UPDATE dictionaries d
SET word_count = stat.word_count,
    entry_count = stat.entry_count,
    updated_at = NOW()
FROM (
    SELECT dw.dictionary_id,
           COUNT(DISTINCT dw.meta_word_id) AS word_count,
           COUNT(*) AS entry_count
    FROM dictionary_words dw
    WHERE dw.dictionary_id = :dictionaryId
    GROUP BY dw.dictionary_id
) stat
WHERE d.id = stat.dictionary_id;
```

### 10.9 清理策略

批次结束后，必须有收口策略：

1. `book_import_stage`
   - 成功发布后清理
   - 失败批次保留一段时间用于排查

2. `import_meta_word_candidates`
   - 发布后保留摘要，必要时清理 payload

3. `import_conflicts`
   - 已解决且批次完成后可归档

4. `import_batches`
   - 建议保留最近 N 次记录

## 11. 关键技术点

### 11.1 使用 PostgreSQL `COPY`

这是导入阶段最大的性能收益点之一。  
对于百万行数据，`COPY` 基本会显著快于逐条 insert 或 JPA `saveAll`。

### 11.2 解析与发布完全解耦

不要让“文件读取线程”直接操作正式业务表。  
建议拆成四层：

1. Parser Worker
2. Stage Writer
3. Merge Worker
4. Publish Worker

### 11.3 自动 merge 不是简单的 `MAX()`

仅用 `MAX(definition)` 之类的聚合规则，无法表达真正的业务语义。  
自动 merge 至少要区分：

- 新增
- 补全
- 冲突
- 忽略

### 11.4 页面上的 merge 是审核层，不是主引擎

页面上适合做：

- 查看候选
- 查看差异
- 处理冲突
- 触发发布

页面上不适合做：

- 百万级逐条 merge
- 全量人工编辑
- 逐词更新正式表

### 11.5 配置层优化

即使最终采用 `COPY + staging + publish`，也建议补齐基础配置：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 16
      minimum-idle: 4
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 1000
        order_inserts: true
        order_updates: true
```

## 12. 测试与验证方案

### 12.1 正确性测试

#### 用例 1：导入只落 staging，不污染正式库

验证：

- `STAGED` 前后正式 `dictionary_words` 不变化
- 旧 dictionary 仍可读

#### 用例 2：自动 merge 正确识别冲突

构造：

- 正式库已有词
- 新导入候选给出不同 definition

验证：

- 该词进入 `import_conflicts`
- 不会被自动发布

#### 用例 3：人工处理后再发布

验证：

- 冲突解决结果会体现在最终 `meta_words`
- 对应 dictionary 内容正确替换

#### 用例 4：同名 imported dictionary 复用原 `dictionary_id`

验证：

- 发布前后 `dictionary_id` 不变
- `dictionary_assignments`、`study_plans`、`exams` 仍可正常工作

### 12.2 性能测试

性能测试必须使用 PostgreSQL，不要用 H2。  
原因包括：

- `COPY`
- `ON CONFLICT`
- JSONB
- MVCC 行为
- 索引和 WAL 行为

建议至少测三段：

1. 纯 staging 导入耗时
2. 自动 merge 耗时
3. 发布耗时

并和旧实现对比：

1. 旧实现：`MetaWordService#importBooksData`
2. 新实现：`StageImportPipeline -> AutoMerge -> Publish`

### 12.3 稳定性测试

#### 用例 1：导入过程中重启应用

验证：

- 批次状态可恢复或可标记失败
- 正式词库仍可读

#### 用例 2：重复点击导入

验证：

- 同时只允许一个全量导入批次
- 第二个请求返回 `409 Conflict`

#### 用例 3：发布过程中异常

验证：

- 失败的 dictionary 不应留下半成品
- 批次状态明确为 `FAILED` 或 `PARTIALLY_PUBLISHED`

推荐默认不允许部分发布成功后自动标记成功。  
发布失败应明确可重试。

## 13. 分阶段落地建议

### 第一步：先把导入从同步接口中拆出来

先上线：

- 批次表
- `202 Accepted`
- 状态查询
- 文件级进度
- advisory lock

### 第二步：导入热路径只写 staging

上线：

- `book_import_stage`
- `COPY`
- 文件并行解析
- 不再直接写正式 `meta_words` / `dictionary_words`

这是吞吐提升最大的部分。

### 第三步：补自动 merge 和冲突页

上线：

- `import_meta_word_candidates`
- `import_conflicts`
- 自动 merge 规则
- 人工冲突处理页

### 第四步：补发布能力

上线：

- 稳定 `dictionary_id`
- 发布事务
- counts 统一更新
- 发布日志

### 第五步：补运维能力

上线：

- 批次废弃
- 历史查询
- 清理任务
- 审计日志

## 14. 结论

针对当前 `books` 导入慢的问题，真正有效的方向不是继续在现有同步链路里微调 batch，而是把导入功能拆成完整的产品化流程：

1. 导入阶段只负责高吞吐写 staging
2. merge 阶段由系统先自动处理大多数词
3. 页面只处理少量冲突和元单词维护
4. 发布阶段再把确认后的数据写回正式词库
5. 发布时保持 `dictionary_id` 稳定，避免破坏 assignment、study plan、exam 等依赖

如果只优先做一件事，建议先做：

- `异步批次 + staging-only 导入`

如果要做到“快、稳、可审、可发布”，推荐完整落地本文方案：

- `STAGE -> AUTO_MERGE -> MANUAL_REVIEW -> PUBLISH`
