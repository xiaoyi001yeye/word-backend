# 高考英语 3500 Markdown 保真存储与导入方案

## 1. 结论

`generated/gaokao-3500-words-markdown/` 不应直接套进现有 CSV/JSON 词书导入流程，也不应只把正文塞入 `meta_words.definition`。

推荐采用**原文层 + 现有学习数据投影层**：

```text
Markdown 文件（权威输入，保留在 Git/备份中）
             │
             ▼
长期原文层：来源文档、原始词条、YAML、完整正文、哈希、解析告警
             │
             ├──可追溯关联──► MetaWord（现有全局可查询词）
             └──按讲次和顺序──► Dictionary / DictionaryWord / Tag（现有词书）
```

其中，原文层是“不丢失任何信息”的系统记录；`MetaWord`、`Dictionary` 与 `DictionaryWord` 是供现有学习、检索和词书功能使用的结构化投影。结构化抽取不确定时仍应保存原文并标记待处理，不能跳过或覆盖。

## 2. 输入资料盘点

目标目录：`generated/gaokao-3500-words-markdown/`。

| 项目 | 实测值 | 依据 |
|---|---:|---|
| 词条 Markdown | 3,296 | 目录 `README.md` 与文件计数 |
| README | 1 | 同目录 |
| 原始 DOCX | 55 | `README.md`，以及 YAML `sources` 去重 |
| 原文总大小 | 约 1.6 MB | 词条 `.md` 文件合计 |
| 词条格式 | YAML + 来源 + 完整内容 | 如 `abandon.md`、`ability.md` |

单个文件格式稳定，但正文不是统一字段表：

```markdown
---
word: abandon
sources:
  - "高考英语3500单词第01讲(单词速记与拓展).docx"
---

# abandon

## 来源
...

## 内容

**来源文件：** `...docx`

1. abandon vt. 抛弃，放弃【...词根、同根词、扩展讲解...】
```

正文可能含词性、释义、词根词缀、同根词、派生词、例外说明和转换/OCR 异常。还存在短语与非标准词头，例如 `would modal`，以及需人工复核的拼写形式。因此“把每条拆成标准释义”只能是渐进增强，不能是入库前置条件。

## 3. 当前项目可复用的部分

### 3.1 `MetaWord`：结构化学习词投影

`src/main/java/com/example/words/model/MetaWord.java` 已支持：

- `word` 与全局唯一的 `normalized_word`；
- 平面字段 `phonetic`、`definition`、`part_of_speech`、`example_sentence`、`translation`、`difficulty`；
- JSONB 字段 `phonetic_detail`、`part_of_speech_detail`、`syllable_detail`。

其中 `part_of_speech_detail` 可承载词性、释义、例句、词形、同义/反义等已可靠解析出的内容。相关迁移包括 `V4__add_jsonb_fields_to_meta_words.sql`、`V19__expand_meta_word_text_columns.sql` 和 `V22__add_meta_word_syllable_detail.sql`。

它适合作为同一标准化词头的全局学习记录，但不适合作为每份讲义原文的存档：同一 `normalized_word` 只能保留一份主投影。

### 3.2 词书及排序：`Dictionary`、`DictionaryWord`、`Tag`

`Dictionary` 可建立一个系统词书“高考英语 3500（讲义原文版）”；`DictionaryWord` 已有 `chapter_tag_id`、`entry_order`，`V16__add_tag_system_and_dictionary_entry_chapter_support.sql` 已提供章节标签机制。建议以 55 份 DOCX 的讲次创建一级 `CHAPTER` 标签，并按讲义中的原始编号设置 `entry_order`。

这能直接接入现有学习计划和词书查询。词书条目只能指向 `MetaWord`，因此它是“学习入口”，不是原文档案。

### 3.3 现有批次导入能力：只复用思路，不直接复用实现

