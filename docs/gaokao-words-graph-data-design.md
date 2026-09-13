# 高考英语词汇图数据结构设计

## 1. 目标和范围

本设计把 `generated/gaokao-3500-words-markdown/` 中每个词条 Markdown 转换为可查询、可追溯的词汇知识图。它必须完整保留原文、来源文件和词条顺序，同时表达单词、短语、词义、词根词缀、派生词、同根词等关系。

这里的“完整”是数据不丢失，而不是把不确定的文本关系伪造为确定事实：所有 Markdown 的正文都保存在原文证据中；结构化提取失败或歧义时仍可入图，并标记为待确认。

推荐采用 PostgreSQL 上的异构属性图存储：节点表、关系边表和证据表组成邻接表。它适合当前 Spring Boot + PostgreSQL 项目，具有外键、事务、全文检索和递归 CTE 查询能力，不要求引入专用图库。

## 2. 图模型

```text
SourceDocument ──HAS_EXCERPT──> SourceExcerpt <──DESCRIBED_BY── Lexeme
                                      │                         │
                                      │EVIDENCES                │HAS_SENSE
                                      ▼                         ▼
                                WordRelation              WordSense
                                      │                         │
                                      ├──DERIVED_FROM──> Lexeme  └──USES_MORPHEME──> Morpheme
                                      ├──RELATED_BY_ROOT──> Lexeme
                                      ├──SYNONYM_OF / ANTONYM_OF──> Lexeme
                                      └──PHRASE_CONTAINS──> Lexeme
```

图是有向的。关系的方向只服务于稳定查询，例如 `ability DERIVED_FROM able`；对称关系（如同义、反义）只保存一条规范方向边，并在读取时当作双向关系处理。

## 3. 节点数据结构

所有节点都有 `id`（UUID）、`node_type`、`created_at`、`updated_at` 和 `properties`（JSONB）。`properties` 只保存类型扩展字段；核心查询字段必须使用明确列，不能藏在 JSONB 中。

### 3.1 Lexeme 单词或短语节点

`Lexeme` 是一个可学习、可显示的英文表面形式。它同时覆盖单词（`abandon`）、连字符词和短语（`be able to`）。一个 Lexeme 不等于一个中文释义。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 主键 |
| `canonical_form` | varchar(255) | 规范小写形式，如 `be able to` |
| `display_form` | varchar(255) | 原始展示形式 |
| `form_type` | enum | `WORD`、`PHRASE`、`ABBREVIATION`、`OTHER` |
| `normalized_key` | varchar(255) unique | 大小写、连续空白、弯直引号归一后的去重键 |
| `language` | varchar(16) | 默认 `en` |
| `status` | enum | `ACTIVE`、`NEEDS_REVIEW`、`MERGED` |

约束：`normalized_key` 唯一；同一拼写的不同词性或不同释义不能复制 Lexeme，而应建立多个 WordSense。

### 3.2 WordSense 词义节点

一个词义是“词条 + 词性 + 一个可区分释义”。例如 `abandon` 的动词义和名词义分别建节点。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 主键 |
| `lexeme_id` | UUID | 指向所属 Lexeme |
| `part_of_speech` | enum | `NOUN`、`VERB`、`ADJECTIVE`、`ADVERB`、`PREPOSITION` 等 |
| `definition_zh` | text | 中文释义，保留原文含义 |
| `definition_en` | text nullable | 未来可补充英文释义 |
| `sense_order` | int | 同词词义顺序，从 1 开始 |
| `extraction_status` | enum | `EXTRACTED`、`REVIEWED`、`RAW_ONLY` |

`RAW_ONLY` 允许先完整入库，再逐步提取结构化释义，保证所有单词都被覆盖。

### 3.3 Morpheme 词素节点

用于保存词根、前缀、后缀和教材中的构词说明，例如 `ab-`、`-norm-`、`-ity`。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 主键 |
| `form` | varchar(100) | 如 `-ity` |
| `normalized_key` | varchar(100) unique | 去除表示边界的变体后归一 |
| `morpheme_type` | enum | `ROOT`、`PREFIX`、`SUFFIX`、`UNKNOWN` |
| `meaning_zh` | text nullable | 如“可…性” |

### 3.4 SourceDocument 来源文件节点

每个 DOCX 对应一个节点，避免把来源文件名只作为不可查询的字符串。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 主键 |
| `file_name` | varchar(512) unique | 原 DOCX 文件名 |
| `source_path` | text | 导入时的目录或归档标识 |
| `lecture_no` | int nullable | 从“第 N 讲”解析；解析失败为空 |
| `checksum_sha256` | char(64) nullable | 导入版本确认 |
| `imported_at` | timestamptz | 导入时间 |

