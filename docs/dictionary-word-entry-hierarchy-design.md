# 词书词条层级设计文档

## 1. 文档目标

本文档用于将 [词书词条层级需求说明](./dictionary-word-entry-hierarchy-requirements.zh-CN.md) 落实为可审阅的后端设计方案，重点覆盖：

1. `dictionary_words` 从“唯一关联表”升级为“书内词条出现记录”的建模方案
2. 用独立 `tags` 树维护章节层级，避免前端手填章节导致错别字和层级不一致
3. 支持同词在同一本词书中多次出现
4. 支持单词详情中的“关联辞书回显”
5. 统一整理数据库、接口、服务、测试层面的代码改动清单，方便人工审核

本文档只做设计，不直接修改代码。

## 2. 设计结论

本次设计采用以下主方案：

1. 新增独立 `tags` 表，维护层级化标签树
2. `tags.type` 支持类型化扩展，本期先落地 `CHAPTER`
3. `tags` 通过 `parent_id` 维护上下级关系，通过 `sort_order` 和派生路径键维护稳定排序
4. `dictionary_words` 不再自己保存整段层级路径，而是通过 `chapter_tag_id` 指向一个叶子章节标签
5. `dictionary_words` 增加 `entry_order`，表示该单词在当前章节下的顺序
6. 同一个 `meta_word` 可以在同一本词书中多次出现，只要它们的章节位置或顺序不同
7. 为后续扩展引入通用 `tag_relations` 表，使标签可以关联 `dictionaries`、`meta_words`、`dictionary_words` 等资源

这是一个“章节树单独建模 + 词条引用章节节点”的设计，而不是继续把章节路径内嵌在 `dictionary_words` 内。

## 3. 需求输入与范围

本次设计基于以下前提：

1. 同一个 `meta_word` 可以在同一本词书中出现多次
2. 书内位置可能是 `第1章 > 第2节`，也可能是 `第3单元 > 第1课`
3. 页面上应通过选取章节节点的方式完成录入，避免人工输入章节名
4. 单词详情要能回显“这个词在哪些辞书的哪个章节中”
5. 学习进度本期仍允许按唯一单词维度跟踪
6. 标签体系未来不仅服务章节，还可能服务其他资源关联场景

本次设计覆盖：

1. 数据库模型
2. 后端接口
3. 服务与查询逻辑
4. 统计与学习计划逻辑
5. 代码改动清单

本次设计不覆盖：

1. 前端交互细节
2. 章节树可视化交互
3. 复杂标签搜索运营后台
4. 二期的章节级学习进度和章节级考试能力

## 4. 当前代码现状

当前系统的关键现状如下：

1. `dictionary_words` 只有 `dictionary_id`、`meta_word_id`、`created_at`
2. 数据库对 `(dictionary_id, meta_word_id)` 有唯一约束，因此同词无法在同一本词书中重复录入
3. `DictionaryWordService` 和导入链路都默认“同一本词典一个词只能出现一次”
4. `/api/dictionary-words/dictionary/{dictionaryId}/words` 返回的是 `Page<MetaWord>`，不具备词条位置表达能力
5. `StudyPlanService` 当前按 `dictionary_words.id ASC` 决定新词顺序
6. `GET /api/meta-words/{id}` 当前仅返回 `MetaWord` 本身，没有关联辞书与章节位置回显
7. 仓库中目前没有 `Tag`、`Label` 或类似的层级标签模型

以上现状决定了：如果继续把章节信息写成自由文本字段，不仅无法解决多层级和选取问题，也无法从根本上减少录入错误。

## 5. 核心设计原则

### 5.1 章节是独立对象，不是字符串

章节应当是一个可维护、可选取、可排序、可复用的独立对象。

### 5.2 词条与章节解耦

`DictionaryWord` 表示某个单词在某本词书中的一次出现。

这个“出现”通过引用章节节点来表达书内位置，而不是自己存自由文本路径。

### 5.3 标签体系可扩展

虽然本期只使用 `CHAPTER` 类型，但标签系统设计为通用模型，后续可以支持：

1. 词书标签
2. 单词标签
3. 教学标签
4. 用户自定义标签

### 5.4 兼顾当前需求和未来扩展