`BooksImportJobService` 已具备批次状态、冲突审阅、发布记录和分阶段导入的模式，值得复用其事务与可观测性设计。但当前实现并不支持本任务：

- 仅扫描容器 `/app/books` 下的 `.csv`、`.json`（`BooksImportJobService.java` 的 `discoverImportFiles`）；
- CSV 只读取前两列；JSON 仅接受 `MetaWordEntryDtoV2` 数组；
- 临时表 `book_import_stage.raw_payload` 会在批次清理时删除，不能承担永久原文保存；
- 发布按 `normalized_word` 聚合，且同词采用首个结构化结果，会折叠不同来源或不同讲解；
- 重发已导入词书时会重建其关联，不能用作原文版本历史。

故应新增 Markdown 专用导入模块；不要把 `.md` 加进当前扫描扩展名后就执行既有发布流程。

## 4. 必须补齐的长期原文层

建议新增两张核心表。命名可按项目最终惯例微调，但职责应保持不变。

### 4.1 `word_source_documents`：来源 DOCX 的逻辑档案

一条记录代表一个 `sources` 中出现的 DOCX 名称，而不是本地 DOCX 文件是否仍可取得。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint/uuid PK | 主键 |
| `collection_key` | varchar | 固定值，例如 `gaokao-3500-markdown` |
| `source_name` | text | YAML 中原始 DOCX 文件名，原样保存 |
| `source_path` | text nullable | 原始 DOCX 已知时记录其路径或归档地址 |
| `lecture_no` | int nullable | 从文件名解析的讲次；失败留空 |
| `source_hash` | char(64) nullable | 若有 DOCX，存 SHA-256 |
| `metadata` | jsonb | 将来扩展页码、转换器版本等 |
| `created_at` / `updated_at` | timestamp | 审计字段 |

唯一性建议：`(collection_key, source_name)`。

### 4.2 `word_source_entries`：每个 Markdown 的永久证据

一条记录对应一个词条 Markdown；这是完整性边界，绝不能因词头重复而合并。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint/uuid PK | 主键 |
| `collection_key` | varchar | 资料集标识 |
| `relative_path` | text | 例如 `abandon.md`，保留文件身份 |
| `source_document_id` | FK | 指向来源 DOCX；当前每条一个来源 |
| `meta_word_id` | FK nullable | 指向解析后的 `MetaWord`；无法确定时允许为空 |
| `raw_word` | text | YAML `word` 原值 |
| `normalized_word` | text | 与 `WordNormalizationUtils.normalize` 一致的检索键 |
| `entry_order` | int nullable | 从正文编号提取，例如 `1` |
| `front_matter` | jsonb | YAML 完整对象，不只存已知字段 |
| `raw_markdown` | text | 文件逐字原文，包含 YAML、标题和正文 |
| `content_markdown` | text | 可选的“内容”区段；不能替代 `raw_markdown` |
| `content_hash` | char(64) | UTF-8 原文件 SHA-256 |
| `parser_version` | varchar | 解析规则版本 |
| `parse_status` | varchar | `RAW_ONLY`、`PARTIAL`、`PARSED`、`NEEDS_REVIEW` |
| `parse_warnings` | jsonb | 行号、原因、保留的异常信息 |
| `extracted_payload` | jsonb | 候选词性/释义/词根等；可重算，不替代原文 |
| `supersedes_entry_id` | FK nullable | 内容变化后指向旧版本，保留历史 |
| `created_at` / `updated_at` | timestamp | 审计字段 |

约束与索引：

- 当前活动版本唯一：`(collection_key, relative_path)`；若保留多版本，可增加 `is_current` 并对活动版本建部分唯一索引；
- `content_hash` 建普通索引，用于检测完全相同的内容；**不可**把 `normalized_word` 设为此表唯一键；
- 给 `meta_word_id`、`source_document_id`、`normalized_word` 建查询索引；
- 新外键使用限制删除或显式服务清理，不使用 `ON DELETE CASCADE`。这符合仓库的删除规范。