### 3.5 SourceExcerpt 原文证据节点

这是完整性和可审计性的关键。每一个 Markdown 中的 `## 内容` 区段都建立一条证据，不因结构化提取失败而丢弃。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 主键 |
| `source_document_id` | UUID | 来源 DOCX |
| `lexeme_id` | UUID | 对应词条 |
| `markdown_file` | varchar(255) | 生成词条 MD 名 |
| `entry_order` | int | 讲义内的编号，如 1、2、3 |
| `raw_heading` | text | 编号词条行 |
| `raw_content` | text | 完整正文，不修改、不裁剪 |
| `content_hash` | char(64) | 幂等导入和变更检测 |
| `parse_status` | enum | `PENDING`、`PARTIAL`、`PARSED`、`NEEDS_REVIEW` |

同一个单词出现于多个讲义时建立多个 SourceExcerpt，而不是覆盖来源。

## 4. 关系边数据结构

关系统一存入 `word_relations`。边本身是带属性的领域对象，而非隐式多对多表，因此可以保存来源、证据、抽取方式和人工确认状态。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 主键 |
| `from_node_id` | UUID | 起点节点 |
| `to_node_id` | UUID | 终点节点 |
| `relation_type` | enum | 关系类型，见下表 |
| `evidence_excerpt_id` | UUID nullable | 支撑此边的原文证据；人工关系也可为空 |
| `confidence` | numeric(4,3) | 0 到 1；人工确认固定为 1 |
| `assertion_status` | enum | `EXTRACTED`、`REVIEWED`、`REJECTED` |
| `position` | int nullable | 同类关系在原文中的出现顺序 |
| `properties` | jsonb | 例如构词箭头、原文片段位置 |
| `created_at` | timestamptz | 创建时间 |

推荐的 `relation_type`：

| 类型 | 中文特指词语 | 起点 → 终点 | 示例 | 含义 |
|---|---|---|---|---|
| `HAS_SENSE` | **释义** | Lexeme → WordSense | `abandon → 动词义` | 词条拥有词义 |
| `DESCRIBED_BY` | **讲解** | Lexeme → SourceExcerpt | `ability → 第01讲证据` | 词条被这段原文讲解 |
| `USES_MORPHEME` | **构词** | WordSense/Lexeme → Morpheme | `ability → -ity` | 构词分析使用词素 |
| `DERIVED_FROM` | **派生** | Lexeme → Lexeme | `ability → able` | 教材明确的派生关系 |
| `RELATED_BY_ROOT` | **同根** | Lexeme → Lexeme | `abnormal → normal` | 共享或围绕同一词根的关系 |
| `SYNONYM_OF` | **同义** | Lexeme → Lexeme | 可扩展 | 同义关系 |
| `ANTONYM_OF` | **反义** | Lexeme → Lexeme | 可扩展 | 反义关系 |
| `PHRASE_CONTAINS` | **组成** | Lexeme → Lexeme | `be able to → able` | 短语包含词元 |
| `VARIANT_OF` | **异形** | Lexeme → Lexeme | 拼写变体 | 词形或拼写变体 |

来源关系也使用固定中文术语：`HAS_EXCERPT` 为**收录**（来源文件 → 原文证据），`EVIDENCES` 为**佐证**（原文证据 → 关系边）。中文特指词语是业务显示名称，不替代稳定的英文枚举值；关系表可增加 `display_name_zh` 只读派生字段，或由后端枚举映射生成，避免数据中出现不一致的中文写法。

只有教材原文或人工审核明确支持时，才创建 `DERIVED_FROM`、`RELATED_BY_ROOT`、同义和反义边。仅因两个词拼写相近不能建立关系。

## 5. PostgreSQL 逻辑表

