# Flyway 切换建议

## 目标

本文档给出当前项目切换到 Flyway 的推荐方案。

已知前提：

1. 项目还没有正式上线。
2. 旧数据库只在测试环境使用过。
3. 历史数据没有保留价值。

基于这个前提，推荐方案不再是 `baseline 接管旧库`，而是直接把历史 SQL 全量整理进 Flyway，让 Flyway 成为唯一数据库变更来源。

## 当前问题

当前项目的数据库初始化存在以下问题：

1. 同时存在两套执行机制：
   - `spring.sql.init`
   - 自定义 `DatabaseInitializer`
2. 两套机制都在手工维护 SQL 清单。
3. 两套机制都允许 `continue-on-error`。
4. 当前历史 SQL 链本身不规范：
   - 存在两个 `V7`
   - `V6` 和 `V12` 没被当前清单纳入执行
5. 当前做法更像“启动时反复尝试跑脚本”，而不是正式管理数据库版本历史。

结论：既然没有生产历史包袱，最优处理不是兼容旧方案，而是一次性收口到 Flyway。

## 推荐总策略

推荐采用 `全量历史迁入 Flyway + 停用旧初始化机制` 的方案。

推荐结论如下：

1. 把当前 `db/migration` 下的历史 SQL 全部纳入 Flyway 管理。
2. 重排重复或不规范的版本号，保证所有 migration version 唯一。
3. 让 Flyway 从 `V1` 开始管理完整历史，而不是只接管未来变更。
4. 删除 `spring.sql.init` 和 `DatabaseInitializer`，避免双轨执行。
5. 从切换完成后的下一版开始，继续在同一条 Flyway 版本链上追加 migration。

## 必要决策与推荐选择

### 1. 历史 SQL 是否全部迁入 Flyway

推荐选择：全部迁入。

原因：

1. 没有生产历史数据需要兼容。
2. 现在就是清理历史链的最佳时机。
3. 以后新环境可以直接从空库完整初始化，不再依赖手工清单。

### 2. 重复版本号如何处理

推荐选择：使用 Flyway 支持的小版本号重排。

推荐示例：

1. `V7__ensure_dictionary_words_unique_constraint.sql`
   改为 `V7.1__ensure_dictionary_words_unique_constraint.sql`
2. `V7__create_exam_tables.sql`
   改为 `V7.2__create_exam_tables.sql`

原则：

1. 所有版本号必须唯一。
2. 执行顺序必须符合真实依赖关系。
3. 后续不再修改已进入正式链路的版本号。

### 3. 漏执行的历史脚本如何处理

推荐选择：纳入正式 Flyway 执行链。

当前重点：

1. `V6__drop_old_columns.sql`
2. `V12__add_meta_word_lower_index.sql`

说明：

1. 即使 `V6` 当前基本是空操作，也应保留在历史链中。
2. `V12` 属于真实 schema 优化，应进入正式迁移顺序。

### 4. 旧初始化机制如何处理

推荐选择：同一版内全部下线。

需要下线的内容：

1. `spring.sql.init`
2. `DatabaseInitializer`

原因：

1. Flyway 接管后，不应再存在第二套 schema 初始化入口。
2. 否则容易产生重复执行和排障困难。

### 5. 测试环境如何处理

推荐选择：区分单元测试与数据库迁移验证。

推荐方案：

1. 现有 H2 单元测试先保留，不强行让 H2 执行 PostgreSQL 风格的 Flyway SQL。
2. 真正验证 Flyway 迁移时，新增 PostgreSQL/Testcontainers 集成测试。

原因：

1. 当前 SQL 使用了 PostgreSQL 方言。
2. H2 不适合承担 Flyway 历史链的真实验证。

### 6. 空库初始化策略如何处理

推荐选择：切换后以 Flyway 历史链作为唯一空库初始化方案。

结果：

1. 新数据库从空库启动时，由 Flyway 依次执行 `V1` 到最新版本。
2. 不再依赖 `schema-locations` 的手工脚本清单。

## 推荐实施路径

### 阶段一：整理历史迁移链

目标：把现有脚本整理成 Flyway 可直接执行的唯一版本链。

建议动作：

1. 保留 `src/main/resources/db/migration` 作为 Flyway 目录。
2. 修正重复版本号。
3. 把漏掉的 `V6` 和 `V12` 纳入链路。
4. 检查每个历史脚本的先后依赖是否合理。
5. 确保所有脚本在空 PostgreSQL 数据库上可完整执行。

本阶段预期结果：

1. 从空库可以完整跑通全部历史 SQL。
2. 迁移顺序和仓库文件一一对应。

### 阶段二：切换应用初始化入口

目标：让 Flyway 成为唯一数据库变更入口。

建议动作：

1. 引入 Flyway 依赖。
2. 关闭 `spring.sql.init`。
3. 删除或停用 `DatabaseInitializer`。
4. 启用 Flyway 自动迁移。

本阶段预期结果：

1. 应用启动时只通过 Flyway 管理 schema。
2. 不再重复执行旧初始化逻辑。

### 阶段三：补充迁移验证

目标：保证迁移链长期可用。

建议动作：

1. 增加基于 PostgreSQL 的迁移验证测试。
2. 至少验证“空库启动可跑通当前全部 migration”。
3. 后续新 migration 合入前都应经过这类验证。

## 不建议的做法

以下做法不建议采用：

1. 保留 `spring.sql.init` 作为 Flyway 的兜底。
2. 保留 `DatabaseInitializer` 作为双保险。
3. 在已经决定全量接管历史的前提下，再保留 `baseline` 方案作为主路径。
4. 继续使用 `continue-on-error` 思路掩盖迁移失败。
5. 让 H2 直接承担 PostgreSQL Flyway 历史链验证。