如果一个 Markdown 将来含多个 `sources`，再增加 `word_source_entry_documents(entry_id, source_document_id, position)` 关联表；当前数据可先以单 FK 落地，但导入器必须校验并告警多来源而非静默丢弃。

## 5. 字段归属与保真规则

| 信息 | 永久保存位置 | 可选结构化投影 |
|---|---|---|
| Markdown 文件名、目录位置 | `word_source_entries.relative_path` | 无 |
| YAML 的所有键和值 | `front_matter` 与 `raw_markdown` | `raw_word`、来源关联 |
| 整个正文、标点、换行、讲解 | `raw_markdown` | 不强制拆分 |
| DOCX 文件名 | `word_source_documents.source_name` | `lecture_no` |
| 讲义内编号 | `entry_order` | `DictionaryWord.entry_order` |
| 词头及大小写 | `raw_word` | `MetaWord.word`、`normalized_word` |
| 音标、词性、释义、例句 | `extracted_payload`（可重算） | `MetaWord` 平面字段 / JSONB |
| 词根、派生、同根词等讲解 | 原文及候选 `extracted_payload` | 后续专用关系表或现有图设计 |
| 无法识别/有歧义内容 | 原文 + `parse_warnings` | 不写入猜测性事实 |

原则：`raw_markdown` 是唯一的完整事实来源；解析产物一律可以删除后重建。不得为提高导入成功率而截断正文、丢弃未知 YAML 字段或将无法解析的记录排除在批次外。

## 6. 导入流程

### 阶段 A：导入前清单与只读校验

1. 固定资料集键：`gaokao-3500-markdown`。
2. 枚举目录下所有 `.md`，忽略且仅忽略 `README.md`；预期为 3,296 条。
3. 对每个文件读取 UTF-8 原始字节并计算 SHA-256。
4. 解析 YAML；校验至少有非空 `word`、非空 `sources`、`## 内容`。不合格项生成错误记录，仍保存原始文件内容。
5. 生成 manifest：相对路径、哈希、字节数、词头、来源、解析状态；导入前存档到批次记录或作为审计附件。

### 阶段 B：先写原文层，再做解析

在单个词条的事务中按如下顺序处理：

1. `upsert` 来源 DOCX 档案；
2. 以资料集 + 相对路径定位旧条目；哈希相同则计为 `UNCHANGED`，不重复创建；
3. 哈希不同则创建新版本（或显式更新并保存旧版本），原文始终完整入库；
4. 从正文首行尝试提取 `entry_order`，提取失败设 `NEEDS_REVIEW`；
5. 只对规则足够明确的字段生成 `extracted_payload`，并记录规则版本及告警。

这一步完成后，即使后续结构化发布失败，资料也已经安全存储并可回导。

### 阶段 C：生成 `MetaWord` 投影与处理冲突

1. 用 `WordNormalizationUtils.normalize(rawWord)` 查找 `MetaWord`；
2. 不存在时创建；存在时不直接以本资料覆盖人工维护字段；
3. 将可靠的缺失字段补齐，或将不一致内容写为待审核候选；
4. 回填 `word_source_entries.meta_word_id`；
5. 对短语、OCR 疑点或同形但内容冲突的条目，保持 `meta_word_id = null` 或关联现有词后标记 `NEEDS_REVIEW`。原文仍是完整的。

不要用“每个 Markdown 必须创建一个 MetaWord”作为约束。3,296 是证据条目数，不必等于全局唯一词头数。

### 阶段 D：发布学习词书

1. 创建/更新系统词书“高考英语 3500（讲义原文版）”，`creation_type=IMPORTED`、`scope_type=SYSTEM`；
2. 根据 `word_source_documents.lecture_no` 建立 55 个讲次标签；解析不到讲次的来源放进“待整理来源”标签；
3. 对有 `meta_word_id` 的当前词条按 `(lecture_no, entry_order, relative_path)` 写入 `DictionaryWord`；
4. 单个词在同一讲次重复时，保留一条学习关联，但不能删除第二条 `word_source_entries` 证据；
5. 无法关联 `MetaWord` 的条目不应阻断批次发布，计入待审核清单。

