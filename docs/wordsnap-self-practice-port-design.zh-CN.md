# WordSnap 功能移植设计：学生自主练习（自测/错题/识词/记忆统计）并入 word-backend 学生端

> 文档状态：设计稿（待评审）
> 移植源：`WordSnap`（Flutter 学习应用 MVP，拍照识词 → 生成考试 → 完成考试 → 记忆分析 → 错题巩固 闭环，数据全部本地 `shared_preferences` 持久化）
> 移植目标：`word-backend` 学生端（Web React 学生工作台 + Spring Boot 3.1.8 后端）
> 关联文档：`docs/superpowers/specs/2026-06-21-student-post-login-requirements-design.md`、`docs/superpowers/specs/2026-06-22-student-word-memory-design.md`、`docs/superpowers/specs/2026-06-22-student-workspace-first-slice-design.md`、`docs/requirements/exam-question-paper-requirements.zh-CN.md`、`docs/desin-ai-import-imag.zh-CN.md`、`docs/study-plan-design.md`、`CONTEXT.md`、`AGENTS.md`

---

## 1. 背景与目标

### 1.1 移植源现状（WordSnap，`D:\code\dsh-home\WordSnap`）

WordSnap 是一个 Flutter 客户端 MVP（`0.1.9+10`），实现并**全部本地持久化**的闭环功能：

1. **拍照识词**：拍照/相册 → 裁剪 → 视觉大模型 OCR（火山 `Doubao-vision` / DeepSeek）→ 结构化词条（word + pos + meaning + confidence）→ 清洗去重 → 生成“识别批次”（`RecognitionCapture`）。
2. **考试生成**：出题范围 = 本次识别 / 默认词本 / 内置词书 / 复习队列；范围内每词一题，题干=英文词、选项=中文释义，单选；干扰项取自同范围词义池，不足时用「我不会/我不知道/我不认识/没学过/不确定」兜底标签补齐。
3. **答题与判分**：4 或 9 选项、单点/双点确认、单人/双人（同屏）；选中兜底标签视为“待巩固”（不算错、不计成绩），判分为 correct/wrong/skipped 三类。
4. **记忆与错题**：每次考试按词更新 4 桶（mastered/fuzzy/uncertain/unseen）展示与“已考 N 次”；**错题（含待巩固）自动加入复习队列**；复习队列=错词集合，可再作为出题范围重考。
5. **统计与分析**：正确率、4 桶分布环形图、学习建议文案、最近学习流。
6. **不适用服务端**：应用内升级（Android）、同屏双人、成绩卡截图分享、深浅主题、引导页、客户端自持 OCR API Key——详见 §9 边界。

WordSnap 侧**不存在**任何服务端；复习队列无排期算法（错词集合语义），记忆是简单的“最近一次判定”4 桶，非间隔重复。

### 1.2 移植目标（产品融合）

用户决策：**产品融合**——不把 WordSnap 做成独立 App 后端，而是把“个人自主练习闭环”作为新模块并入 word-backend 现有学生端产品，与已有“学习计划（今日任务）”“正式考试/试卷”并列；学生数据（词书、记忆、成绩历史）统一走服务端，客户端不再本地存储学习状态。

### 1.3 目标与非目标

**目标**

- G1 学生在 Web 学生工作台可随时从「已分配词书 / 错词 / 收藏词」发起**自测**，答完即出成绩、分析、错题明细。
- G2 自测结果**自动回写全局卡片盒记忆与自动错词本**（错词=复习队列），自测**不计入老师评价**，不与学习计划进度耦合。
- G3（二期）学生上传/拍摄单词材料图片 → 服务端识词 → 预览确认 → 词条进入练习池。
- G4（三期）学生侧 30 天统计、记忆分布与学习建议、收藏词复习、偏好服务端化。
- G5 全程遵循仓库现有术语（`CONTEXT.md`）、硬规则（Flyway 只前向、新外键禁级联、单后台实例规则、幂等/乐观锁、`@PreAuthorize` 后端校验）。

**非目标（本期）**

- 不做老师视角的任何新报表；自测数据对老师不可见、不计入任何正式评价统计。
- 不复制 WordSnap 的本地存储模型、同屏双人模式、客户端 AI Key、应用内升级、音效/触觉、成绩卡分享。
- 不改动 V39 试卷流（老师域）与遗留正式考试（FORMAL）的既有行为。

### 1.4 设计原则（来自仓库既有约定）

1. **术语对齐**：使用 `CONTEXT.md` 词汇（辞书/词书、学习计划、学生作答记录、自动错词本、卡片盒等）；不引入与既有含义冲突的新词。
2. **轨道分治**：正式考试（老师评价）与自测（学生个人练习）存储分型、统计分轨（沿用 2026-06-21 spec 的 `exam_type` 决策），老师统计接口只查 FORMAL。
3. **复用优先**：记忆引擎、错词/收藏查询、AI 网关、发音、幂等提交模式、异常体系全部复用，不新建平行设施。
4. **语义归位**：WordSnap 的“4 桶/复习队列/不确定”等客户端概念，**映射**到服务端既有卡片盒/`autoWrong`/`SKIPPED` 语义（见 §3），不在服务端再造一套并行记忆模型。

---

## 2. 现状基线摘要

### 2.1 word-backend 与“自测闭环”相关的已有能力（复用的地基）

| 领域 | 现状 | 复用点 |
|---|---|---|
| 用户/角色 | JWT + `@PreAuthorize`；ADMIN/TEACHER/STUDENT 单一主角色 | 自测全部 `STUDENT` 权限 |
| 词书 | `dictionaries`→`meta_words`（`uk_meta_words_normalized_word` 唯一）→`dictionary_words`；学生可见=直接/班级分配（`dictionary_assignments`、`classroom_dictionary_assignments`） | 自测词源与资格校验 |
| 记忆 | `student_word_memories`（(student,meta_word) 唯一；`box_level 0..6`、`auto_wrong`、`mastery_level`、`next_review_date`、`last_result/last_source`）；`REVIEW_INTERVALS=[0,1,2,4,7,15,30]`；CORRECT→box+1、否则 box−1 且 `auto_wrong=true`、box≥5 且连对≥3 才清错词；每次更新写 `student_word_memory_events` | 自测结果回写入口（需泛化，见 §5.3） |
| 错词/收藏 | `GET /students/me/wrong-words|favorite-words`（= `auto_wrong=true`/`favorite=true` 的记忆）；收藏仅限分配词书词 | “错词/收藏”自测范围的数据源 |
| 遗留考试 | `exams` + `exam_questions`（快照 4 选项 A–D、`correct_option`/`selected_option`、`status GENERATED/SUBMITTED`、`score=round(correct/question_count×100)`），创建限 ADMIN/TEACHER | 按 2026-06-21 spec 扩展为自测存储 |
| 正式试卷 | V39 `question_bank…paper_releases…student_paper_attempts` 双层快照、老师域 | **不复用**（自测不进老师评价） |
| 学生聚合 | `GET /students/me/assessments/pending|history` 统一 LEGACY_GENERATED_EXAM + PAPER_RELEASE_ATTEMPT | 新增自测来源枚举，历史可聚合展示 |
| AI | `ai_configs`（按用户，`is_default`）+ `AiGatewayService.generateText`（OpenAI 兼容，文本） | 识词=视觉调用（需扩展，见 §6.3） |
| 图片 AI 导入 | 老师/管理员“试题图片→默认视觉 AI 配置→预览→确认入库”模式（`desin-ai-import-imag.zh-CN.md`） | 学生识词流程参照其“上传→识别→预览确认”骨架 |
| 幂等/可靠性 | `study_records.request_key` 幂等重放 + 409；试卷提交 `version` 乐观锁；提交后不可就地改 | 自测提交采用同款“状态迁移+幂等键” |
| 发音/音节 | `word-pronunciation-design.zh-CN.md` 落地（uk/us URL > 有道 dictvoice > TTS）；学生端已有发音播放 | 答题页发音面板复用；**自测答题界面不得展示音节文字**（2026-06-21 syllable spec） |