本期章节功能需要“强约束、好排序、易查询”，因此章节和词条的主关系需要可直接索引。

未来标签扩展需要“多资源、多类型、多对多”，因此需要预留通用关系表。

## 6. 数据模型设计

## 6.1 术语定义

- `MetaWord`：全局唯一单词
- `DictionaryWord`：某单词在某本词书中的一次出现记录
- `Tag`：可层级化的标签节点
- `Chapter Tag`：`type=CHAPTER` 的标签节点
- `Entry Order`：单词在某个章节下的顺序

## 6.2 标签体系总览

推荐引入两类表：

1. `tags`
2. `tag_relations`

其中：

1. `tags` 负责维护标签自身属性和树结构
2. `tag_relations` 负责通用标签关联能力
3. `dictionary_words.chapter_tag_id` 负责当前主业务场景中的“词条主章节归属”

这样的设计是“强业务引用 + 通用扩展能力”并存。

## 6.3 `tags` 表设计

推荐字段：

1. `id`
2. `name`
3. `type`
   - 本期先有 `CHAPTER`
4. `dictionary_id`
   - 对于 `CHAPTER` 类型必填，表示该章节树属于哪本词书
5. `parent_id`
   - 自关联，维护上下级关系
6. `sort_order`
   - 当前父节点下的排序
7. `path_name`
   - 规范化展示路径，例如 `第1单元 > 第2课`
8. `path_key`
   - 稳定比较键，例如 `1/12/35`
9. `sort_key`
   - 稳定排序键，例如 `000001.000002.000003`
10. `level`
11. `created_at`
12. `updated_at`

### 说明

1. `parent_id` 负责树结构
2. `sort_order` 负责同级节点排序
3. `path_name`、`path_key`、`sort_key` 是派生字段，用于降低查询和排序复杂度
4. `dictionary_id` 让章节树天然挂靠在某本词书下，便于页面选择和权限校验

## 6.4 `tag_relations` 表设计

推荐字段：

1. `id`
2. `tag_id`
3. `resource_type`
   - 例如 `DICTIONARY`、`META_WORD`、`DICTIONARY_WORD`
4. `resource_id`
5. `relation_role`
   - 例如 `TAGGED`、`PRIMARY`
6. `created_at`

### 设计目的

该表用于支持你提出的“tag 可以关联字典表、元单词表、字段单词关系表等等”的需求。

本期章节功能并不强依赖该表完成核心流程，但建议一起纳入设计，以免后续再次重构标签系统。

### 本期使用边界

本期主章节归属仍建议以 `dictionary_words.chapter_tag_id` 为主。

原因：

1. 查询简单
2. 唯一约束容易落地
3. 排序和学习计划逻辑更直接
4. 不需要在章节位置查询中每次都额外走一层通用多态关联

`tag_relations` 本期更像标签系统基础设施，为后续 `Dictionary`、`MetaWord` 等资源打通统一标签能力。

## 6.5 `dictionary_words` 表调整

建议新增字段：

1. `chapter_tag_id`
   - 外键指向 `tags.id`
   - 表示当前词条所在的叶子章节
2. `entry_order`
   - 表示该词在该章节中的顺序

建议移除当前唯一约束 `(dictionary_id, meta_word_id)`。

推荐新的约束：

1. 唯一约束：`(dictionary_id, chapter_tag_id, entry_order)`
   - 表示同一本书、同一章节、同一顺序位置只能有一个词条
2. 普通索引：`(dictionary_id, meta_word_id)`
   - 用于单词详情回查和唯一词统计
3. 排序索引：`(dictionary_id, chapter_tag_id, entry_order, id)`

### 为什么不用“只靠 tag_relations”

如果只靠 `tag_relations` 给 `dictionary_word` 绑章节：

1. 词条主章节引用不够明确
2. 很难在数据库层表达“一个词条只能有一个主章节”
3. 排序、唯一约束、学习计划取词都要多一层间接关系

因此本期建议保留 `chapter_tag_id` 这个强业务字段。

## 6.6 `dictionaries` 统计字段设计

建议保留：

1. `word_count`
   - 语义调整为“唯一单词数”

建议新增：

1. `entry_count`
   - 语义为“词条出现总数”

