# 班级学习计划设计文档

## 1. 文档目标

本文档用于为当前单词学习系统设计“教师给班级下发学习计划”的能力，重点解决以下问题：

- 教师可以面向班级发布一套持续多天的学习计划，而不是一次性只分配词书。
- 学生登录后点击“学习”即可进入当天任务，不需要自己判断今天该学什么。
- 系统可以按艾宾浩斯遗忘曲线组织复习，并支持老师调整关键指标。
- 教师可以明确看出哪些学生哪一天没有学习，哪些学生学了但没有完成。

本文档只做设计，不直接修改代码。

## 2. 当前项目现状

当前后端已经具备以下与本需求强相关的基础能力：

- 班级管理：`classrooms`、`classroom_members`
- 教师和学生归属关系：`teacher_student_relations`
- 词书分配：`dictionary_assignments`
- 学生查看已分配词书：`GET /api/students/me/dictionaries`
- 教师给学生发考试、学生提交考试：`exams`

现状说明：

- 当前系统已经能表达“哪个老师管理哪个班级、哪个学生属于哪个班级”。
- 当前系统已经能表达“某本词书被分配给哪些学生”。
- 当前系统还不能表达“按天分解的学习任务”“按单词的复习阶段”“缺勤识别”“学习打卡”“学习日历”。
- 因此，学习计划功能应建立在现有班级和词书分配之上，但需要新增独立的数据模型，不能直接复用考试表。

## 3. 设计目标

### 3.1 业务目标

- 教师可以按班级发布一个完整学习计划。
- 学习计划可以绑定某一本词书。
- 学习计划支持每日新学和复习混合推进。
- 学生每天进入系统后能拿到“今日任务队列”。
- 教师可以查看班级维度和学生维度的执行情况。

### 3.2 教学目标

- 支持符合艾宾浩斯遗忘曲线的复习节奏。
- 支持不同年级、不同词书、不同学习强度的配置。
- 支持错词回退和逾期补学。
- 支持老师快速发现掉队学生。

### 3.3 非目标

- 本期不做复杂 AI 个性化推荐。
- 本期不做家长端。
- 本期不把“学习计划”和“考试”合并成一个模型。
- 本期不要求支持多个老师共同编辑同一计划。

## 4. 核心用户场景

### 4.1 教师场景

教师进入班级页面后，可以选择一本词书，为一个或多个班级创建学习计划，配置开始时间、每日新词量、每日复习上限、复习曲线和完成标准，然后发布。

发布后：

- 系统为班级内每个学生生成一份个人计划实例。
- 系统为这些学生自动建立词书可见关系。
- 教师可以随时查看计划执行情况和缺勤名单。

### 4.2 学生场景

学生登录后看到“我的学习计划”列表。进入某个计划后点击“开始学习”，系统自动返回今天应学习的内容：

- 逾期未完成复习
- 今天应复习的词
- 今天的新词

学生完成学习后，系统实时更新：

- 当天任务完成状态
- 单词掌握进度
- 下次复习日期
- 连续学习天数
- 每个单词的停留时长和注意力统计

### 4.3 班主任/老师管理场景

老师可以查看：

- 今日班级完成率
- 今日未开始学习人数
- 今日学习中但未完成人数
- 今日已完成人数
- 连续缺勤人数
- 复习积压最严重的学生
- 每个学生当天的单词停留统计和注意力概况

## 5. 学习计划的关键配置指标

老师可配置的指标建议分为七类。

### 5.1 基础信息

- `planName`：计划名称
- `description`：计划说明
- `dictionaryId`：绑定词书
- `classroomIds`：目标班级
- `startDate`：开始日期
- `endDate`：结束日期
- `timezone`：时区

### 5.2 学习强度

- `dailyNewCount`：每日新词数
- `dailyReviewLimit`：每日最大复习数
- `studyDaysPerWeek`：每周学习天数
- `allowWeekend`：是否周末学习
- `dailyDeadlineTime`：每日截止时间，例如 `21:30`

### 5.3 复习策略