### 2.2 缺口（本文档要补齐的）

1. 学生**自测创建入口**：`POST /api/exams` 目前限 ADMIN/TEACHER，无学生自建、无范围/题量概念。
2. 记忆写入口只有 `recordPlanStudy`（PLAN_STUDY）；`StudentWordMemorySourceType` 已含 SELF_STUDY/FORMAL_EXAM/SELF_PRACTICE 但无写路径。
3. 错词/收藏只有只读列表，**没有“从错词直接出题重考”的闭环**。
4. 后端零 OCR/图片识词端点。
5. 学生侧无跨来源（计划/自测/考试）30 天统计与记忆分布分析接口。
6. 前端学生工作台无“自测/我的练习”页面族。

---

## 3. 语义映射与关键决策（ADR）

### 3.1 WordSnap 概念 → word-backend 概念映射

| WordSnap（客户端） | word-backend（服务端） | 说明 / 决策 |
|---|---|---|
| 识别批次 `RecognitionCapture` | `student_recognition_batches` + `student_recognition_items`（新表，§6） | 服务端保存批次与逐词确认状态，图片留存有限期 |
| 默认词本 `WordBook`（个人累积） | 已分配词书（直接或班级分配） | 本期**不做**“学生个人任意加词”的私有词书；个人词条累积能力=自测错词/收藏（由记忆行承载）。见开放问题 OQ-2 |
| 内置词书 | 服务端词书（学生可见范围=已分配） | 无需客户端内置词书 |
| 出题范围 recognized/wordBook/builtInBook/reviewQueue | `practice_scope ∈ {DICTIONARY, REVIEW_QUEUE, FAVORITES, RECOGNITION}` | 服务端新增枚举列；本期 DICTIONARY/REVIEW_QUEUE/FAVORITES，RECOGNITION 随 M2 启用 |
| 4 桶 mastered/fuzzy/uncertain/unseen（展示用，按最近一次判定） | 服务端**不新增** 4 桶字段；展示分布由卡片盒状态推导 | mastered=box≥5 且非 auto_wrong；fuzzy=`auto_wrong=true`；uncertain=有记忆但 box=0 且非错（多为 SKIPPED 所致）；unseen=无记忆行。阈值收敛为常量，见 §7.2 |
| “已考 N 次”`examCount` | 记忆事件计数（`student_word_memory_events` 按词聚合）或练习历史统计 | 由服务端统计得出，不落“词→次数”客户端式字段 |
| 复习队列（= 错题集合，**无排期**） | `auto_wrong=true` 的记忆行（服务端已有“自动错词本”语义） | 完全对齐：**自测答错/跳过 ⇒ 自动入错词本**；错词即复习队列。间隔复习调度仍由学习计划（StudyDayTask）承担，自测侧二期（M3）可加“到期错词提醒” |
| 兜底标签「我不会/不确定/没学过…」= 不确定（不算错） | `SKIPPED` 语义（`StudyRecordResult.SKIPPED`） | 服务端按 2026-06-22 spec：**SKIPPED 对长期记忆按错误处理**（box−1、计错词），但**不计入本轮正确/错误统计**、单独显示“待巩固/跳过”。与计划任务“SKIPPED=完成”语义分层不冲突 |
| 判分 correct/wrong/skipped | `StudyRecordResult.{CORRECT, INCORRECT, SKIPPED}` | 复用 |
| 识别词资格 | 已解析到 `meta_words` 且**属于学生已分配词书**的词才可出题 | 词书外/无释义词在确认页展示但不入题（见 §3.4 决策 D3 与 §6.3） |
| 单次考试设置（4/9 选项、双点、单人/双人） | 服务端仅落 4 选项单选 + 按需跳过 | Web 融合产品统一 4 选项；同屏双人属移动端特性不做；**9 选项已定案不做（OQ-1 已决）** |

### 3.2 决策 D1：自测存储轨道 —— 扩展遗留 `exams`（采纳 2026-06-21 定稿）

- **决策**：自测会话存储**复用 `exams` + `exam_questions`**，按 2026-06-21 spec 增加 `exam_type`（FORMAL/SELF_PRACTICE）与 `created_by_student_id`；**不新建独立自测域表**，也**不走 V39 paper 流**。
- 依据：2026-06-21 spec 明确“扩展 Exam 增加类型字段，本设计不新增独立自测表，以复用现有题目、提交和结果能力”；自测与遗留 quick-quiz 同为“词→释义快照卷”，模型同构；`exam_questions` 已具备选项/作答/正确列，最小扩展即可承载。
- V39 试卷流是老师域（发布/冻结/评价），`exam-question-paper-requirements.zh-CN.md` 要求新试卷流不复用旧表、自测不进老师评价——因此自测**不进** paper 域，留在 exams 域但**用 `exam_type` 分轨**，老师端所有查询只过滤 FORMAL，天然隔离。
- 备选（否决）：新建 `student_practice_sessions` 系列表——与已定稿 spec 冲突、且自测需要的历史/聚合需另接一条轨道；否决理由记录于此，若未来自测模型（多题型/大题/变体）显著偏离快照卷模型再复议。

> **评审补强（Q1 已决）**
> - **行所有权契约**：SELF_PRACTICE 行 `created_by_student_id` = 当前学生、`created_by_user_id` = NULL、`target_user_id` = 当前学生（使既有“按学生归属”查询天然可见自测，与 2026-06-21 spec 的“正式考试和自测都能进入学生个人历史”一致）。
> - **隔离审计表**：`ExamRepository.findStudentAssessments`、`findByStatusOrderBySubmittedAtDescCreatedAtDesc`、`findByDictionaryIdAndStatusOrderBySubmittedAtDescCreatedAtDesc`，及 `ExamController` 的 `GET /api/exams/history`、`GET /api/exams/{examId}`、`POST /api/exams/{examId}/submit`、`GET /api/exams/{examId}/result` 逐点复核：历史/聚合视图按需包含或排除 SELF_PRACTICE；**旧面 `POST /api/exams`（建卷，仅 ADMIN/TEACHER）与 FORMAL 行语义、判分/提交行为一律保持 FORMAL-only 不变**；SELF_PRACTICE 行只能经新 `/api/students/me/practices` 面访问与变更。
> - **退出通道**：若未来自测需多题型/大题/变体等显著偏离 quick-quiz 快照模型，按本 ADR 迁移至独立自测域表，本文档不为此预留列。