## 7. 实现边界与建议的代码改动

建议新建一个独立模块，例如：

```text
model/WordSourceDocument.java
model/WordSourceEntry.java
repository/WordSourceDocumentRepository.java
repository/WordSourceEntryRepository.java
service/GaokaoMarkdownImportService.java
service/GaokaoMarkdownParser.java
controller/GaokaoMarkdownImportController.java   （若需要后台触发）
resources/db/migration/V43__add_word_source_archive.sql
```

`GaokaoMarkdownParser` 应是纯解析模块：输入原始 Markdown 和相对路径，输出 YAML、正文、词头、顺序、候选结构化字段和告警；不负责数据库访问。`GaokaoMarkdownImportService` 负责幂等写原文、创建投影、批次报告和事务边界。

如果资料由 Docker 中导入，必须把该目录以只读卷挂到容器内，并将导入根目录做成受控配置；不能沿用写死的 `/app/books` 及 CSV/JSON 扫描逻辑。应用中保留的 `Dictionary.file_path` 只记录导入资料根与版本提示，不能代替数据库中的逐条原文。

已有 `docs/gaokao-words-graph-data-design.md` 设计了词义、词根和关系图。它适合本方案完成“原文层最小闭环”后作为第二阶段：图中的原文证据应由 `word_source_entries` 提供或与其一一关联，避免再建第二份不可同步的正文副本。

## 8. 验收与回滚

### 8.1 必须自动化验证的覆盖指标

首轮导入完成后生成并保存报告，至少包含：

| 检查 | 通过条件 |
|---|---|
| 输入文件覆盖 | 3,296 个词条均有成功原文记录或明确错误记录 |
| 来源覆盖 | 55 个不同 DOCX 名称均有 `word_source_documents` 记录 |
| 原文完整性 | 每个活动词条的数据库 SHA-256 与源文件完全一致 |
| YAML 完整性 | 每个源文件的 YAML 在 `front_matter` 可还原，并仍存在于 `raw_markdown` |
| 可回导 | 从 `raw_markdown` 导出的文件树逐文件哈希等于输入目录 |
| 投影关联 | 已关联、待审核、解析失败数量分别可查，总和为 3,296 |
| 词书顺序 | 每讲条目按原始编号排序；无编号项有显式告警 |
| 幂等性 | 对未变目录第二次运行不新增活动原文条目、不重复词书关联 |
| 变更检测 | 修改一个文件后仅产生该文件的新版本/审计变更 |

### 8.2 事务和回滚策略

- 每个 Markdown 的“原文档案 + 解析结果 + 投影关联”使用独立事务，单条失败不损害已入库原文；
- 批次发布词书前先完成原文层并生成 manifest；
- 不物理删除导入原文来回滚。将批次标记为失败/已废弃，或把活动版本指针切回前一版本；
- 词书重建仅影响该系统词书的关联，且必须采用显式删除顺序，不引入级联删除；
- 源目录应继续保留在版本控制或独立备份中；数据库存档不是唯一备份策略。

## 9. 分阶段实施顺序

1. 建表与实体：只实现来源档案、词条原文、哈希、状态、索引与无级联外键。
2. 实现 Markdown 清单、原样入库、幂等重跑和 3,296/55/哈希报告；此阶段不依赖语义解析。
3. 实现基础解析：词头、来源、编号、显式的词性/中文释义候选；无法确定即告警。
4. 接入现有 `MetaWord` 和高考系统词书，建立 55 讲次标签及排序。
5. 增加人工审核页/API，处理 OCR、短语、冲突词义和待关联词条。
6. 需要词根、派生、同根词查询时，再按图数据设计建立关系与证据关联；所有关系必须回指本原文层。