- `reviewMode`：复习模式，建议支持 `EBBINGHAUS`、`FIXED_INTERVAL`、`CUSTOM`
- `reviewIntervals`：复习间隔数组，例如 `[0, 1, 2, 4, 7, 15]`
- `wrongAnswerFallbackStage`：答错后回退到哪一级
- `allowOverdueCarryForward`：是否允许逾期任务滚入次日
- `overduePriority`：逾期任务优先级，建议默认最高

### 5.4 完成标准

- `completionThreshold`：判定“完成当天计划”的完成率阈值，建议默认 `100%`
- `minStudyMinutes`：最少学习时长
- `minCompletedWords`：最少完成单词数
- `minAccuracy`：最低正确率

### 5.5 排序与出题策略

- `wordOrderMode`：词书顺序、随机、难度优先、低掌握优先
- `preferHighFrequencyWords`：是否优先高频词
- `preferLowMasteryWords`：是否优先低掌握词
- `newAndReviewMixMode`：新词和复习词的混排模式

### 5.6 计划弹性策略

- `allowAutoThrottle`：是否允许系统自动降载
- `maxBacklogDays`：最大积压天数
- `maxDailyTaskCap`：每日任务总上限
- `enableMakeUpMode`：是否启用补学模式

### 5.7 注意力采集策略

- `enableAttentionTracking`：是否启用注意力指标采集
- `minFocusSecondsPerWord`：单词有效停留最小时长阈值
- `maxFocusSecondsPerWord`：单词有效停留最大计入时长，超出部分按封顶处理
- `longStayWarningSeconds`：单词停留过长预警阈值
- `idleTimeoutSeconds`：页面无操作多久后视为脱离注意力
- `attentionScoreMode`：注意力分算法，建议支持 `RULE_BASED`

## 6. 推荐默认方案：艾宾浩斯学习计划

为了尽快上线，建议提供一套默认模板，老师创建时可直接选用。

### 6.1 推荐默认参数

- `dailyNewCount = 20`
- `dailyReviewLimit = 60`
- `reviewIntervals = [0, 1, 2, 4, 7, 15]`
- `completionThreshold = 100%`
- `allowOverdueCarryForward = true`
- `wrongAnswerFallbackStage = previous_stage`
- `dailyDeadlineTime = 21:30`

### 6.2 含义说明

- `0` 表示新词当天必须完成首轮学习。
- `1` 表示第 1 天复习。
- `2` 表示第 2 天复习。
- `4` 表示第 4 天复习。
- `7` 表示第 7 天复习。
- `15` 表示第 15 天复习。

### 6.3 单词复习规则

- 新词第一次完成后，进入阶段 `0`，并生成下一次复习时间。
- 每次复习答对，则进入下一阶段。
- 每次复习答错，则回退一阶段。
- 若连续错误较多，可直接回退到 `1` 日复习阶段。
- 当天到期未复习，则进入逾期状态，并在次日排到最高优先级。

### 6.4 每日任务优先级

建议采用以下优先级：

1. 逾期复习
2. 今天应复习
3. 今天新词

这样能避免学生只做新词、不补复习，导致记忆断层。

## 7. 业务对象设计

建议新增以下核心对象。

### 7.1 `StudyPlan`

作用：

- 教师创建的计划模板
- 描述一套教学安排

关键字段建议：

- `id`
- `name`
- `description`
- `teacherId`
- `dictionaryId`
- `startDate`
- `endDate`
- `timezone`
- `dailyNewCount`
- `dailyReviewLimit`
- `reviewMode`
- `reviewIntervalsJson`
- `completionThreshold`
- `dailyDeadlineTime`
- `status`
- `createdAt`
- `updatedAt`

### 7.2 `StudyPlanClassroom`

作用：

- 关联一个学习计划和多个班级

关键字段建议：

- `id`
- `studyPlanId`
- `classroomId`
- `createdAt`

### 7.3 `StudentStudyPlan`

作用：

- 学习计划发布后，为每个学生生成个人计划实例
- 便于单独记录每个学生的进度、状态、缺勤、连续学习天数

关键字段建议：