### 3.3 决策 D2：出题/判分语义以服务端为准，客户端只做呈现

- 服务端生成**随机题目快照**（选项落库、正确项字母化），提交按快照判分；客户端不参与判分。
- “不确定/跳过”通过**选项文本命中兜底标签集合**或**缺省未答**识别，映射 `SKIPPED`（§5.5）。
- 自测创建即生成完整快照卷，断点/重进只做“继续作答同一快照”，不做重新抽题。

### 3.4 决策 D3：词汇资格规则

自测可练习词 = 满足**其一**：

- 属学生**直接或班级分配词书**的 `meta_words`（含自测/计划/考试产生记忆的词）；
- （M2 起）学生本人识别批次中已解析且属于已分配词书范围的词。

收藏词资格保持既有限制（仅分配词书词）。**词书外识别词与未解析词定案（评审 Q3 已决）：不入题、不写记忆、不可收藏**；识别确认页对“词书外/未解析”词展示原因并禁用勾选，仅作为个人识别记录留存（供后续被分配词书覆盖后可练）。完整“学生个人词本”实体不做（见 §12 OQ-2/OQ-6 已决记录）。

### 3.5 决策 D4：自测与计划/正式考试互不写进度

自测**不**产生 `study_records`、不更新 `study_day_tasks`/`study_word_progresses`/`student_study_plans`，不触发积分事件（积分的两个来源维持现状：每日任务首次完成 + 计划学习答对）。自测只写：`exams`(SELF_PRACTICE)、记忆（含事件）、（M2）识别批次表。从而 2026-06-21 spec 的“自由学习不计计划进度、自测不进老师评价”两条红线从数据链路层面成立。

---

## 4. 总体架构

```
浏览器 (React 学生工作台 frontend/src/student/*)
   │  JWT / cookie word_atelier_token
   ▼
nginx :80  ──/api──▶  Spring Boot app
                          │
   ┌───────────┬──────────┼───────────────────┬──────────────────┐
   ▼           ▼          ▼                   ▼                  ▼
[自测域]   [记忆域(已有)] [识别域 M2]      [统计域 M3]        [AI 网关(已有,扩展视觉)]
SelfPractice  StudentWord   Recognition    StudyStats        AiGatewayService
Controller  MemoryService  Controller      Controller         +VisionImageCall
Service      (泛化)        Service                              (新: 图片消息)
   │           │              │               │
exams(SELF_   student_word_   student_        （聚合查询）      ai_configs(is_default
PRACTICE)+    memories/       recognition_                         视觉配置)
exam_questions(已有表,扩列)    events         batches/items
```

- 存储层：全部走 Flyway（下一个版本号 V43 起），PostgreSQL 15。
- 安全层：新增控制器均为 STUDENT 角色；创建自测等写操作在服务层二次校验“词书已分配/词资格”（沿用 `StudentWordMemoryService.isVisibleToStudent` 同款规则，收敛进 `AccessControlService` 或复用现有私有方法）。
- 响应/异常：裸 DTO + `GlobalExceptionHandler` 既有错误体；不做统一响应包裹。

---

## 5. 阶段一（M1）：自测与错题巩固闭环（Web，无 OCR）

> 目标：学生可从已分配词书 / 错词 / 收藏词发起 4 选 1 释义自测 → 逐题作答 → 结果页（正确/错误/待巩固 + 错题明细）→ 自动更新卡片盒与错词本 → 错词可再次作为范围重考。前端在 `frontend/src/student/*` 内新增页面族。

### 5.1 数据模型变更（迁移 V43，示意）

```sql
-- 1) exams：自测分型与归属、范围、自测统计列
ALTER TABLE exams
    ADD COLUMN exam_type           VARCHAR(32) NOT NULL DEFAULT 'FORMAL',
    ADD COLUMN created_by_student_id BIGINT,
    ADD COLUMN practice_scope      VARCHAR(32),          -- DICTIONARY|REVIEW_QUEUE|FAVORITES|RECOGNITION
    ADD COLUMN skipped_count       INT NOT NULL DEFAULT 0,
    ADD COLUMN wrong_count         INT NOT NULL DEFAULT 0,
    ADD COLUMN submitted_request_key VARCHAR(64);        -- 自测提交幂等键（唯一可空）
ALTER TABLE exams
    ALTER COLUMN dictionary_id DROP NOT NULL,            -- REVIEW_QUEUE/FAVORITES/RECOGNITION 可无单一词书
    ADD CONSTRAINT ck_exams_exam_type CHECK (exam_type IN ('FORMAL','SELF_PRACTICE')),
    ADD CONSTRAINT ck_exams_practice_scope CHECK (practice_scope IS NULL OR
        practice_scope IN ('DICTIONARY','REVIEW_QUEUE','FAVORITES','RECOGNITION'));
ALTER TABLE exams
    ADD CONSTRAINT fk_exams_created_by_student FOREIGN KEY (created_by_student_id)
        REFERENCES users(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_exams_submitted_request_key
    ON exams(submitted_request_key) WHERE submitted_request_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_exams_student_self_practice
    ON exams(created_by_student_id, exam_type, created_at DESC) WHERE exam_type = 'SELF_PRACTICE';

-- 2) 记忆事件表：确认 dictionary_id 可空（非 DICTIONARY 范围事件不带词书）
--    若 student_word_memory_events.dictionary_id 当前 NOT NULL，则
--    ALTER TABLE student_word_memory_events ALTER COLUMN dictionary_id DROP NOT NULL;

-- 3) exam_questions：无需加列（选项仍 A–D；“跳过”通过选项文本标签识别 / 未作答识别）。
```

> 迁移纪律：只新增 V43，不改动历史迁移；FORMAL 旧行 `exam_type` 默认 FORMAL，行为零变化。

### 5.2 服务端代码变更

新类（沿用仓库 `controller/service` 平铺分层，类名加 SelfPractice 前缀）：

| 类 | 职责 |
|---|---|
| `SelfPracticeController`（STUDENT） | 见 §5.4 API |
| `SelfPracticeService` | 范围解析→候选词→生成快照卷（事务） |
| `PracticePaperGenerator` | 出题算法（§5.5），纯逻辑、可单测 |
| `SelfPracticeSubmitService` | 提交判分（事务、幂等、记忆回写编排） |
| `SelfPracticeResultAssembler` | 结果/分析 DTO 组装 |

**改动既有类**：