原因：

1. 同一个词可在一本书中出现多次后，唯一词数和词条数不再相等
2. 学习进度本期建议仍按唯一单词推进
3. 浏览和导入统计需要展示词条总量

## 6.7 历史数据迁移策略

推荐新增 migration：

- `V16__add_tag_system_and_dictionary_entry_chapter_support.sql`

迁移建议：

1. 新建 `tags`
2. 新建 `tag_relations`
3. 为 `dictionary_words` 增加 `chapter_tag_id` 和 `entry_order`
4. 为每本已有词书自动创建一个默认根章节，例如“默认章节”
5. 将该词书下所有历史 `dictionary_words` 指向默认章节
6. `entry_order` 按现有 `id` 顺序回填
7. 移除旧唯一约束 `(dictionary_id, meta_word_id)`
8. 建立新的唯一约束和索引
9. 为 `dictionaries` 增加 `entry_count`
10. 将 `entry_count` 初始化为历史 `word_count`

## 7. API 设计

## 7.1 章节标签树接口

新增标签树接口，支持页面“选章节而不是输章节”。

推荐新增：

1. `GET /api/dictionaries/{dictionaryId}/tags/tree?type=CHAPTER`
2. `POST /api/dictionaries/{dictionaryId}/tags`
3. `PATCH /api/tags/{tagId}`
4. `DELETE /api/tags/{tagId}`

### 章节树返回建议

```json
[
  {
    "id": 101,
    "name": "第1单元",
    "type": "CHAPTER",
    "sortOrder": 1,
    "children": [
      {
        "id": 102,
        "name": "第1课",
        "type": "CHAPTER",
        "sortOrder": 1,
        "children": []
      }
    ]
  }
]
```

### 用途

1. 录入页面选章节
2. 词典详情展示章节树
3. 后端校验章节是否属于当前词书

## 7.2 词典词条浏览接口

当前：

- `GET /api/dictionary-words/dictionary/{dictionaryId}/words`

返回的是 `Page<MetaWord>`，无法表达重复词条和章节位置。

推荐新增：

1. `GET /api/dictionary-words/dictionary/{dictionaryId}/entries`
2. `GET /api/dictionary-words/dictionary/{dictionaryId}/entries/search`

推荐字段：

1. `entryId`
2. `dictionaryId`
3. `metaWordId`
4. `word`
5. `translation`
6. `phonetic`
7. `definition`
8. `chapterTagId`
9. `chapterDisplayPath`
10. `entryOrder`

### 排序

按照：

1. `tag.sort_key ASC`
2. `dictionary_words.entry_order ASC`
3. `dictionary_words.id ASC`

## 7.3 写接口与导入接口

### 兼容旧接口

保留以下旧接口：

1. `POST /api/dictionary-words/{dictionaryId}/{metaWordId}`
2. `POST /api/dictionary-words/{dictionaryId}/batch`
3. `POST /api/dictionary-words/{dictionaryId}/words/list`
4. `POST /api/dictionary-words/{dictionaryId}/words/list/v2`
5. `POST /api/dictionary-words/{dictionaryId}/words/import-csv`
6. `POST /api/dictionary-words/{dictionaryId}/words/import-json`

兼容策略：

1. 若未指定章节，则默认落入该词书的“默认章节”
2. `entry_order` 自动追加生成
3. 不再因为 `meta_word` 已存在而忽略新的词条出现

### 推荐新增结构化词条接口

1. `POST /api/dictionary-words/{dictionaryId}/entries/batch`
2. `POST /api/dictionary-words/{dictionaryId}/entries/import-json`
3. `POST /api/dictionary-words/{dictionaryId}/entries/import-csv`

推荐写入 DTO 中显式传：

1. 单词元数据
2. `chapterTagId`
3. `entryOrder`

页面层优先使用 `chapterTagId` 选取章节，避免手填路径。

## 7.4 单词详情回显接口

保留现有：

- `GET /api/meta-words/{id}`

新增：

- `GET /api/meta-words/{id}/detail`

推荐响应结构：