- `id`
- `studyPlanId`
- `studentId`
- `status`
- `joinedAt`
- `completedDays`
- `missedDays`
- `currentStreak`
- `lastStudyAt`
- `overallProgress`
- `avgFocusSeconds`
- `attentionScore`
- `createdAt`
- `updatedAt`

### 7.4 `StudyDayTask`

作用：

- 表示某个学生在某个日期的学习任务汇总

关键字段建议：

- `id`
- `studentStudyPlanId`
- `taskDate`
- `newCount`
- `reviewCount`
- `overdueCount`
- `completedCount`
- `completionRate`
- `totalFocusSeconds`
- `avgFocusSecondsPerWord`
- `maxFocusSecondsPerWord`
- `attentionScore`
- `idleInterruptCount`
- `status`
- `startedAt`
- `completedAt`
- `deadlineAt`
- `createdAt`

### 7.5 `StudyWordProgress`

作用：

- 表示学生在一个计划内对某个单词的长期掌握情况

关键字段建议：

- `id`
- `studentStudyPlanId`
- `metaWordId`
- `phase`
- `masteryLevel`
- `nextReviewDate`
- `lastReviewAt`
- `correctTimes`
- `wrongTimes`
- `totalReviews`
- `lastResult`
- `lastFocusSeconds`
- `avgFocusSeconds`
- `maxFocusSeconds`
- `status`

### 7.6 `StudyRecord`

作用：

- 记录每次学习行为
- 支撑审计、回放、统计和补偿逻辑

关键字段建议：

- `id`
- `studentStudyPlanId`
- `metaWordId`
- `taskDate`
- `actionType`
- `result`
- `durationSeconds`
- `focusSeconds`
- `idleSeconds`
- `interactionCount`
- `attentionState`
- `stageBefore`
- `stageAfter`
- `createdAt`

### 7.7 `StudentAttentionDailyStat`

作用：

- 汇总学生某一天的注意力表现
- 支撑老师按日查看学生单词停留统计

关键字段建议：

- `id`
- `studentStudyPlanId`
- `taskDate`
- `wordsVisited`
- `wordsCompleted`
- `totalFocusSeconds`
- `avgFocusSecondsPerWord`
- `medianFocusSecondsPerWord`
- `maxFocusSecondsPerWord`
- `longStayWordCount`
- `idleInterruptCount`
- `attentionScore`
- `createdAt`
- `updatedAt`

## 8. 推荐状态设计

### 8.1 计划状态

`StudyPlan.status`：

- `DRAFT`
- `PUBLISHED`
- `PAUSED`
- `COMPLETED`
- `ARCHIVED`

### 8.2 学生计划状态

`StudentStudyPlan.status`：

- `ACTIVE`
- `PAUSED`
- `COMPLETED`
- `DROPPED`

### 8.3 每日任务状态

`StudyDayTask.status`：

- `NOT_STARTED`
- `IN_PROGRESS`
- `COMPLETED`
- `MISSED`

### 8.4 单词学习状态

`StudyWordProgress.status`：

- `NEW`
- `LEARNING`
- `REVIEWING`
- `MASTERED`
- `OVERDUE`

## 9. 核心业务流程

### 9.1 教师创建并发布学习计划

1. 教师选择词书和班级。
2. 教师配置每日新词量、复习策略、截止时间等参数。
3. 系统校验教师是否有权管理这些班级和词书。
4. 系统创建 `StudyPlan`。
5. 系统创建 `StudyPlanClassroom` 关联记录。
6. 系统为班级内每个学生生成 `StudentStudyPlan`。
7. 系统为这些学生补齐词书分配关系。
8. 系统创建起始日期的 `StudyDayTask`，或在首次进入时惰性生成。

### 9.2 学生进入学习

1. 学生打开“我的学习计划”。
2. 系统返回当前激活计划和今日概况。
3. 学生点击“开始学习”。
4. 系统根据当前日期和复习规则生成今日任务队列。
5. 队列顺序遵循“逾期复习 > 今日复习 > 今日新词”。

### 9.3 学生完成单词学习