这条路径首先保证资料永不因解析能力不足而丢失，再逐步提升可学习、可检索和可图谱化程度。

## 10. 当前单词记忆维度与适配改造

### 10.1 当前程序实际支持的记忆维度

当前系统并非没有记忆数据，而是将所有记忆结果汇总到“学生 × `MetaWord`”这一粒度。

| 层次 | 现有存储 | 已支持的维度 |
|---|---|---|
| 全局单词记忆 | `student_word_memories` / `StudentWordMemory` | 莱特纳盒级 `box_level`、总掌握度 `mastery_level`、下次复习日、对错次数、连续正确次数、上次结果/来源、错词标记、收藏、上次学习时间 |
| 全局记忆事件 | `student_word_memory_events` / `StudentWordMemoryEvent` | 结果、学习来源（计划、自学、正式考试、自测）、来源记录 ID、词书 ID、盒级前后值、时间 |
| 学习计划内进度 | `study_word_progresses` / `StudyWordProgress` | 学习阶段、计划内掌握度、分配/复习日期、对错次数、复习次数、状态、单词专注时长统计 |
| 单次学习记录 | `study_records` / `StudyRecord` | 新学/复习、正确/错误/跳过、总时长、专注/空闲时长、交互数、注意力状态、阶段变化 |

其中 `StudentWordMemoryService` 以固定间隔 `0, 1, 2, 4, 7, 15, 30` 天更新盒级，并使用正确率计算总掌握度；`StudyPlan` 则允许每个计划配置复习间隔。两套进度目前都绑定 `meta_word_id`。

### 10.2 当前模型不能表达的内容

`MetaWord` 能存音标、释义、词性、例句、词形、同反义词等**词条内容**，但不代表系统能分别记录学生对这些内容的掌握情况。面对高考讲义原文，当前模型缺少：

- 拼写/词形、发音、词义、语境例句、构词法、搭配或短语等**分维度掌握度**；
- 同一单词的多个词义或多个讲义解释的独立记忆状态；
- “本次答的是哪个记忆卡片、展示了哪份讲义原文、用了哪种题型”的可审计事件；
- 当 Markdown 重新解析或人工修订后，已学习内容所对应版本的稳定定位；
- 将“看过一段讲义原文”与“能答对某个可验证知识点”区分开来。

因此不能仅给 `student_word_memories` 继续增加 `pronunciation_mastery`、`meaning_mastery` 等许多列：维度会持续扩展，且一个词有多个释义/多个例句时仍无法定位具体学习目标。

### 10.3 推荐的记忆对象层次

采用以下三层，而不是用 Markdown 原文直接作为记忆状态主键：

```text
MetaWord（全局词头）
  └─ WordMemoryUnit（一个可被出题、作答、复习的稳定记忆单元）
       └─ StudentMemoryUnitState（某学生对此单元的间隔复习状态）
            └─ StudentMemoryUnitEvent（每次作答的不可变事件）

WordSourceEntry（讲义原文证据） ──佐证/展示──► WordMemoryUnit
```

- `MetaWord` 继续是词条、词书和全局单词汇总的锚点。
- `WordMemoryUnit` 是新的学习粒度，例如“`abandon` 的拼写”“`abandon` 的动词释义：抛弃，放弃”“`ability` 的后缀 `-ity` 构词知识”。
- `WordSourceEntry` 只负责原文证据与展示上下文；它不等于学生记忆状态。相同的可训练知识可以由多份讲义原文佐证。
- `StudentMemoryUnitState` 负责排期和分维度掌握度；`StudentWordMemory` 保留为兼容当前错词本、收藏与单词总览的汇总记录。

### 10.4 建议的初始记忆维度

首期不要把所有讲解自动拆成知识点。先只创建可稳定验证的维度，其他内容保留为原文阅读材料。