```json
{
  "id": 1001,
  "word": "abandon",
  "translation": "放弃",
  "dictionaryReferences": [
    {
      "dictionaryId": 10,
      "dictionaryName": "高中核心词汇",
      "locations": [
        {
          "chapterTagId": 102,
          "chapterDisplayPath": "第1单元 > 第1课",
          "entryOrder": 5
        },
        {
          "chapterTagId": 205,
          "chapterDisplayPath": "第3单元 > 第2课",
          "entryOrder": 8
        }
      ]
    }
  ]
}
```

### 权限规则

单词详情中的关联辞书回显必须遵循现有词典权限：

1. 管理员可见全部
2. 教师仅可见有权限查看的词书
3. 学生仅可见系统词书或已分配给自己的词书

## 8. 服务与仓储设计

## 8.1 Repository 层

### `TagRepository`

建议新增：

1. 根据 `dictionaryId + type` 查询整棵树
2. 根据 `parentId` 查询子节点
3. 根据 `dictionaryId + parentId + sortOrder` 校验冲突
4. 根据 `dictionaryId + pathKey` 查询节点

### `TagRelationRepository`

建议新增：

1. 通用标签绑定查询
2. 根据资源类型/资源 ID 查询标签
3. 根据标签反查资源

### `DictionaryWordRepository`

建议新增或调整：

1. `findByDictionaryIdOrderByChapterSortKeyAscEntryOrderAscIdAsc`
2. `countDistinctMetaWordIdByDictionaryId`
3. 结构化分页查询，join `tags`
4. 单词详情回显查询，join `dictionary_words + tags + dictionaries`

### `DictionaryRepository`

建议增加：

1. `entryCount` 更新或重算能力
2. 唯一词数/词条数统一重算能力

## 8.2 Service 层

### `TagService`

建议新增，职责包括：

1. 维护章节树
2. 校验章节节点是否属于某本词书
3. 维护 `path_name`、`path_key`、`sort_key`
4. 删除或移动节点时做层级校验

### `DictionaryWordService`

本次改造的核心服务，职责建议包括：

1. 词条创建和导入
2. 词条列表查询
3. 章节归属校验
4. 统计字段重算

核心变更：

1. 从“唯一关联服务”升级为“词条服务”
2. 批量插入时不再按 `meta_word_id` 去重
3. 改为按 `chapter_tag_id + entry_order` 判定位置冲突
4. 排序逻辑基于章节树顺序

### `MetaWordService`

建议新增单词详情聚合能力：

1. 读取 `MetaWord`
2. 查询该词的所有 `DictionaryWord`
3. join `Tag`
4. join `Dictionary`
5. 做权限过滤
6. 聚合为 `MetaWordDetailResponse`

### `DictionaryService`

建议新增或调整：

1. 维护 `word_count`
2. 维护 `entry_count`
3. 需要时支持词书级章节根节点初始化

### `AccessControlService`

现有词典权限模型可复用。

单词详情回显时：

1. 先查到所有词条所属词书
2. 对每本词书调用既有可见性规则
3. 仅返回当前用户可见的词书位置

## 8.3 DTO 设计建议

建议新增：

1. `TagTreeNodeResponse`
2. `CreateTagRequest`
3. `UpdateTagRequest`
4. `DictionaryWordEntryResponse`
5. `DictionaryWordEntryImportDto`
6. `AddDictionaryEntriesRequest`
7. `MetaWordDictionaryReferenceDto`
8. `MetaWordReferenceLocationDto`
9. `MetaWordDetailResponse`

## 9. 学习计划与统计设计

## 9.1 学习计划排序逻辑

当前新词顺序基于 `dictionary_words.id ASC`。

建议改为：

1. `tags.sort_key ASC`
2. `dictionary_words.entry_order ASC`
3. `dictionary_words.id ASC`

## 9.2 重复词条与学习进度

本期建议保持：

1. `StudyWordProgress` 仍按 `meta_word_id` 跟踪
2. 学习任务生成时按词条顺序遍历
3. 遇到已存在进度的 `meta_word_id` 时，不再把该词作为“新词”重复发放

这意味着：

1. 书内词条结构完整保留
2. 学习层面不强制重复学习同一词
3. 后续章节再次出现时可显示为已学习

## 9.3 进度分母

建议：

1. 学习进度分母使用唯一单词数
2. 词书页面可同时显示“唯一单词数 / 词条数”