1. 学生完成一个单词学习动作。
2. 系统记录一条 `StudyRecord`。
3. 系统保存该单词的停留时长、空闲时长、交互次数等注意力数据。
4. 系统更新对应 `StudyWordProgress`。
5. 系统计算下次复习日期。
6. 系统回写 `StudyDayTask.completedCount`、`completionRate` 和当日注意力汇总。
7. 当天达到完成标准后，`StudyDayTask` 标记为 `COMPLETED`。

### 9.4 当天未学习或未完成

1. 到达当天截止时间后，系统扫描未完成的 `StudyDayTask`。
2. 若从未开始，标记为 `MISSED`，类型记为“未开始”。
3. 若已经开始但未完成，也标记为 `MISSED`，类型记为“未完成”。
4. 未完成的复习任务转为 `OVERDUE`，滚入下一日任务。
5. 学生的 `missedDays` 增加，`currentStreak` 清零。

## 10. 缺勤识别与显示规则

缺勤是本功能的重要价值点，建议明确规则。

### 10.1 缺勤判定

- 仅登录系统不算学习。
- 必须存在有效 `StudyRecord` 才算“已开始学习”。
- 到截止时间仍未达到完成标准，则当天记为缺勤。

### 10.2 缺勤分类

建议拆成两类，方便老师判断问题性质：

- `ABSENT_NOT_STARTED`：当天完全未学
- `ABSENT_UNFINISHED`：当天开始了但没完成

### 10.3 教师端建议展示

- 今日未开始学习人数
- 今日未完成人数
- 昨日缺勤名单
- 连续缺勤 2 天以上名单
- 最近 7 天学习日历

### 10.4 学生端建议展示

- 今日任务状态
- 最近连续学习天数
- 最近一次学习时间
- 最近 7 天日历
- 待补复习数量

## 11. 注意力指标设计

注意力指标用于辅助老师判断学生是不是“真正停留并处理了单词”，而不是只看完成数量。

### 11.1 采集目标

- 记录学生学习每个单词的停留时间
- 区分有效停留和空闲挂起
- 形成学生每日注意力统计
- 给老师提供班级和个人维度的注意力概览

### 11.2 核心指标

- `focusSeconds`：单个单词的有效停留时长
- `idleSeconds`：单个单词页面停留但无有效操作的空闲时长
- `interactionCount`：单词学习期间的交互次数，例如翻面、发音、作答、提交
- `avgFocusSecondsPerWord`：当天平均每词有效停留时长
- `maxFocusSecondsPerWord`：当天单词最大停留时长
- `longStayWordCount`：停留时间超过预警阈值的单词数
- `attentionScore`：综合注意力分

### 11.3 采集口径建议

- 进入单词页面时开始计时。
- 仅在页面可见且学生有持续操作时累加有效停留时长。
- 页面切后台、锁屏、长时间无操作时暂停有效计时。
- 超过 `idleTimeoutSeconds` 的时长记入 `idleSeconds`，不记入 `focusSeconds`。
- 每个单词停留时长建议做上限裁剪，避免页面长时间挂起污染统计。

### 11.4 注意力分建议

首期建议采用规则分，不引入复杂模型。

可参考如下思路：

- 基础分来自有效停留覆盖率
- 有交互行为加分
- 空闲时间占比高则扣分
- 极短停留和极长停留都降权

注意：

- 注意力分仅作为辅助指标，不应直接替代学习效果判断。
- 教师端更应展示原始统计值，避免只给一个抽象分数。

## 12. 与现有模块的关系

### 12.1 与班级模块的关系

学习计划的发布对象应直接复用现有班级体系，不建议重新设计“学习组”。

原因：

- 当前项目已经有 `classrooms`
- 当前项目已经有 `classroom_members`
- 当前项目已经有教师管理班级的权限边界

### 12.2 与词书分配模块的关系

学习计划不是词书分配的替代，而是更上层的教学安排。

建议关系：

- 发布学习计划时，自动确保学生已被分配到对应词书
- 学生可见词书仍然通过现有 `dictionary_assignments` 兜底

### 12.3 与考试模块的关系

考试模块应作为学习计划的补充，而不是复用学习计划表。

建议分工：