| `dimension_code` | 训练目标 | 创建条件 | 示例 |
|---|---|---|---|
| `FORM_RECOGNITION` | 看词识别词形 | 每个有效词头 | 看见 `abandon` |
| `SPELLING` | 听/看义拼写词头 | 词头可规范化 | 拼写 `ability` |
| `PRONUNCIATION` | 音标或发音辨识 | 有可靠音标/音频 | 英美音标辨识 |
| `SENSE` | 词义与词性匹配 | 可靠切出词性和释义 | `abandon` → `vt. 抛弃，放弃` |
| `USAGE` | 例句或语境理解 | 有结构化例句/可审核题干 | 语境中选义 |
| `INFLECTION` | 词形变化 | 有可靠 `Inflection` 数据 | 单复数、过去式 |
| `COLLOCATION` | 固定搭配/短语 | 人工审核或结构化导入 | `be able to` |
| `MORPHOLOGY` | 词根、前后缀、派生 | 教材明确且已审核 | `ability` 与 `-ity` |

`FORM_RECOGNITION`、`SPELLING` 和 `SENSE` 可作为第一批；`MORPHOLOGY` 与 `COLLOCATION` 需等原文解析和人工审核成熟后再启用。无法可靠抽取的原文不建立单元，但始终可在学习页作为“拓展讲解”显示。

### 10.5 建议新增的存储结构

以下表与第 4 节的原文层配套。表名是建议名，重点是职责和唯一性约束。

#### `word_memory_units`：可训练知识单元模板

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint/uuid PK | 单元 ID |
| `meta_word_id` | FK | 所属全局单词 |
| `dimension_code` | varchar/enum | 上表中的记忆维度 |
| `unit_key` | text | 同词同维度下稳定且可重建的键，例如 `sense:verb:1` |
| `prompt_payload` | jsonb | 出题所需的受控内容快照，如题干、正确答案、干扰项来源 |
| `display_payload` | jsonb | 学习页展示所需内容；不能替代原文 |
| `source_entry_id` | FK nullable | 首要原文证据；多来源时另用关联表 |
| `content_hash` | char(64) | 单元语义内容的版本哈希 |
| `status` | varchar | `ACTIVE`、`NEEDS_REVIEW`、`RETIRED` |
| `created_at` / `updated_at` | timestamp | 审计字段 |

唯一性建议：活动单元 `(meta_word_id, dimension_code, unit_key)` 唯一。解析规则变动时，保持 `unit_key` 稳定、改变 `content_hash`；若知识语义已不相同，则停用旧单元并创建新单元，不覆盖既有答题历史。

#### `word_memory_unit_sources`：单元与原文证据的多对多关联

字段为 `memory_unit_id`、`source_entry_id`、`evidence_role`、`position`、`created_at`。这使一个词义/构词知识可被多份讲义共同佐证，也避免将 Markdown 正文复制到记忆表。

#### `student_memory_unit_states`：学生对每个记忆单元的状态

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint/uuid PK | 状态 ID |
| `student_id` | FK | 学生 |
| `memory_unit_id` | FK | 训练单元 |
| `box_level` | int | 该维度自己的间隔复习盒级 |
| `mastery_level` | numeric | 该维度自己的掌握度 |
| `next_review_at` | timestamp/date | 下次复习时间 |
| `correct_times` / `wrong_times` / `correct_streak` | int | 该单元统计 |
| `last_result` / `last_attempt_at` | enum/timestamp | 最近学习结果 |
| `scheduler_version` | varchar | 排期算法版本，便于规则升级 |
| `created_at` / `updated_at` | timestamp | 审计字段 |

唯一性：`(student_id, memory_unit_id)`。这里的复习时间必须下沉到单元，而不能只保留在 `student_word_memories.next_review_date`，否则不同词义/拼写维度仍会互相覆盖。

#### `student_memory_unit_events`：不可变的学习尝试