## 切换完成后的规则

切换完成后，建议执行以下规则：

1. 所有数据库变更必须新增 Flyway migration。
2. 不允许修改已进入正式链路的历史 migration。
3. 失败修复优先通过新增 migration 向前修复。
4. 大的数据修复和结构变更尽量拆分。
5. 新版本号必须唯一，避免再次出现重号。

## 最终建议

如果目标是“在当前项目状态下，以最优方式切到 Flyway”，推荐最终方案如下：

1. 直接把历史 SQL 全量迁入 Flyway 管理。
2. 使用小版本号修正重复版本，例如 `V7.1`、`V7.2`。
3. 保留并纳入 `V6`、`V12` 等之前漏执行的脚本。
4. 停用 `spring.sql.init` 和 `DatabaseInitializer`。
5. 用 PostgreSQL 环境验证整条历史迁移链能从空库跑通。

这比 `baseline 接管` 更干净，也更适合当前“未正式上线、历史数据可放弃”的状态。

## 具体代码改动方案

本节给出基于当前仓库结构的具体改动建议。

### 1. `pom.xml`

建议改动：

1. 新增 `org.flywaydb:flyway-core`
2. 保留 PostgreSQL 驱动
3. 不引入 Liquibase

推荐原因：

1. Spring Boot 可以直接自动装配 Flyway。
2. 当前项目不需要引入第二套迁移框架。

### 2. `src/main/resources/db/migration`

建议改动：

1. 保留当前目录作为 Flyway 正式迁移目录。
2. 调整重复版本号，保证唯一。
3. 保留全部历史文件，不拆到新目录。
4. 确保目录内文件名符合 Flyway 命名规范。

建议重排方向：

1. `V1__init_word_vocabulary.sql`
2. `V2__create_dictionary_tables.sql`
3. `V3__add_dictionary_creation_type.sql`
4. `V4__add_jsonb_fields_to_meta_words.sql`
5. `V5__migrate_data_to_jsonb.sql`
6. `V6__drop_old_columns.sql`
7. `V7.1__ensure_dictionary_words_unique_constraint.sql`
8. `V7.2__create_exam_tables.sql`
9. `V8__add_selected_option_to_exam_questions.sql`
10. `V9__add_user_role_support.sql`
11. `V10__add_classroom_support.sql`
12. `V11__add_famous_quotes.sql`
13. `V12__add_meta_word_lower_index.sql`
14. `V13__add_famous_quote_translations.sql`
15. `V14__add_study_plan_support.sql`
16. `V15__deduplicate_study_plan_data_and_add_constraints.sql`
17. `V16__add_tag_system_and_dictionary_entry_chapter_support.sql`

说明：

1. 这里的顺序基于当前文件依赖关系。
2. 真正修改前，仍应再核对一次每个脚本的前后依赖。

### 3. `src/main/resources/application.yml`

建议改动：

1. 关闭 `spring.sql.init`
2. 启用 `spring.flyway`
3. 让 Flyway 扫描默认目录 `classpath:db/migration`
4. 删除旧的手工脚本清单

推荐配置方向：

```yml
spring:
  sql:
    init:
      mode: never

  flyway:
    enabled: true
    locations: classpath:db/migration
```

建议同时删除或停用以下内容：

1. `spring.sql.init.schema-locations`
2. `spring.sql.init.continue-on-error`

### 4. `src/main/java/com/example/words/config/DatabaseInitializer.java`

建议改动：

1. 直接删除

推荐原因：

1. 它已经与 Flyway 的职责重复。
2. 保留它只会制造双轨执行。

如果短期内不想删除文件，至少要做到：

1. 默认关闭自动装配
2. 所有环境都不再启用它

但从维护角度看，直接删除更干净。

### 5. `src/main/resources/application-test.yml`

建议改动：

这里分两种情况。

方案 A：短期内保留现有 H2 单元测试

1. 显式关闭 Flyway：

```yml
spring:
  flyway:
    enabled: false
```

2. 保留 `ddl-auto: create-drop`

适用场景：

1. 本次切换只想先收口生产代码路径
2. 暂时不调整测试技术栈

方案 B：补 PostgreSQL/Testcontainers 集成测试

1. 新增专门的 PostgreSQL 集成测试配置
2. 在该链路中开启 Flyway
3. 验证空库迁移

推荐结论：

1. 首次切换时，先采用方案 A
2. 紧接着补方案 B

### 6. 首次改动的建议范围

首个改动批次建议只做以下内容：

1. `pom.xml`
   - 增加 Flyway 依赖
2. `src/main/resources/db/migration`
   - 修正版本号
   - 保留全部历史 SQL
3. `src/main/resources/application.yml`
   - 关闭 `spring.sql.init`
   - 开启 Flyway
4. `src/main/java/com/example/words/config/DatabaseInitializer.java`
   - 删除
5. `src/main/resources/application-test.yml`
   - 短期先关闭 Flyway

首次改动不建议混入：

1. 新业务表结构改动
2. 大规模测试重构
3. 其他无关配置调整

### 7. 推荐的提交拆分

建议拆成两次提交。

第一次提交：

1. 引入 Flyway
2. 整理历史 migration 文件名
3. 关闭旧初始化机制
4. 调整配置

第二次提交：

1. 增加 PostgreSQL/Testcontainers 迁移验证
2. 跑通空库迁移测试

这样做的好处：

1. 可以先完成主切换。
2. 再单独补充验证链路。
3. 出问题时更容易定位是迁移接管问题还是测试接入问题。