- 学习计划：日常新学、复习、打卡、缺勤、掌握进度
- 考试：阶段测验、周测、单元测、成绩统计

原因：

- 当前 `Exam` 模型更适合一次性题目集合
- 学习计划需要长期、细粒度、按单词推进的状态管理

## 13. 数据库设计建议

建议新增一组 Flyway migration，对应以下表。

### 13.1 `study_plans`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `name VARCHAR(255) NOT NULL`
- `description TEXT`
- `teacher_id BIGINT NOT NULL`
- `dictionary_id BIGINT NOT NULL`
- `start_date DATE NOT NULL`
- `end_date DATE`
- `timezone VARCHAR(64) NOT NULL`
- `daily_new_count INT NOT NULL`
- `daily_review_limit INT NOT NULL`
- `review_mode VARCHAR(32) NOT NULL`
- `review_intervals_json JSONB NOT NULL`
- `completion_threshold NUMERIC(5,2) NOT NULL`
- `daily_deadline_time VARCHAR(8) NOT NULL`
- `status VARCHAR(32) NOT NULL`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

### 13.2 `study_plan_classrooms`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `study_plan_id BIGINT NOT NULL`
- `classroom_id BIGINT NOT NULL`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

唯一约束建议：

- `(study_plan_id, classroom_id)`

### 13.3 `student_study_plans`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `study_plan_id BIGINT NOT NULL`
- `student_id BIGINT NOT NULL`
- `status VARCHAR(32) NOT NULL`
- `joined_at TIMESTAMP`
- `completed_days INT NOT NULL DEFAULT 0`
- `missed_days INT NOT NULL DEFAULT 0`
- `current_streak INT NOT NULL DEFAULT 0`
- `last_study_at TIMESTAMP`
- `overall_progress NUMERIC(5,2) NOT NULL DEFAULT 0`
- `avg_focus_seconds NUMERIC(10,2) NOT NULL DEFAULT 0`
- `attention_score NUMERIC(5,2) NOT NULL DEFAULT 0`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

唯一约束建议：

- `(study_plan_id, student_id)`

### 13.4 `study_day_tasks`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_study_plan_id BIGINT NOT NULL`
- `task_date DATE NOT NULL`
- `new_count INT NOT NULL DEFAULT 0`
- `review_count INT NOT NULL DEFAULT 0`
- `overdue_count INT NOT NULL DEFAULT 0`
- `completed_count INT NOT NULL DEFAULT 0`
- `completion_rate NUMERIC(5,2) NOT NULL DEFAULT 0`
- `total_focus_seconds INT NOT NULL DEFAULT 0`
- `avg_focus_seconds_per_word NUMERIC(10,2) NOT NULL DEFAULT 0`
- `max_focus_seconds_per_word INT NOT NULL DEFAULT 0`
- `attention_score NUMERIC(5,2) NOT NULL DEFAULT 0`
- `idle_interrupt_count INT NOT NULL DEFAULT 0`
- `status VARCHAR(32) NOT NULL`
- `started_at TIMESTAMP`
- `completed_at TIMESTAMP`
- `deadline_at TIMESTAMP NOT NULL`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

唯一约束建议：

- `(student_study_plan_id, task_date)`

### 13.5 `study_word_progresses`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_study_plan_id BIGINT NOT NULL`
- `meta_word_id BIGINT NOT NULL`
- `phase INT NOT NULL DEFAULT 0`
- `mastery_level NUMERIC(5,2) NOT NULL DEFAULT 0`
- `next_review_date DATE`
- `last_review_at TIMESTAMP`
- `correct_times INT NOT NULL DEFAULT 0`
- `wrong_times INT NOT NULL DEFAULT 0`
- `total_reviews INT NOT NULL DEFAULT 0`
- `last_result VARCHAR(16)`
- `last_focus_seconds INT`
- `avg_focus_seconds NUMERIC(10,2) NOT NULL DEFAULT 0`
- `max_focus_seconds INT NOT NULL DEFAULT 0`
- `status VARCHAR(32) NOT NULL`

唯一约束建议：

- `(student_study_plan_id, meta_word_id)`