至少保存 `student_memory_unit_state_id`、`student_id`、`memory_unit_id`、`result`、`attempt_type`、`source_type`、`source_id`、`dictionary_id`、`study_record_id`、`prompt_hash`、`answer_payload`（注意脱敏）、`state_before`、`state_after`、`occurred_at`。该表是重算掌握度、分析题型效果与解释学习结果的依据；不能只记录盒级前后值。

所有新增外键应采用限制删除或明确业务清理流程，不新增 `ON DELETE CASCADE`。原文版本、记忆单元和学生事件均不得因删除词书而被连带删除。

### 10.6 对现有表和流程的具体调整

| 现有对象 | 保留/调整方式 |
|---|---|
| `student_word_memories` | 保留为“单词总览”与兼容层；`mastery_level` 改由活跃记忆单元状态加权汇总，不再是唯一排期依据。`favorite`、`auto_wrong` 仍可保持在单词级。 |
| `student_word_memory_events` | 保留历史读取；新事件写入 `student_memory_unit_events`。若需统一报表，可加 `memory_unit_id` nullable 后双写一段迁移期，但不要把不同粒度的事件混淆为同一事实。 |
| `study_word_progresses` | 保留“计划内是否已覆盖该词”的进度；不要复制每个维度的排期。可增加 `introduced_unit_count`、`mastered_unit_count` 等汇总字段或由查询计算。 |
| `study_day_task_items` | 增加 `memory_unit_id` nullable。旧任务继续只使用 `meta_word_id`；新维度任务使用 `memory_unit_id`，且必须能反查其 `meta_word_id` 以保持界面兼容。 |
| `study_records` 与 `RecordStudyRequest` | 增加 `memory_unit_id` nullable、题型/尝试上下文；旧客户端仅传 `metaWordId` 时按默认 `FORM_RECOGNITION` 或旧单词流程处理。新任务提交时校验该单元属于该 `metaWordId` 且对学生可见。 |
| `StudentWordMemoryService` | 不应继续直接承担每一种维度的排期实现；改为调用新的记忆排期模块并刷新单词汇总。 |

迁移应以“新增 nullable 列和新表 → 双写 → 回填/核对 → 切换读路径 → 删除旧依赖（如确有必要）”进行。不要改写或删除既有学生的 `student_word_memories`、`study_records`、`student_word_memory_events` 历史数据。

### 10.7 模块设计与小接口

新增一个深度模块 `MemorySchedulingModule`，把“题目属于何种维度、学生是否有资格作答、更新单元状态、写事件、刷新单词汇总、计算下次复习时间”隐藏在同一个实现中。其外部接口应保持很小，例如：

```text
recordAttempt(studentId, memoryUnitId, attempt) -> MemoryAttemptResult
getDueUnits(studentId, scope, limit) -> List<DueMemoryUnit>
refreshWordSummary(studentId, metaWordId) -> StudentWordMemory
```

调用方不应自行计算盒级、在多个表间复制对错次数，或猜测某个 `unit_key` 的含义。这条接口所在的 seam 让题型、排期算法和原文来源的复杂性集中在模块内部；对调用方提供的是“提交一次作答”和“取得待复习单元”的高 leverage 能力。

### 10.8 分步落地建议

1. 先完成第 4 节原文层，建立 `MetaWord` 与原文的可追溯关联。
2. 创建 `word_memory_units`，只生成 `FORM_RECOGNITION`、`SPELLING`、`SENSE` 三类已审核单元。
3. 建立状态和事件表，但保留旧单词记忆表；新旧数据分别统计并做一致性报告。
4. 扩展每日任务与学习提交，使任务可指向 `memory_unit_id`；旧客户端走兼容路径。
5. 接入 `MemorySchedulingModule`，按单元排期，同时刷新单词级总览。
6. 最后才引入构词、搭配、例句等复杂维度，并要求每个单元均可回跳至 `word_source_entries.raw_markdown` 的证据。