当前 `StudyPlanService` 相关逻辑需要从“词条总数”改为“distinct meta_word_id 数量”或 `dictionary.wordCount`。

## 9.4 学习任务是否要记录章节节点

当前 `StudyDayTaskItem` 只有 `meta_word_id`，没有 `dictionary_word_id` 或 `chapter_tag_id`。

### 本期建议

本期先不强制修改 `study_day_task_items`。

原因：

1. 当前学习进度仍按唯一单词推进
2. 当前需求没有强制要求学习任务页精确展示“它来自哪一课”

### 二期增强

如果后续希望学习任务页显示“这个词来自第几单元第几课”，再考虑：

1. 为 `study_day_task_items` 增加 `dictionary_word_id`
2. 学习任务接口回显 `chapterTagId` 和 `chapterDisplayPath`

## 10. 导入设计

## 10.1 导入输入规范

结构化导入建议显式传：

1. 单词信息
2. `chapterTagId`
3. `entryOrder`

### JSON 导入

推荐优先支持 JSON，因为天然适合结构化字段。

### CSV 导入

CSV 不建议让用户直接填写章节路径文字。

推荐两种方式：

1. 先在页面选择词书和章节树，再导入该章节下的词表
2. 导入工具先把章节名称映射为已有 `chapterTagId` 再提交

这样可以最大程度避免录入错误。

## 10.2 去重规则

导入规则建议改为：

1. `MetaWord` 可复用
2. 同一本词书中，相同 `chapter_tag_id + entry_order` 视为重复位置
3. 相同词但不同章节或不同顺序，允许重复录入

## 10.3 导入结果统计

建议回显：

1. `totalRows`
2. `reusedMetaWords`
3. `createdMetaWords`
4. `createdEntries`
5. `duplicatePositions`
6. `failedRows`

## 11. 代码级改动清单

本节用于实现前人工审核。

### 11.1 数据库与配置

需要修改或新增：

1. `src/main/resources/db/migration/V16__add_tag_system_and_dictionary_entry_chapter_support.sql`
   - 新建 `tags`
   - 新建 `tag_relations`
   - 为 `dictionary_words` 增加 `chapter_tag_id`、`entry_order`
   - 调整唯一约束和索引
   - 为 `dictionaries` 增加 `entry_count`
2. `src/main/resources/application.yml`
   - 注册新 migration
3. `src/main/java/com/example/words/config/DatabaseInitializer.java`
   - 注册新 migration

### 11.2 Model 层

需要新增或修改：

1. `src/main/java/com/example/words/model/DictionaryWord.java`
   - 增加 `chapterTagId`、`entryOrder`
2. `src/main/java/com/example/words/model/Dictionary.java`
   - 增加 `entryCount`
3. `src/main/java/com/example/words/model/Tag.java`
4. `src/main/java/com/example/words/model/TagRelation.java`
5. `src/main/java/com/example/words/model/TagType.java`
6. `src/main/java/com/example/words/model/TagResourceType.java`
7. 视实现需要增加 `TagRelationRole.java`

### 11.3 Repository 层

需要新增或修改：

1. `src/main/java/com/example/words/repository/DictionaryWordRepository.java`
   - 增加章节排序查询
   - 增加结构化分页查询
   - 增加单词详情回显查询
   - 增加 distinct 统计
2. `src/main/java/com/example/words/repository/DictionaryRepository.java`
   - 增加 `entryCount` 的维护能力
3. `src/main/java/com/example/words/repository/TagRepository.java`
4. `src/main/java/com/example/words/repository/TagRelationRepository.java`

### 11.4 DTO 层

建议新增：

1. `src/main/java/com/example/words/dto/TagTreeNodeResponse.java`
2. `src/main/java/com/example/words/dto/CreateTagRequest.java`
3. `src/main/java/com/example/words/dto/UpdateTagRequest.java`
4. `src/main/java/com/example/words/dto/DictionaryWordEntryResponse.java`
5. `src/main/java/com/example/words/dto/DictionaryWordEntryImportDto.java`
6. `src/main/java/com/example/words/dto/AddDictionaryEntriesRequest.java`
7. `src/main/java/com/example/words/dto/MetaWordDictionaryReferenceDto.java`
8. `src/main/java/com/example/words/dto/MetaWordReferenceLocationDto.java`
9. `src/main/java/com/example/words/dto/MetaWordDetailResponse.java`