### 13.6 `study_records`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_study_plan_id BIGINT NOT NULL`
- `meta_word_id BIGINT NOT NULL`
- `task_date DATE NOT NULL`
- `action_type VARCHAR(32) NOT NULL`
- `result VARCHAR(16) NOT NULL`
- `duration_seconds INT`
- `focus_seconds INT`
- `idle_seconds INT`
- `interaction_count INT NOT NULL DEFAULT 0`
- `attention_state VARCHAR(32)`
- `stage_before INT`
- `stage_after INT`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

索引建议：

- `(student_study_plan_id, task_date)`
- `(meta_word_id, created_at)`

### 13.7 `student_attention_daily_stats`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_study_plan_id BIGINT NOT NULL`
- `task_date DATE NOT NULL`
- `words_visited INT NOT NULL DEFAULT 0`
- `words_completed INT NOT NULL DEFAULT 0`
- `total_focus_seconds INT NOT NULL DEFAULT 0`
- `avg_focus_seconds_per_word NUMERIC(10,2) NOT NULL DEFAULT 0`
- `median_focus_seconds_per_word NUMERIC(10,2) NOT NULL DEFAULT 0`
- `max_focus_seconds_per_word INT NOT NULL DEFAULT 0`
- `long_stay_word_count INT NOT NULL DEFAULT 0`
- `idle_interrupt_count INT NOT NULL DEFAULT 0`
- `attention_score NUMERIC(5,2) NOT NULL DEFAULT 0`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

唯一约束建议：

- `(student_study_plan_id, task_date)`

## 14. API 设计建议

### 14.1 教师端接口

- `POST /api/study-plans`
- `PUT /api/study-plans/{id}`
- `POST /api/study-plans/{id}/publish`
- `POST /api/study-plans/{id}/pause`
- `POST /api/study-plans/{id}/resume`
- `GET /api/study-plans`
- `GET /api/study-plans/{id}`
- `GET /api/study-plans/{id}/overview`
- `GET /api/study-plans/{id}/students`
- `GET /api/study-plans/{id}/students/{studentId}`
- `GET /api/study-plans/{id}/attendance`
- `GET /api/study-plans/{id}/attention/daily`
- `GET /api/study-plans/{id}/students/{studentId}/attention`

### 14.2 学生端接口

- `GET /api/students/me/study-plans`
- `GET /api/students/me/study-plans/{id}`
- `GET /api/students/me/study-plans/{id}/today`
- `POST /api/students/me/study-plans/{id}/start`
- `POST /api/students/me/study-plans/{id}/records`
- `GET /api/students/me/study-plans/{id}/calendar`
- `GET /api/students/me/study-plans/{id}/progress`
- `GET /api/students/me/study-plans/{id}/attention`

### 14.3 请求响应示意

教师创建计划请求示意：

```json
{
  "name": "高一英语春季复习计划",
  "description": "按艾宾浩斯节奏完成 30 天词汇学习",
  "dictionaryId": 12,
  "classroomIds": [3, 4],
  "startDate": "2026-04-01",
  "endDate": "2026-04-30",
  "timezone": "Asia/Shanghai",
  "dailyNewCount": 20,
  "dailyReviewLimit": 60,
  "reviewMode": "EBBINGHAUS",
  "reviewIntervals": [0, 1, 2, 4, 7, 15],
  "completionThreshold": 100,
  "dailyDeadlineTime": "21:30"
}
```

学生获取今日任务响应示意：

```json
{
  "studentStudyPlanId": 1024,
  "taskDate": "2026-04-05",
  "status": "IN_PROGRESS",
  "summary": {
    "overdueCount": 8,
    "reviewCount": 22,
    "newCount": 20,
    "completedCount": 11,
    "completionRate": 22.0
  },
  "queue": [
    {
      "metaWordId": 3001,
      "word": "abandon",
      "taskType": "OVERDUE_REVIEW",
      "phase": 2
    },
    {
      "metaWordId": 3002,
      "word": "benefit",
      "taskType": "TODAY_REVIEW",
      "phase": 1
    },
    {
      "metaWordId": 3003,
      "word": "capture",
      "taskType": "NEW_LEARN",
      "phase": 0
    }
  ]
}
```