```sql
create table graph_nodes (
    id uuid primary key,
    node_type varchar(32) not null,
    properties jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table lexemes (
    id uuid primary key references graph_nodes(id),
    canonical_form varchar(255) not null,
    display_form varchar(255) not null,
    normalized_key varchar(255) not null unique,
    form_type varchar(32) not null,
    language varchar(16) not null default 'en',
    status varchar(32) not null
);

create table source_excerpts (
    id uuid primary key references graph_nodes(id),
    source_document_id uuid not null references graph_nodes(id),
    lexeme_id uuid not null references lexemes(id),
    markdown_file varchar(255) not null,
    entry_order integer,
    raw_heading text not null,
    raw_content text not null,
    content_hash char(64) not null unique,
    parse_status varchar(32) not null
);

create table word_relations (
    id uuid primary key,
    from_node_id uuid not null references graph_nodes(id),
    to_node_id uuid not null references graph_nodes(id),
    relation_type varchar(64) not null,
    evidence_excerpt_id uuid references source_excerpts(id),
    confidence numeric(4,3) not null check (confidence between 0 and 1),
    assertion_status varchar(32) not null,
    position integer,
    properties jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    unique (from_node_id, to_node_id, relation_type, evidence_excerpt_id)
);

create index idx_word_relations_from_type on word_relations (from_node_id, relation_type);
create index idx_word_relations_to_type on word_relations (to_node_id, relation_type);
create index idx_source_excerpts_lexeme on source_excerpts (lexeme_id);
```

实际迁移中，`graph_nodes` 可以省略并将各类节点置于独立表；本设计保留它，是为了让任意节点间的关系都能由 `word_relations` 表达。所有外键均使用默认限制删除策略，禁止级联删除；删除来源或词条必须先经显式业务流程处理关联记录。

## 6. Markdown 到图的导入规则

1. 读取每个 Markdown 的 YAML `word` 和 `sources`，通过 `normalized_key` 幂等创建或更新 Lexeme。
2. 为每个“来源文件 + 内容区段”创建 SourceDocument 和 SourceExcerpt，正文原样写入 `raw_content`。
3. 从标题行解析 `entry_order`；不能解析时仍导入证据并设为 `NEEDS_REVIEW`。
4. 解析词性和中文释义，创建 WordSense；不能可靠切分时只保留 `RAW_ONLY` 词义或待审核证据。
5. 仅从 `【词根词缀：…】` 等明确模式提取 Morpheme 与 `USES_MORPHEME` 边；从教材明示的派生/同根词建立词间边。
6. 每条抽取边都写入 `evidence_excerpt_id`、`confidence` 和 `assertion_status=EXTRACTED`。审核通过后更新为 `REVIEWED`，不覆盖原文。

导入的外部接口应保持很小：`importDirectory(path)`、`getWordGraph(normalizedKey, depth)` 和 `searchWords(query)`。词条归一、去重、证据保存、关系抽取和重跑幂等性隐藏在导入模块内部，形成高深度模块。

## 7. 一致性规则和覆盖保证

- 每个 Markdown 必须产生且仅产生一个 Lexeme；若名称无法规范化，导入失败并记录错误，不可静默跳过。
- 每个 `sources` 项至少产生一个 SourceDocument，并且每个正文区段至少产生一个 SourceExcerpt。
- 每个 SourceExcerpt 必须通过 `DESCRIBED_BY` 或等价外键关联到 Lexeme，保证可从单词回溯原文。
- 每条结构化词义、词素和词间关系必须带来源证据，或标记为人工维护。
- `content_hash` 防止重复导入同一原文；内容变化产生新版证据而非覆盖历史。
- `REJECTED` 边保留审核痕迹，但默认查询不返回。
- 建立导入报告：输入 MD 数、Lexeme 数、SourceExcerpt 数、解析成功/待审核数和未处理错误数；MD 数与成功或错误记录数必须相等。

这套规则确保现有 3,296 个单词 Markdown 全部可入图：简单词、短语、重复来源词条和暂时无法自动理解的讲解文本都不会丢失。

## 8. 典型查询

| 问题 | 图遍历 |
|---|---|
| 查询 `ability` 的释义、词根和原文 | Lexeme → WordSense / SourceExcerpt → Morpheme |
| 找出与 `abnormal` 同根的词 | Lexeme -`RELATED_BY_ROOT`→ Lexeme |
| 查某讲义中所有词条 | SourceDocument → SourceExcerpt → Lexeme，按 `entry_order` 排序 |
| 展示某关系为何成立 | WordRelation → `evidence_excerpt_id` → SourceExcerpt.raw_content |
| 找没有完成结构化解析的单词 | SourceExcerpt.`parse_status != PARSED` |

## 9. 实施顺序

1. 创建上述节点、词条、词义、词素、来源和关系表，以及只读查询索引。
2. 实现 Markdown 导入器的“Lexeme + SourceExcerpt”最小闭环，并生成覆盖报告。
3. 添加词性与释义提取；对未确定内容保留 `RAW_ONLY`，不要阻塞全量导入。
4. 添加词根词缀和派生关系提取，再提供人工审核界面或后台任务。
5. 在词典查询中按置信度和审核状态展示图关系，并始终可查看原文证据。