### 11.5 Controller 层

需要新增或修改：

1. `src/main/java/com/example/words/controller/TagController.java`
   - 章节树查询与维护
2. `src/main/java/com/example/words/controller/MetaWordController.java`
   - 新增 `GET /api/meta-words/{id}/detail`
3. `src/main/java/com/example/words/controller/DictionaryWordController.java`
   - 新增结构化 entries 查询接口
   - 新增结构化导入接口
   - 保留旧接口兼容

### 11.6 Service 层

需要新增或修改：

1. `src/main/java/com/example/words/service/TagService.java`
   - 维护标签树和路径键
2. `src/main/java/com/example/words/service/DictionaryWordService.java`
   - 从唯一关联服务升级为词条服务
   - 支持章节选择、位置去重、词条排序
3. `src/main/java/com/example/words/service/MetaWordService.java`
   - 增加单词详情聚合能力
4. `src/main/java/com/example/words/service/DictionaryService.java`
   - 扩展 `entryCount` 维护
5. `src/main/java/com/example/words/service/StudyPlanService.java`
   - 新词顺序改为章节树顺序
   - 进度分母改为唯一词数
6. `src/main/java/com/example/words/service/CsvImportService.java`
   - 导入逻辑改为章节感知

### 11.7 测试

需要新增或修改：

1. `src/test/java/com/example/words/service/DictionaryWordServiceTest.java`
   - 增加“同词不同章节可重复录入”
   - 增加“同章节按 entryOrder 排序”
2. `src/test/java/com/example/words/service/StudyPlanServiceTest.java`
   - 增加“按章节树顺序发词”
   - 增加“重复词条不重复生成新进度”
3. 建议新增 `src/test/java/com/example/words/service/MetaWordServiceTest.java`
   - 校验单词详情的关联辞书回显
   - 校验权限过滤
4. 建议新增 `src/test/java/com/example/words/service/TagServiceTest.java`
   - 校验章节树路径和排序键维护
5. 建议新增 controller 层测试或集成测试
   - 校验标签树接口
   - 校验结构化词条接口

## 12. 推荐实施顺序

建议按以下顺序实施：

1. 先做 migration 和 `Tag` / `DictionaryWord` 模型改造
2. 再做 `TagService` 和章节树接口
3. 再做 `DictionaryWordService` 的章节感知改造
4. 再做 `MetaWordController` 的单词详情回显接口
5. 最后修改 `StudyPlanService` 的排序与统计逻辑

这样可以先让“章节树可维护、词条可归属”跑通，再逐步接入下游逻辑。

## 13. 主要风险与审查点

实施前建议重点审查以下问题：

1. `chapter_tag_id` 与 `tag_relations` 的边界是否达成共识
2. `tags.dictionary_id` 是否足以表达章节树归属
3. 同词重复出现时，前端是否理解“词条数”和“唯一词数”的区别
4. 默认章节策略是否符合旧接口兼容预期
5. 标签树移动节点时，`path_name`、`path_key`、`sort_key` 的重算代价是否可接受
6. 单词详情关联辞书回显是否会产生性能问题

## 14. 结论

基于“章节应当可维护、可选取、可复用”的思路，本次设计从原先的“路径内嵌到 `dictionary_words`”调整为“独立 `tags` 树 + 词条引用章节节点”的方案。

推荐方案是：

1. 新增 `tags` 表，维护层级章节树
2. `dictionary_words` 增加 `chapter_tag_id` 和 `entry_order`
3. 移除 `(dictionary_id, meta_word_id)` 唯一约束，允许同词在同一本词书中多次出现
4. 新增 `tag_relations`，为字典、元单词、词条等资源的通用标签能力打基础
5. 单词详情通过新聚合接口回显关联辞书及其章节位置
6. 学习计划本期仍按唯一单词推进，但发词顺序改为章节树顺序

该方案更符合你提出的“页面选章节、避免写错、标签体系可扩展”的方向，也更适合后续继续演进章节树和其他标签能力。