学生提交学习记录请求示意：

```json
{
  "metaWordId": 3003,
  "actionType": "LEARN",
  "result": "CORRECT",
  "durationSeconds": 26,
  "focusSeconds": 22,
  "idleSeconds": 4,
  "interactionCount": 5,
  "attentionState": "FOCUSED"
}
```

老师查看某学生按日注意力统计响应示意：

```json
{
  "studentId": 88,
  "studentName": "张三",
  "planId": 15,
  "dailyStats": [
    {
      "taskDate": "2026-04-05",
      "wordsVisited": 34,
      "wordsCompleted": 28,
      "totalFocusSeconds": 756,
      "avgFocusSecondsPerWord": 27.0,
      "maxFocusSecondsPerWord": 81,
      "longStayWordCount": 3,
      "idleInterruptCount": 5,
      "attentionScore": 78.5
    },
    {
      "taskDate": "2026-04-06",
      "wordsVisited": 30,
      "wordsCompleted": 30,
      "totalFocusSeconds": 702,
      "avgFocusSecondsPerWord": 23.4,
      "maxFocusSecondsPerWord": 49,
      "longStayWordCount": 1,
      "idleInterruptCount": 2,
      "attentionScore": 86.0
    }
  ]
}
```

## 15. 统计指标设计

### 15.1 教师看板指标

- 班级学生总数
- 已加入计划人数
- 今日已完成人数
- 今日未开始人数
- 今日未完成人数
- 今日完成率
- 最近 7 天完成率趋势
- 连续缺勤人数
- 平均连续学习天数
- 人均待复习词数
- 人均掌握词数
- 人均单词停留时长
- 每日注意力分趋势
- 长停留异常学生数
- 单词停留时长最高的学生列表

### 15.2 学生个人指标

- 今日任务完成率
- 当前连续学习天数
- 历史缺勤天数
- 已掌握词数
- 待复习词数
- 逾期词数
- 最近一次学习时间
- 今日总有效停留时长
- 今日平均每词停留时长
- 今日最长单词停留时长
- 今日注意力分
- 最近 7 天注意力趋势

### 15.3 计划质量指标

- 计划发布人数
- 实际激活人数
- 日均学习量
- 计划平均完成率
- 计划中途掉队率
- 错误率最高的单词
- 遗忘回退最多的单词
- 平均每词停留时长
- 注意力异常占比

## 16. 权限设计建议

建议沿用现有权限思想。

### 16.1 教师权限

- 只能管理自己负责班级的学习计划
- 只能查看自己学生的学习进度和缺勤情况
- 只能绑定自己可访问的词书

### 16.2 学生权限

- 只能查看自己的学习计划
- 只能提交自己的学习记录
- 不能查看其他学生数据

### 16.3 管理员权限

- 可查看全部计划
- 可查看全部学习记录
- 可用于运营排查和数据修复

## 17. 技术实现建议

### 17.1 任务生成策略

建议采用“按日惰性生成 + 到点补偿”的方式。

原因：

- 可以减少一次性预生成大量任务的成本
- 支持老师中途暂停、调整、补发
- 更容易处理学生迟到加入、班级成员变化

建议逻辑：

- 学生第一次进入某天计划时，若当天任务不存在，则动态生成
- 系统每日定时任务扫描昨日未完成任务，更新为 `MISSED`
- 系统在生成今日任务时自动合并逾期复习

### 17.2 注意力采集实现建议

建议由前端按“进入单词 -> 发生交互 -> 离开单词/提交结果”的事件流上报，后端负责落库和聚合。

建议规则：

- 前端记录单词进入时间和最近一次活跃时间
- 用户交互事件用于刷新活跃时间
- 页面隐藏、切后台、长时间无操作时停止累加有效时长
- 提交记录时同时上报 `focusSeconds`、`idleSeconds`、`interactionCount`
- 后端负责二次校验，防止异常值直接入库

### 17.3 注意力统计聚合建议

建议在两层做聚合：