- `model/Exam`：加 `examType`、`createdByStudentId`、`practiceScope`、`skippedCount`、`wrongCount`、`submittedRequestKey`（新增枚举 `ExamType`、`PracticeScope`）。
- `model/ExamStatus`：保持 GENERATED/SUBMITTED 即可（自测中途退出可再进，见 §5.4 resume）。
- `dto/ExamAnswerDto`：`selectedOption` 仍必填（提交的题目必须给选择）；**跳过**语义 = 该题不出现在 `answers`（判分按缺省=SKIPPED），或选中兜底标签文本（同 SKIPPED）。两种都支持，见 §5.5。
- `StudentWordMemoryService`：泛化记忆写入口（§5.3）。
- `StudentAssessmentService`（聚合器）：新增来源枚举（如 `SELF_PRACTICE_EXAM`），把 `exam_type=SELF_PRACTICE` 行纳入学生 `/assessments/history`（来源筛选）；**不进** teacher 可见的任何统计。

### 5.3 记忆回写：泛化 `StudentWordMemoryService`

现状：`recordPlanStudy(studentId, metaWordId, sourceId, dictionaryId, result, occurredAt)` 内部硬编码 `sourceType=PLAN_STUDY`，`applyResult` 里也硬编码 `lastSource=PLAN_STUDY`；事件表已支持任意 sourceType。

改造（向后兼容）：

```java
public StudentWordMemory record(Long studentId, Long metaWordId, Long sourceId,
        Long dictionaryId, StudentWordMemorySourceType sourceType,
        StudyRecordResult result, LocalDateTime occurredAt) {
    // 内部逻辑 = 现 recordPlanStudy，但 sourceType 入参化；dictionaryId 允许 null
    // 资格校验只对 FAVORITE 写操作保留（收藏仅限分配词书）；练习记忆行写入不限词书来源，
    // 但候选词在 SelfPracticeService 已按 D3 过滤，因此到达这里必然合规。
}
// 旧入口保留为委托，避免影响计划学习调用方：
public StudentWordMemory recordPlanStudy(...) { return record(..., PLAN_STUDY, ...); }
```

自测提交后逐词调用 `record(..., SELF_PRACTICE, sourceId=exam.id, dictionaryId=exam.dictionaryId, result, submittedAt)`。自测 SKIPPED → 传 `StudyRecordResult.SKIPPED`（按 spec 长期记忆按错误处理，box−1 + auto_wrong；同时 `last_result=SKIPPED` 保留语义区分）。

> 评审 Q7 已决：非 DICTIONARY 范围（错词/收藏）回写时 `dictionaryId` 传 `null`，事件不携带词书、不做反查（避免额外开销）；词书归属可从题目快照反查。

### 5.4 API 设计（M1）