- 实时聚合到 `study_day_tasks`
- 日级聚合到 `student_attention_daily_stats`

这样可以同时满足：

- 学生学习中实时显示今日停留统计
- 老师按天查看学生注意力数据
- 后续低成本做班级趋势报表

### 17.4 学习记录粒度

建议以“单词级行为记录”为主，而不是只记一次整天打卡。

原因：

- 能精确计算复习阶段
- 能支撑回退规则
- 能支撑错误词统计
- 能支撑单词停留时长统计
- 能支持未来做学习分析

### 17.5 异常值处理建议

为了让停留时间可用，建议明确异常值处理规则：

- 小于 `minFocusSecondsPerWord` 的记录记为“极短停留”，不直接算高质量学习
- 大于 `maxFocusSecondsPerWord` 的记录按封顶值计入
- 单词页面切后台后累计的时间不算有效停留
- 网络重试导致的重复上报应幂等去重

### 17.6 难度适配

当前 `MetaWord` 已有 `difficulty` 字段，后续可以利用该字段做增强：

- 新词排序时优先简单词
- 掌握度低且难度高的词可增加复习频次
- 初期可以先不启用自适应，只保留字段兼容

## 18. 风险与边界

### 18.1 数据量风险

如果词书很大、班级人数很多，`StudyWordProgress` 可能增长很快。

建议：

- 只为进入计划范围的单词生成进度记录
- 用唯一索引保证去重
- 为高频查询字段建立索引

### 18.2 时间边界风险

不同地区和跨午夜学习可能导致“哪一天算完成”出现歧义。

建议：

- 在计划层面固定 `timezone`
- 每日任务按计划时区计算，不按服务器时区计算

### 18.3 教师调整计划的风险

老师发布后又调整每日新词数、复习曲线，可能影响已生成任务。

建议：

- 发布后只允许调整部分参数
- 对已生成的历史任务不回溯修改
- 对未生成日期采用新配置

### 18.4 缺勤定义争议

有些学生可能只学了几分钟，老师会认为不算完成。

建议：

- 区分“已开始”和“已完成”
- 对完成设置清晰阈值
- 老师端同时展示两类状态

### 18.5 注意力指标误判风险

单词停留时间不一定完全等于认真程度，可能受网络、页面挂起、学生发呆等因素影响。

建议：

- 优先展示原始停留时长和空闲占比
- 注意力分只作辅助，不直接作为考核唯一依据
- 对异常长停留做截断和标记
- 前端和后端都做异常值过滤

## 19. 分阶段实施建议

### 19.1 第一阶段

目标：

- 教师创建计划并发布到班级
- 学生查看并进入今日任务
- 支持基础新学和复习
- 支持缺勤识别和学习日历
- 支持班级完成率统计
- 支持单词停留时长采集和老师按天查看学生停留统计

### 19.2 第二阶段

目标：

- 支持完整艾宾浩斯间隔配置
- 支持错词回退
- 支持逾期补学
- 支持更完整的班级报表
- 支持注意力分和异常停留识别

### 19.3 第三阶段

目标：

- 支持计划模板库
- 支持按学生表现自动调节负载
- 支持与考试联动生成阶段测验
- 支持学习分析报告

## 20. MVP 建议

如果希望尽快落地，MVP 建议只做以下能力：

- 教师按班级创建并发布学习计划
- 计划绑定词书
- 学生查看今日任务并学习
- 系统记录当天是否完成
- 教师查看缺勤和完成率
- 默认使用固定一套艾宾浩斯间隔模板
- 记录每个单词有效停留时长
- 教师查看学生每日单词停留统计

暂不纳入 MVP：

- 自适应难度
- 老师中途批量重排计划
- 多模板混合
- 高级学习分析报表

## 21. 结论

学习计划功能应被设计为“建立在班级和词书分配之上的长期学习编排层”。

它和当前系统的关系应当是：

- 班级模块负责“人”
- 词书模块负责“内容”
- 学习计划模块负责“节奏”
- 考试模块负责“测验”

从当前代码基础看，这一设计方向与现有项目结构兼容，且具备明确的增量演进路径，适合作为后续一期功能设计基础。