约定：全部 `@PreAuthorize("hasRole('STUDENT')")`；前缀 `/api/students/me/practices`；自测行落在 exams 表（`exam_type=SELF_PRACTICE`），对外仍叫 practices 以与正式考试 API 隔离。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/students/me/practices/dictionaries` | 已分配词书 + 每本可出题词数（含直接/班级分配去重；便于发起页展示） |
| POST | `/api/students/me/practices` | 创建自测。Body：`{scope: DICTIONARY|REVIEW_QUEUE|FAVORITES, dictionaryId?, questionCount?, }`；返回会话（题目列表**不含正确答案**、含选项文本与发音元数据引用） |
| GET | `/api/students/me/practices/{examId}` | 续答未提交卷（返回同结构，已答保留） |
| POST | `/api/students/me/practices/{examId}/submit` | 交卷。Body：`{requestKey, answers:[{questionId, selectedOption}]}`；幂等键冲突→返回原结果；状态迁移 GENERATED→SUBMITTED |
| GET | `/api/students/me/practices/{examId}/result` | 结果：得分、correct/wrong/skipped、错题明细（词/正确释义/你的选择/是否入错词本）、记忆分布快照 |
| GET | `/api/students/me/practices/history?page&size&from&to` | 自测历史（也可经 `/assessments/history` 聚合视图进入） |

**历史与续答呈现契约（评审 Q2 已决）**：
- `/assessments/pending` 与 reminders **不含自测**（自测无老师下发、无截止，不参与 OVERDUE > IN_PROGRESS > NOT_STARTED 优先级排队）；
- 未提交自测经 `GET /api/students/me/practices/latest`（最近一场未提交）续答，前端入口文案“继续上次练习”；同一学生未提交自测同时 ≤ 5 场（超限需先提交/放弃最近一场，评审 Q6 配额）；
- 自测进入 `/assessments/history`，带来源枚举（如 `SELF_PRACTICE_EXAM`）可按来源过滤；历史仅呈现、不参与 pending；
- 遗留 `GET /api/exams/history` **默认行为不变（仅 FORMAL）**，不引入 `?type=` 参数（2026-06-21 spec 的过滤设想由新端点承担），对现网零回归。

**创建语义（D2/D3）**：

- `scope=DICTIONARY`：`dictionaryId` 必填且必须属于当前学生已分配词书；候选词=该书 `dictionary_words`（去重、需有可展示释义）。
- `scope=REVIEW_QUEUE`：候选词=当前学生 `auto_wrong=true` 记忆词（自动错词本）。
- `scope=FAVORITES`：候选词=`favorite=true` 记忆词。
- 候选词 < 2 时 400（提示“至少需要 2 个单词”，对齐 WordSnap 规则）。
- `questionCount` 缺省/0/超过候选数 = 全量；上限 100（防超大卷，可配常量）。抽题随机、顺序打乱后**立即落快照**。
- 复用场景下（REVIEW_QUEUE）不做“已入卷就移出错词本”的操作——错词是否清出由记忆规则（box≥5 且连对≥3）决定，与 WordSnap“手工移出”不同，但结果页提供“本次答对仍留在错词本/已达标自动移出”的说明文案；**手工移出错词本**接口列入 M3（见 §7.3；口径已决，评审 OQ-5：仅清 `auto_wrong`、答错自动重回，规则与自动达标移出并存）。

**创建响应（示意）**：

```jsonc
{
  "examId": 9012, "scope": "REVIEW_QUEUE", "sourceLabel": "自动错词本",
  "questionCount": 12,
  "questions": [
    { "questionId": 551, "metaWordId": 887, "word": "abandon",
      "phonetic": "/əˈbændən/",
      "phoneticDetail": { "ukPhonetic": "…", "usPhonetic": "…", "ukSpeechUrl": "…", "usSpeechUrl": "…" },
      "options": ["放弃；抛弃", "吸收；同化", "遵守", "我不会"],
      "optionLabels": ["A","B","C","D"] }
  ],
  "createdAt": "2026-…"
}
```

> 注意：自测答题界面**不返回、不展示音节文字**（`syllable_detail`），遵循 2026-06-21 syllable spec 防泄露；发音按钮仍可用（音素/音频不暴露拼写规则）。

**提交与幂等**：`submit` 事务内 `SELECT … FOR UPDATE` 该 exam → 已 SUBMITTED 且同 `requestKey` → 返回原结果（重放）；已 SUBMITTED 且异键 → 409（`ConflictException`，code 复用 `IDEMPOTENCY_KEY_CONFLICT` 风格）；否则判分并迁移状态。`submitted_request_key` 唯一部分索引兜底（对齐 reliable-handoff 的“应用幂等 + DB 唯一”双层模式）。

### 5.5 出题与判分算法（移植 WordSnap 逻辑）

**候选词解析**：按 scope 取词（见上）→ 按 `LOWER(word)` 去重（服务端有 `meta_words.normalized_word`/`WordNormalizationUtils` 可对齐）→ 过滤“无可展示释义”的词（WordSnap `hasResolvedMeaning` 规则）→ 随机抽样 `questionCount`（全量则全部）→ 乱序。

**每题选项生成**（`PracticePaperGenerator`，`optionCount=4`）：

1. `correctMeaning` = 词在服务端词条的展示释义（word→meaning/translation 优先级按既有展示口径：优先 `definition`+`translation`+`partOfSpeech` 组装，复用学生端词条展示同款组装器，避免与词书浏览不一致）。
2. 干扰池 = 同范围/同词书内其它词的正确释义 ∪（不足时）当前学生可见其它词书的释义池；参考 WordSnap：仍不足用兜底标签集合 `{我不会, 我不知道, 我不认识, 没学过, 不确定}` 顺序补齐至 3 个干扰项。
3. 洗牌后按 A–D 落 `exam_questions.option_a..d`，`correct_option` = 正确释义所在字母。
4. **跳过判定**：提交时该题缺失，或所选字母对应的选项文本命中兜底标签集合 → `SKIPPED`（待巩固；判分不计对错，记忆按错处理）。
5. **选项唯一性不变量（评审增补）**：四选项文本必须两两不同且正确项唯一——抽取的干扰项若与 `correctMeaning` 同文本则弃用并换源（其他可见词书释义 / 中性填充），避免同文本双正确项的歧义。
6. **即时反馈与独立“跳过”（评审 Q5 已决）**：正式选项恒为 4 个真实释义；页面提供独立“跳过此题”按钮 = `SKIPPED`（缺省未答同判 SKIPPED）；仅在极端小池（干扰源不足 3 个）时允许中性填充“不确定”，不把“我不会/没学过”等负面标签作为正式选项；答题交互为逐题即时反馈（对/错并展示正确释义）。

**判分汇总**：`correct_count/wrong_count/skipped_count`；`answered_count = correct+wrong`（对齐遗留列语义的“已作答”口径，正式考试行为不变）；`score = round(correct / question_count × 100)`（沿用遗留公式，SKIPPED 不进分子，与 WordSnap accuracy=correct/total 一致）。

**记忆回写**：见 §5.3；每题一个记忆事件（SELF_PRACTICE）。

**结果与分析**：复用遗留 result 查询 + 新增字段；错题明细组装 WordSnap `MistakeReviewItem` 等价结构（word/phonetic/correctMeaning/selectedMeanings），并标注“已自动加入自动错词本”。4 桶分布由记忆状态推导（§3.1 映射、§7.2 阈值）。

### 5.6 前端（M1，`frontend/src/student/*`）

| 页面/组件 | 内容 |
|---|---|
| 学生工作台入口 | 次级入口卡片「自主练习」：显示错词数/可练词书数（对齐 2026-06-21 工作台布局：首页摘要 + 自测次级入口） |
| `PracticeSetupPage` | 范围三选（词书/错词/收藏）→ 选词书（scope=DICTIONARY）→ 题量（10/20/30/全部）→ 展示预计题数、来源、退出练习说明 |
| `PracticeAttemptPage` | 题干=词 + 发音按钮（不展示音节文字）；4 选项单选；顶部进度 n/N；可选“我不会/不确定”标签项；答后即时对错反馈进入下一题；最后一题提交（带 `requestKey`，`sessionStorage` 复用提交键防重复，对齐前端幂等惯例） |
| `PracticeResultPage` | 奖杯 + 得分/正确率 + 正确/错误/待巩固 + 错题明细（自动入错词本标注）+ 记忆分布 + 学习建议 + “再练一遍（同错词）”按钮（scope=REVIEW_QUEUE 重新创建） |
| `WrongWordsPage`（增强现有错词列表页） | 错词 →「练这些词」一键以错词为范围建卷；每词可手工移出（M3，OQ-5 已决：仅清 auto_wrong、答错自动重回） |
| 复用 | 现有发音播放、SyllableReader 之外的词条展示组件、错误提示与加载态 |

> 命名与交互收敛（评审 Q5/Q7 已决）：入口卡片命名固定为「自测」（对齐 spec“自测练习”），**不占用** June spec 预留的“自由学习”命名（后续逐词学习功能使用）；答题页含“跳过此题”按钮。

### 5.7 M1 验收标准

1. 学生在 Web 可对已分配词书/错词/收藏发起自测，题量可配、快照稳定（重进继续作答同一卷）。
2. 提交幂等（双击/断线重发不重复判分不重复写记忆）；提交后不可改。
3. 判分正确：对/错/跳过计数与 score 符合 §5.5；兜底标签选择显示为“待巩固”且不污染正确率。
4. 自测结果**不**产生 plan/study_record/points 副作用；老师端所有统计零变化；正式考试行为回归通过（复用既有 exam 单测 + 新增集成测试）。
5. 记忆回写正确：CORRECT→box+1；错/跳过→box−1 且 `auto_wrong=true`；事件 `source_type=SELF_PRACTICE`、`source_id=exam.id`；`last_source=SELF_PRACTICE`。
6. 错词（`GET /students/me/wrong-words`）立即包含本次错/跳词；用错词范围可立即重考。
7. 自测行不出现于 `/assessments/pending`、reminders 与任何老师视图；`/practices/latest` 可续答最近未提交卷；遗留 `/api/exams/history` 输出与上线前一致（仅 FORMAL）。

---

## 6. 阶段二（M2）：拍照/图片识词云端化（OCR）

> 目标：把 WordSnap “拍照识词 → 确认 → 入练习池”搬到服务端。Web 端=上传图片/粘贴截图（浏览器端做 EXIF 纠正与压缩，或服务端二次校验）；后续移动端可复用同一 API（客户端裁剪、原生预处理差异收敛到客户端）。

### 6.1 总体流程（移植 WordSnap OCR 管线到服务端）

```
上传图片(POST /recognitions)
  → 服务端校验/落盘(uuid, 上限 10MB, 内容类型)
  → 视觉 AI 结构化识别(默认视觉 AI 配置; prompt 复用 WordSnap word_book_ocr.prompt, 服务端资源化)
  → 词条清洗(无 CJK、字母开头、≤48 字符、≤4 token；同词去重择优)
  → meta_words 解析回填(释义/音标/发音元数据, normalized_word 命中)
  → 生成识别批次(草稿) + 候选词列表
  → 学生预览确认(勾选/剔除；无释义或词书外词禁用勾选并提示原因)
  → 确认落库(批次标记 confirmed；勾选词进入“练习池”)
  → 从该批发起自测(scope=RECOGNITION)或逐词加入错词/收藏
```

### 6.2 数据模型（迁移 V44，示意）

```sql
CREATE TABLE student_recognition_batches (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    source_type     VARCHAR(32) NOT NULL,          -- CAMERA|GALLERY|UPLOAD (客户端上报)
    source_label    VARCHAR(255) NOT NULL,         -- 识别来源标签, 如 "课本第 10 页"
    image_path      VARCHAR(500) NOT NULL,         -- 服务端存储相对路径
    image_sha256    VARCHAR(64),
    quality_score   NUMERIC(4,2),                  -- 对齐 WordSnap 质量分(0..1)
    suggestion      VARCHAR(500),                  -- 低质量建议文案
    ocr_engine_label VARCHAR(100),                 -- 模型标识(审计)
    raw_recognized_text TEXT,                      -- 原文(审计/调优用)
    recognized_line_count INT, cjk_line_count INT,
    recognition_duration_ms BIGINT,
    status          VARCHAR(32) NOT NULL,          -- PENDING_CONFIRM|CONFIRMED|DISCARDED|EXPIRED
    confirmed_at    TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_recognition_batch_status CHECK (status IN
        ('PENDING_CONFIRM','CONFIRMED','DISCARDED','EXPIRED'))
);
CREATE INDEX idx_recognition_batches_student ON student_recognition_batches(student_id, created_at DESC);

CREATE TABLE student_recognition_items (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      BIGINT NOT NULL REFERENCES student_recognition_batches(id) ON DELETE RESTRICT,
    raw_word      VARCHAR(120) NOT NULL,           -- OCR 原文
    meta_word_id  BIGINT REFERENCES meta_words(id) ON DELETE RESTRICT,  -- 解析命中; NULL=未解析
    meaning       TEXT, phonetic VARCHAR(255),     -- OCR 或回填的快照释义/音标
    confidence    NUMERIC(4,3),
    status        VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',  -- CANDIDATE|SELECTED|EXCLUDED|OUT_OF_BOOK|UNRESOLVED
    resolved_from_dictionary BOOLEAN NOT NULL DEFAULT FALSE,  -- 命中已分配词书?(D3 资格)
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_recognition_item_status CHECK (status IN
        ('CANDIDATE','SELECTED','EXCLUDED','OUT_OF_BOOK','UNRESOLVED'))
);
CREATE UNIQUE INDEX uk_recognition_items_batch_raw ON student_recognition_items(batch_id, LOWER(raw_word));
CREATE INDEX idx_recognition_items_meta ON student_recognition_items(meta_word_id);
```

### 6.3 识词服务（复用/扩展 AI 设施）

- 新增 `VisionAiGateway`（或扩展 `AiGatewayService`）：支持 OpenAI 兼容 `chat/completions` 的 `image_url` 消息（base64 data URI），沿用 connect/read 超时、错误映射（`BadGatewayException`）、失败重试策略（WordSnap 解析失败/截断重试 1 次的经验可直接复刻）。
- **配置（前置依赖 A，评审 Q4 已决）**：学生识词**统一以系统级默认视觉配置代调用**——落地 `desin-ai-import-imag.zh-CN.md` 已设计的配置作用域：`ai_configs` 增加 `scope(USER/SYSTEM)` 与 `supports_vision`，系统级候选 `ENABLED + isDefault + supportsVision` 多选一；学生无 Key、无 Provider 下拉。现状该机制仅存在于设计文档（现 `ai_configs` 按用户、`AiGatewayService` 仅 `generateText`），故随 M2 一并交付：作用域/视觉能力列、系统级默认配置查询、视觉网关方法；识别行为审计记录 `ocr_engine_label`。
- **防滥用（前置依赖 B，评审 Q4 已决）**：图片按 `sha256` 去重（同图不重复调 AI）；每生识别配额默认 **10 图/日**（配置项）；无可用默认视觉配置时返回可读错误“识词服务未配置，请联系老师/管理员”，不泄露内部细节。
- **清理纪律（前置依赖 C，评审 Q4/Q6 已决）**：不新增定时后台任务——图片超期（默认 30 天）采用惰性清理（上传/列表查询时顺带删除）并保留**显式维护脚本**（符合“物理清理必须显式脚本/管理操作”条款），不与积分单后台实例纪律冲突。
- Prompt：把 `WordSnap/assets/prompts/word_book_ocr.prompt` 迁入 `src/main/resources/prompts/word_book_ocr.prompt`（保留 JSON schema 约束与“只返回完整 JSON”提示），字段名对齐既有 `meta_words` 解析（word/pos+meaning/confidence）。
- 解析回填：OCR 词 → `meta_words`（`normalized_word` 唯一索引命中）→ 组装释义/音标/`phonetic_detail` 等展示数据；未命中词保留 OCR 释义或标“待补充释义”（WordSnap `unresolvedMeaning` 语义）且不入题。
- 词书归属判定：命中词是否属于该生直接/班级分配词书（复用资格查询）；否 → `OUT_OF_BOOK`（展示但禁入题）。

### 6.4 API 设计（M2）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/students/me/recognitions` | multipart `image`（≤10MB, image/*）；同步返回识别预览（批次草稿 + 候选词列表 + 质量分/建议/耗时） |
| GET | `/api/students/me/recognitions/{batchId}` | 取批次与候选词（继续确认） |
| POST | `/api/students/me/recognitions/{batchId}/confirm` | 提交勾选结果 `{items:[{itemId, selected:true/false}]}` → 落库（SELECTED/EXCLUDED），批次 → CONFIRMED |
| POST | `/api/students/me/recognitions/{batchId}/discard` | 丢弃批次（图片标记删除） |
| GET | `/api/students/me/recognitions/history` | 识别历史（PENDING/CONFIRMED 概览 + 词数 + 状态） |
| POST | `/api/students/me/practices` | 创建时 `scope=RECOGNITION` + `recognitionBatchId`（仅限本人 CONFIRMED 批次中 SELECTED 且资格词） |

图片留存：服务端 `uploads/recognitions/`（compose 挂卷），批次 CONFIRMED 后保留默认 **30 天**（配置项），按评审 Q4-C 由惰性清理删除并置 `status=EXPIRED`/`DISCARDED`；响应体不带私有存储绝对路径。隐私说明随文档附录（学生图片属敏感数据，需在设置/协议中声明用途与留存期）。批次与条目元数据保留 **12 个月**后由显式维护脚本清理（评审 Q6 已决）；识别批次历史不参与 M3 统计口径（统计依赖 exams + memories）。

### 6.5 前端（M2）

- Web：识词入口支持选择/拖拽/粘贴图片；上传后展示“识别预览页”（原图缩略 + 质量分 + 词表勾选，禁用不可入题项并标注原因），确认后跳“识别结果页”→ 可“直接出题（本批）”。
- 移动端（后续 Flutter/原生客户端）：拍照、裁剪走客户端能力，仅上传已处理图片复用同一 API；服务端不实现客户端裁剪。

### 6.6 M2 验收标准

1. 学生可上传图片并得到结构化候选词；与 WordSnap 同图同词（抽验 10 图）召回一致。
2. 预览确认/剔除可落库；未解析与词书外词正确标记且不可入题；发起 RECOGNITION 自测仅含 SELECTED+资格词。
3. 无默认视觉配置时返回可读错误（如“识词服务未配置，请联系老师/管理员”），不泄露内部错误。
4. 图片按策略留存与清理；学生只能访问本人批次。

---

## 7. 阶段三（M3）：记忆分析与统计增强

> 目标：把 WordSnap “记忆分析页/统计页/学习建议/最近学习”沉淀为学生端服务端计算的分析能力，补齐 30 天跨来源统计。

> 统计口径原则（评审增补）：自测按**场次口径**（每场题数/得分），计划学习按**词条作答口径**（study_records）；两者不跨源合并“正确率”，跨源仅合并可加性指标（学习天数、学习词次、连续打卡）；统计不依赖识别批次历史。

### 7.1 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/students/me/study-stats/summary` | 汇总：今日/近 30 天自测场次、词数、正确率、当前错词/收藏/已学词数（组合查询 exams(SELF_PRACTICE) + memories + plans） |
| GET | `/api/students/me/study-stats/daily?days=30&sources=PLAN_STUDY,SELF_PRACTICE,FORMAL_EXAM` | 按日学习量/正确率序列（学习计划走 `student_attention_daily_stats`/`study_records`；自测走 exams；来源可筛选，对齐 2026-06-21 “正确率趋势可按来源筛选”） |
| GET | `/api/students/me/study-stats/memory-distribution` | 4 桶分布（§3.1 阈值） + 桶内词抽样 |
| GET | `/api/students/me/study-stats/recommendation` | 学习建议文案（移植 WordSnap `_buildStudyRecommendation` 规则，输入换成服务端指标） |

### 7.2 记忆分布推导（服务端统一口径）

`MemoryDistributionService` 按学生记忆行聚合（阈值常量，随 2026-06-22 spec 复核）：

- mastered：`box_level ≥ 5` 且 `auto_wrong=false`
- fuzzy：`auto_wrong=true`（错词）
- uncertain：有记忆行、`box_level=0`、非错词（多为 SKIPPED 累积）
- unseen：学生可见词书内无记忆行的词数（= 可见词总量 − 有记忆行数；避免每次全表扫，可缓存/定时统计，见下）

**性能注意**：unseen 需要“学生可见词总量”。直接分配词书在词典规模大时 count 代价可接受（学生可见词书通常 ≤ 几本），实现时验证；必要时为分配关系维护物化计数或按需懒加载，**不引入新后台任务**（遵守“单后台实例”纪律；若需要定时任务需显式走配置并评审）。

### 7.3 前端（M3）

- 学生端“统计/我的学习”页：30 天日历/柱状（按来源切换）、记忆分布环图与词抽样、学习建议卡、最近学习流（自测历史 + 识别历史 + 计划学习摘要，来源标签区分——对齐 WordSnap `RecentStudyUnit` 视图）。
- “收藏词”列表页升级为可浏览可复习（scope=FAVORITES 出题已有）。
- 错词页提供“移出错词本”（PATCH 记忆 `auto_wrong=false` 需满足业务校验——建议仅当该词已达标或学生明确选择，M3 评审产品口径）。

**错词手工移出（评审 OQ-5 已决）**：新增 `PATCH /api/students/me/word-memory/{metaWordId}/wrong`（置 `autoWrong=false`）——只清错词标记，不动 `box_level` 与答对/答错历史；下次答错自动重回错词本；前端二次确认，文案说明“仅移出错词复习列表、不影响掌握度，答错会自动再次加入”；可选软约束：仅当该词最近一次练习结果为答对时展示“移出”按钮。

### 7.4 M3 验收标准

1. 三种来源按日统计口径与既有 dashboard/attention 数据一致（不重复计次：同一次作答只计一个来源）。
2. 记忆分布与错词/收藏列表同源（都是 `student_word_memories` 派生），数字对得上。
3. 建议文案可解释（规则表驱动），不出现误导性结论。

---

## 8. 可选增强与后续（不在三阶段内）

- 自测复习排期：将“错词到期”并入学生 dashboard reminders（新增 `WRONG_WORD_DUE` 提醒源）需与学习计划提醒逻辑合并评审。
- 双人/跨设备对战（WordSnap two-player 服务端化）：需新建实时/回合制模型，单独立项。
- 个人扩展词本（词书外词入个人练习池）：与现有“收藏仅限分配词书”“错词源自记忆行”两条不变式冲突，单独立项（见 OQ-2）。
- 学生自测设置（题量/确认方式等偏好）服务端持久化：M3 后可加 `student_practice_prefs` 单行表。

---

## 9. 明确不移植清单（WordSnap → 服务端产品）

| WordSnap 特性 | 结论 | 理由 |
|---|---|---|
| 同屏双人模式 | 不移植 | Web 产品无同屏双人场景；跨设备对战另行立项 |
| 9 选项模式 | 不移植（统一 4 选项） | 与现有 4 选 1 快照模型一致；**OQ-1 已决：不做 9 选项，不扩列 option_e..i、不扩展 A–I 判分语义** |
| 客户端 OCR API Key/Provider 下拉 | 移除 | 服务端默认视觉配置统一管理（§6.3） |
| 应用内升级、成绩卡分享、音效/触觉、深浅主题、引导页 | 不移植 | 客户端/平台特性，与后端产品无关 |
| 本地存储键模型（captures/history/exam_counts/word_buckets…） | 不移植 | 服务端状态模型 = exams/memories/recognitions |
| “演示考试/种子识别数据” | 不移植 | 服务端不做客户端式种子演示；空数据页给空态引导 |

---

## 10. 测试与迁移策略

- **单元测试**：`PracticePaperGenerator`（选项唯一性、正确项存在、兜底标签补齐、去重/资格过滤、随机种子确定性）；判分（对/错/缺省跳过/标签跳过）；记忆回写参数化（sourceType/事件字段）；分布推导。
- **集成测试**：Testcontainers PostgreSQL 跑 V43/V44 迁移链（按仓库既有 PG 验证纪律；H2 仅 test ddl-auto，注意实体与生产迁移的口径漂移，新列必须进实体与迁移两侧）。
- **回归**：遗留 FORMAL exam 全链路测试不回归；`/assessments/*` 聚合兼容；学生 dashboard、积分、计划 recordStudy 不受影响（自测链路不触碰它们）。
- **幂等测试**：submit 同键重放返回原结果、异键 409、并发提交只成功一次。

---

## 11. 实施顺序与工作量粗估

| 阶段 | 内容 | 后端 | 前端 | 依赖 |
|---|---|---|---|---|
| M1 | 自测/错题闭环 | V43 + 服务泛化 + 自测服务 + API | 4 页面 + 入口 | 2026-06-21/22 spec 评审 |
| M2 | 识词云端化 | V44 + VisionAi + 识别服务 + API | 上传/预览/确认页 | M1 记忆回写就绪；默认视觉配置上线 |
| M3 | 统计与分析 | 统计服务 + 分布推导 | 统计/收藏/分析页 | M1/M2 数据沉淀 |

每阶段独立可发布；M1 是闭环地基，建议先行并上线验证后再进入 M2。

---

## 12. 风险与开放问题

**风险**

| 风险 | 影响 | 缓解 |
|---|---|---|
| exams 表承载自测后行数膨胀（学生高频自测） | 查询性能/表单增长 | 自测索引（§5.1）；历史只保留近 N 场/聚合统计，超期自测卷可归档列或移历史表（评审后定） |
| 自测高频写记忆事件 | 事件表膨胀 | 事件保留沿用计划学习既有策略、不做自测特例；若整体膨胀单独立项压缩方案（评审 Q6 已决） |
| 视觉 AI 成本/失败 | M2 不可用 | 默认配置 + 限流（每学生每日识别次数配额，评审定值）+ 前端友好错误 |
| 图片隐私 | 合规 | 留存 30 天、本人可见、声明用途（§6.4） |
| `exam_questions` 选项列数 | ~~4 列将来不够（如 9 选项）~~ **已定案关闭（OQ-1 已决）：保持 4 选项，不做 9 选项，不扩列 option_e..i**；若未来产品改弦更张，需单独评审扩列与 A–I 判分语义 |

**开放问题（OQ，评审确认）**

- OQ-1：~~选项数量是否永远 4？~~ **已决（产品确认）：保持 4 选项，不做 9 选项**——不扩列 `option_e..i`、不扩展 A–I 判分语义；同屏双人亦不做。
- OQ-2：~~是否允许练习词书外识别词~~ **已决（评审 Q3）：不允许**——词书外/未解析词不入题、不写记忆、不可收藏。
- OQ-3：~~题量/配额~~ **已决（评审 Q6）**：题量默认全量、上限 100；识别配额 10 图/日；未提交自测 ≤ 5 场。
- OQ-4：~~历史保留策略~~ **已决（评审 Q6）**：自测行长期保留，超阈值（如 50M 行）再启动显式归档；识别批次元数据 12 个月后显式脚本清理。
- OQ-5：~~错词“手工移出”的产品口径~~ **已决（产品确认）：允许手工移出（带护栏）**——只清 `auto_wrong`、不动卡盒与答对/答错历史，下次答错自动重回错词本；移出需二次确认；自动达标（box≥5 且连对≥3）移出仍为主通道，规则不变。详见 §7.3。
- OQ-6：~~个人词本实体~~ **已决（评审 Q3）：不做**；“我的词书”体验 = 分配词书 + 错词/收藏 +（M2）识别批次内已解析且已分配的词。

---

## 附录 A：WordSnap 功能 → 服务端/前端落点速查

| WordSnap 功能 | 服务端落点 | 前端落点 | 阶段 |
|---|---|---|---|
| 识别（拍照/相册/裁剪/OCR） | 识别批次服务 + Vision AI | 上传/预览/确认页 | M2 |
| 识别词筛选（勾选入考） | items.SELECTED + RECOGNITION scope | 确认页勾选 | M2 |
| 考试设置（范围/题量/模式） | SelfPracticeService（4 选 1、范围三/四类、题量） | PracticeSetupPage | M1 |
| 答题页（题干词+释义选项+发音+进度） | 快照卷 GET | PracticeAttemptPage（不展示音节） | M1 |
| 判分（对/错/待巩固/兜底标签） | 提交服务 + SKIPPED 语义 | 结果页 | M1 |
| 成绩/分析/错题明细 | ResultAssembler + 记忆分布 | PracticeResultPage | M1 |
| 收藏词 | 既有记忆 favorite + FAVORITES scope | 收藏列表页（M3 升级） | M1/M3 |
| 复习队列（错题集合） | `auto_wrong` 记忆 + REVIEW_QUEUE scope | 错词页 + 一键重练 | M1 |
| 记忆 4 桶/环形图 | 分布推导服务 | 分析/结果页图表 | M1(结果页)/M3(统计) |
| 学习建议 | 规则服务（指标化） | 建议卡 | M1/M3 |
| 最近学习流/统计 | 统计服务 | 统计页 | M3 |
| 设置持久化（本地） | （偏好服务端化） | 设置 | M3 可选 |

## 附录 B：本文档引用的仓库权威文档

- `CONTEXT.md`：产品语言与词汇（班级/辞书/计划/试卷/学生作答记录…）。
- `docs/superpowers/specs/2026-06-21-student-post-login-requirements-design.md`：学生端全需求 + **扩展 exams(exam_type/created_by_student_id) 决策**（见本文 §3.2 决策 D1）。
- `docs/superpowers/specs/2026-06-22-student-word-memory-design.md`：记忆规则（SKIPPED 长期按错、间隔、错词清除条件）。
- `docs/superpowers/specs/2026-06-22-student-workspace-first-slice-design.md`：提交幂等/权威 dashboard/任务完成语义。
- `docs/superpowers/specs/2026-06-21-student-syllable-reading-design.md`：自测/考试答题界面不展示音节。
- `docs/requirements/exam-question-paper-requirements.zh-CN.md`：试卷流老师域、自测不进老师评价、禁级联。
- `docs/desin-ai-import-imag.zh-CN.md`：默认视觉 AI 配置 + 上传→识别→预览→确认 模式。
- `docs/study-plan-design.md`：计划记忆与复习调度（自测错词排期衔接点）。
- `AGENTS.md`：构建/质量/编码纪律（Flyway 只前向、新外键禁级联、单后台实例等）。

---

## 附录 C：评审修订记录

> 评审方式：grilling 逐题访谈；结论已并入上文对应小节，本表为追溯索引。

| 议题 | 结论 | 落点 |
|---|---|---|
| Q1 存储轨道 | 维持 D1；补行所有权契约、隔离审计表（旧面 FORMAL-only）、退出通道 | §3.2 |
| Q2 聚合呈现 | pending/reminders 不含自测；`/practices/latest` 续答；`/exams/history` 默认不变 | §5.4、§5.7 |
| Q3 词书外练词 | 定案：不入题/不写记忆/不可收藏；无个人词本 | §3.4、§12 OQ |
| Q4 M2 前置 | SYSTEM 视觉配置+网关（A）、hash 去重+配额（B）、惰性清理+显式脚本（C） | §6.3 |
| Q5 答题交互 | 即时反馈 + 独立“跳过”按钮；选项恒 4 个真实释义 | §5.5、§5.6 |
| Q6 配额保留 | 识别 10 图/日、未提交自测 ≤5、题量 ≤100；长期保留超阈值归档；批次元数据 12 月清理；事件无特例 | §12、§6.4 |
| Q7 收尾 | 命名“自测”（空出“自由学习”）；非词书范围事件 dictionary_id 置空；不迁移 WordSnap 本地数据；填充仅“不确定” | §5.3、§5.5、§5.6 |
| OQ-1 选项数（评审后续决议） | 保持 4 选项，不做 9 选项，不扩列 option_e..i、不扩展 A–I 判分语义 | §9、§12 |
| OQ-5 错词手工移出（评审后续决议） | 允许手工移出（带护栏）：仅清 auto_wrong、不动卡盒/历史、答错自动重回；自动达标仍为主通道 | §7.3、§12 |


