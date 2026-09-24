# StudyCompanion 学习伴读移动端账号设计

## 1. 文档目标

本文档用于设计 StudyCompanion（伴读社区）手机版前端的首页、学生注册、老师注册、登录、伴读社区、伴读管理、伴读详情、编辑伴读和二维码页面，并明确它们与当前单词学习系统账号体系及原班级管理数据的关系。

本文档记录设计与当前实现约定。数据库、后端接口和前端实现均按本文档落地，后续变更仍需同步更新本文档。除特别说明外，本文档中的新增页面均指手机版浏览页面。

- 学生注册效果图：`docs/design/2学生注册.png`
- 老师注册效果图：`docs/design/3老师注册.png`
- 登录效果图：`docs/design/4登录.png`
- 当前用户表：`users`
- 当前认证接口：`/api/auth/login`、`/api/auth/logout`、`/api/auth/me`

核心结论：学生和老师继续使用原系统 `users` 表，不新增独立的学生表或老师表。昵称直接写入现有 `users.display_name`，其余注册资料作为同一张表的可空扩展字段保存。

## 2. 设计范围

### 2.1 本期范围

1. 手机版首页、登录、学生注册、老师注册页面。
2. 手机版伴读社区、伴读管理、伴读详情、编辑伴读和二维码页面。
3. 学生和老师账号创建。
4. 用户资料字段扩展。
5. 注册后登录态建立和角色路由。
6. 原 `classrooms` 班级表扩展伴读资料。
7. 复用原班级群聊中的负责人选定视频展示伴读视频列表。
8. 已有管理员、老师、学生账号和原班级管理功能的数据兼容。

### 2.2 不在本期范围

1. 手机号短信验证码注册。
2. 邮箱验证和找回密码。
3. 第三方登录。
4. 老师资质审核流程。
5. 学校、年级、兴趣标签的独立运营后台。
6. 头像上传到对象存储；本期头像采用内置头像标识。
7. 学生与老师的班级关系自动建立。
8. 新建独立的伴读班级表、伴读视频列表表或移动端专用登录接口。

## 3. 当前系统现状

### 3.1 用户表

当前 `users` 表由 `V9__add_user_role_support.sql` 创建，已有字段：

| 字段 | 类型 | 当前用途 | 约束 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 用户主键 | 主键、自增序列 |
| `username` | `VARCHAR(100)` | 登录用户名 | 非空、唯一 |
| `password_hash` | `VARCHAR(255)` | 加密后的登录密码 | 非空，不保存明文 |
| `display_name` | `VARCHAR(100)` | 页面显示名称 | 非空；本需求中的“昵称”写入此字段 |
| `email` | `VARCHAR(255)` | 邮箱 | 当前可空 |
| `phone` | `VARCHAR(50)` | 手机号 | 当前可空 |
| `role` | `VARCHAR(20)` | 账号角色 | `ADMIN`、`TEACHER`、`STUDENT` |
| `status` | `VARCHAR(20)` | 账号状态 | `ACTIVE`、`DISABLED`、`LOCKED` |
| `created_at` | `TIMESTAMP` | 创建时间 | 自动写入 |
| `updated_at` | `TIMESTAMP` | 修改时间 | 自动更新 |
| `last_login_at` | `TIMESTAMP` | 最近登录时间 | 登录成功后更新 |

当前 `AppUser` 实体已经映射该表，`UserResponse` 已向前端返回基础用户信息。当前用户创建接口为
`POST /api/users`，由管理员调用；本需求不新增注册 URL，而是扩展该原有创建用户接口，使移动端两个注册页面复用同一个接口。

### 3.2 现有角色

```text
ADMIN
TEACHER
STUDENT
```

注册页面只允许创建 `TEACHER` 或 `STUDENT`。`ADMIN` 只能由管理员后台创建或由受控迁移产生，禁止通过公开注册请求传入。

## 4. 页面设计

### 4.1 移动端共同视觉语言

三张效果图形成同一套移动端账号体验：

- 顶部使用蓝色品牌区域和返回按钮。
- 主体为纵向滚动页面，适配窄屏设备。
- 表单使用大字号标签、大点击区域和清晰的必填星号。
- 主操作使用蓝色实心按钮，取消操作使用浅色描边或浅色按钮。
- 输入框左侧使用语义图标，密码框右侧提供显示/隐藏切换。
- 页面底部提供角色切换和已有账号登录入口。
- 学生注册页和老师注册页是两个独立页面，分别维护自己的表单和角色隐藏域。两页可以复用基础输入组件，但不能合并成一个页面通过下拉框选择角色。

页面不得把管理员注册入口暴露给移动端用户。

### 4.2 学生注册页

建议路由：`/register/student`

| 页面元素 | 是否必填 | 数据字段 | 交互规则 |
| --- | --- | --- | --- |
| 用户名 | 是 | `username` | 只能输入允许的登录名格式，提交前提示是否已存在 |
| 密码 | 是 | `password` | 至少 6 位；输入时支持显示/隐藏 |
| 确认密码 | 是 | `confirmPassword` | 前端校验必须与密码一致，不提交到数据库 |
| 昵称 | 是 | `displayName` | 对应现有 `users.display_name` |
| 选择头像 | 否 | `avatarKey` | 从内置学生头像集合单选，默认第一项 |
| 性别 | 否 | `gender` | `MALE`、`FEMALE`、`UNSPECIFIED`；效果图默认男，但设计上允许不选择 |
| 学校 | 否 | `schoolName` | 普通文本，保存学校名称 |
| 年级 | 是 | `grade` | 使用枚举或受控选项，不接受任意自由文本 |
| 标签 | 否 | `interestTags` | 多选；学生兴趣标签，例如语文、数学、英语、编程、阅读 |
| 注册 | - | - | 校验成功后创建 `STUDENT` 账号并进入学生首页 |
| 取消 | - | - | 返回登录页或上一页，不产生账号 |

学生注册页底部提供：

- “我是老师？去老师注册”跳转 `/register/teacher`
- “已有账号？直接登录”跳转 `/login`

### 4.3 老师注册页

建议路由：`/register/teacher`

| 页面元素 | 是否必填 | 数据字段 | 交互规则 |
| --- | --- | --- | --- |
| 用户名 | 是 | `username` | 与学生注册使用同一套用户名规则和唯一性校验 |
| 密码 | 是 | `password` | 至少 6 位；输入时支持显示/隐藏 |
| 确认密码 | 是 | `confirmPassword` | 前端校验必须与密码一致，不提交到数据库 |
| 昵称 | 是 | `displayName` | 对应现有 `users.display_name`；示例可使用“王老师” |
| 选择头像 | 否 | `avatarKey` | 从内置老师头像集合单选，默认第一项 |
| 性别 | 否 | `gender` | `MALE`、`FEMALE`、`UNSPECIFIED` |
| 教学学段 | 是 | `teachingStage` | 使用受控选项，例如小学、初中、高中、大学、成人教育 |
| 擅长领域 | 否 | `expertiseTags` | 多选，顺序为英语、数学、语文、编程、竞赛 |
| 注册 | - | - | 校验成功后创建 `TEACHER` 账号并进入老师首页 |
| 取消 | - | - | 返回登录页或上一页，不产生账号 |

老师注册页不展示学校字段；`school_name` 仅作为用户表的可选兼容资料字段保留，管理员创建用户时可按需填写。老师注册页底部提供：

- “我是学生？去学生注册”跳转 `/register/student`
- “已有账号？直接登录”跳转 `/login`

### 4.4 登录页

建议路由：`/login`

| 页面元素 | 数据 | 交互规则 |
| --- | --- | --- |
| 用户名 | `username` | 必填，使用现有登录接口 |
| 密码 | `password` | 必填，支持显示/隐藏 |
| 登录 | - | 调用 `POST /api/auth/login`，学生和老师成功后统一跳转 `/community` |
| 取消 | - | 返回上一页或首页 |
| 学生注册 | - | 跳转 `/register/student` |
| 老师注册 | - | 跳转 `/register/teacher` |

角色跳转建议：

| 角色 | 默认入口 |
| --- | --- |
| `ADMIN` | 原系统管理后台首页，保持原系统登录成功流程 |
| `TEACHER` | `/community`，登录后在社区页显示“老师管理”入口 |
| `STUDENT` | `/community` |

登录仍使用现有 `POST /api/auth/login`，不复制一套移动端登录认证逻辑。

学生和老师的移动端登录成功处理必须明确区分于原系统登录页：移动端 `/login` 成功后固定进入 `/community`；原系统登录页及其原有 `/admin/` 跳转行为保持不变。

## 5. 数据模型设计

### 5.1 在 `users` 表上增加字段

本需求不新增用户子表，直接在 `users` 表上追加以下字段：

| 新字段 | 建议类型 | 是否必填 | 说明 |
| --- | --- | --- | --- |
| `avatar_key` | `VARCHAR(100)` | 否 | 内置头像标识，例如 `student-panda`、`teacher-book`；不保存二进制图片 |
| `gender` | `VARCHAR(20)` | 否 | `MALE`、`FEMALE`、`UNSPECIFIED` |
| `school_name` | `VARCHAR(255)` | 否 | 学生学校名称；老师也可填写所属学校 |
| `grade` | `VARCHAR(50)` | 否 | 学生年级编码，例如 `PRIMARY_3`、`JUNIOR_2`、`SENIOR_1` |
| `interest_tags_json` | `TEXT` 或 `JSONB` | 否 | 学生兴趣标签数组；短期采用 JSON 数组保存 |
| `teaching_stage` | `VARCHAR(50)` | 否 | 老师教学学段编码，例如 `PRIMARY`、`JUNIOR_HIGH`、`SENIOR_HIGH` |
| `expertise_tags_json` | `TEXT` 或 `JSONB` | 否 | 老师擅长领域数组；短期采用 JSON 数组保存 |

推荐使用 `JSONB` 保存两个标签字段。若当前数据库迁移规范仍统一使用 `TEXT` 保存 JSON，则使用 `TEXT`，由 DTO 层以数组形式读写，不能让前端直接拼接 JSON 字符串。

### 5.2 字段与角色的使用边界

| 字段 | `ADMIN` | `TEACHER` | `STUDENT` |
| --- | --- | --- | --- |
| `avatar_key` | 可选 | 可选 | 可选 |
| `gender` | 可选 | 可选 | 可选 |
| `school_name` | 可选 | 可选 | 可选 |
| `grade` | 不使用 | 不使用 | 注册时可选/按产品要求必填 |
| `interest_tags_json` | 不使用 | 不使用 | 可选 |
| `teaching_stage` | 不使用 | 注册时必填 | 不使用 |
| `expertise_tags_json` | 不使用 | 可选 | 不使用 |

这些字段允许为空，以兼容已有管理员和历史账号。角色专属字段的业务校验在注册服务中完成，不建议给数据库增加按角色的复杂 `CHECK` 约束。

### 5.3 推荐枚举值

`gender`：

```text
MALE
FEMALE
UNSPECIFIED
```

学生 `grade` 示例：

```text
PRIMARY_1, PRIMARY_2, PRIMARY_3, PRIMARY_4, PRIMARY_5, PRIMARY_6
JUNIOR_1, JUNIOR_2, JUNIOR_3
SENIOR_1, SENIOR_2, SENIOR_3
UNIVERSITY, ADULT
```

老师 `teaching_stage` 示例：

```text
PRIMARY
JUNIOR_HIGH
SENIOR_HIGH
UNIVERSITY
ADULT
OTHER
```

标签值必须使用前端和后端共享的稳定编码，页面显示名称由前端字典或后端配置转换，不把中文显示文本作为长期业务主键。

## 6. 数据库迁移设计

新增 Flyway migration，例如：

```text
V45__add_study_companion_user_profile.sql
```

实际版本号必须以仓库当前最高 migration 版本为准，不能与已有版本重复。

迁移建议：

```sql
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS gender VARCHAR(20),
    ADD COLUMN IF NOT EXISTS school_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS grade VARCHAR(50),
    ADD COLUMN IF NOT EXISTS interest_tags_json JSONB,
    ADD COLUMN IF NOT EXISTS teaching_stage VARCHAR(50),
    ADD COLUMN IF NOT EXISTS expertise_tags_json JSONB;

CREATE INDEX IF NOT EXISTS idx_users_teaching_stage ON users(teaching_stage);
CREATE INDEX IF NOT EXISTS idx_users_grade ON users(grade);
```

如果项目最终决定使用 `TEXT` JSON 字段，则将两个 `JSONB` 字段改为 `TEXT`，并统一由后端序列化和反序列化。

迁移要求：

1. 所有新字段可空，迁移不能影响既有账号登录。
2. 不修改既有 `username`、`password_hash`、`role`、`status` 数据。
3. 不删除或重建 `users` 表。
4. 不自动猜测历史用户的性别、学校、年级或标签。
5. 默认头像由应用层在返回资料时补齐，不必回写数据库。

## 7. 后端接口设计

### 7.1 复用原有创建用户接口

学生注册页和老师注册页都调用原系统的：

```http
POST /api/users
Content-Type: application/json
```

不新增以下接口：

```text
/api/auth/register/student
/api/auth/register/teacher
```

移动端页面只负责收集资料，并在请求中提交隐藏的 `role` 字段。隐藏域不是安全边界，后端仍必须校验角色白名单。

### 7.2 学生注册页请求

```json
{
  "username": "student001",
  "password": "******",
  "displayName": "小明",
  "role": "STUDENT",
  "avatarKey": "student-panda",
  "gender": "MALE",
  "schoolName": "实验中学",
  "grade": "SENIOR_1",
  "interestTags": ["ENGLISH", "READING"]
}
```

页面规则：

- `role` 由学生注册页面写入隐藏域，值固定为 `STUDENT`。
- 页面不显示角色选择控件，用户不能在页面上切换成老师或管理员。
- 请求仍发送到 `POST /api/users`。

后端规则：

```text
role = STUDENT
status = ACTIVE
display_name = request.displayName
```

### 7.3 老师注册页请求

```json
{
  "username": "teacher001",
  "password": "******",
  "displayName": "王老师",
  "role": "TEACHER",
  "avatarKey": "teacher-book",
  "gender": "FEMALE",
  "teachingStage": "SENIOR_HIGH",
  "expertiseTags": ["ENGLISH", "CLASS_TEACHER"]
}
```

页面规则：

- `role` 由老师注册页面写入隐藏域，值固定为 `TEACHER`。
- 页面不显示角色选择控件，用户不能在页面上切换成学生或管理员。
- 请求仍发送到 `POST /api/users`。

后端规则：

```text
role = TEACHER
status = ACTIVE
display_name = request.displayName
```

### 7.4 原有创建用户接口的权限分流

当前 `POST /api/users` 作为学生和老师注册的统一创建接口。为兼容后台创建用户和移动端注册，接口按角色进行权限分流，但 URL 和核心用户创建服务保持不变：

| 调用者 | 允许的 `role` | 处理规则 |
| --- | --- | --- |
| 未登录移动端注册页 | `STUDENT`、`TEACHER` | 允许创建，强制 `status = ACTIVE`，不得创建管理员 |
| 已登录管理员 | `ADMIN`、`TEACHER`、`STUDENT` | 保留原管理员创建用户能力 |
| 已登录老师/学生 | `STUDENT`、`TEACHER` | 允许继续注册学生或老师账号；不能创建管理员 |

未登录或已登录的非管理员请求只能使用注册允许的资料字段，不能通过请求体覆盖 `status`、创建时间、登录时间或其他系统字段。后端必须拒绝 `role = ADMIN`，不能因为页面使用隐藏域就信任客户端传值。

创建成功后接口返回现有 `UserResponse`。注册成功后跳转 `/login`，用户使用新账号调用现有 `POST /api/auth/login`；本期不在创建用户接口中额外签发 token。

### 7.5 当前用户资料

现有接口 `GET /api/auth/me` 扩展返回新字段：

```json
{
  "id": 12,
  "username": "student001",
  "displayName": "小明",
  "email": null,
  "phone": null,
  "avatarKey": "student-panda",
  "gender": "MALE",
  "schoolName": "实验中学",
  "grade": "SENIOR_1",
  "interestTags": ["ENGLISH", "READING"],
  "teachingStage": null,
  "expertiseTags": [],
  "role": "STUDENT",
  "status": "ACTIVE"
}
```

旧客户端只使用已有字段时应继续正常工作。

## 8. 请求校验与错误处理

### 8.1 通用校验

1. `username` 非空，长度建议 3–100 个字符，必须符合登录名格式。
2. `password` 非空，至少 6 位；禁止返回或记录明文密码。
3. `displayName` 非空，长度建议 1–100 个字符。
4. `confirmPassword` 只在前端使用，后端可以接收后校验，但不得持久化。
5. `username` 已存在时返回 `409 Conflict`，提示“用户名已存在”。
6. 未登录请求的 `role` 只能是 `STUDENT` 或 `TEACHER`；不允许 `ADMIN`。
7. 标签去重、去除空值，并限制单次数量和单项长度。

### 8.2 学生专属校验

1. `grade` 必须来自允许的年级编码。
2. `interestTags` 中的值必须来自学生标签字典。
3. `teachingStage` 和 `expertiseTags` 即使被传入也应忽略或拒绝，避免角色字段混用。

### 8.3 老师专属校验

1. `teachingStage` 必填且必须来自教学学段字典。
2. `expertiseTags` 中的值必须来自老师领域字典。
3. `grade` 和 `interestTags` 即使被传入也应忽略或拒绝。

### 8.4 错误响应

沿用现有全局错误响应格式，至少覆盖：

| 场景 | HTTP 状态 | 前端提示 |
| --- | --- | --- |
| 参数缺失 | `400` | 请完善必填信息 |
| 密码不一致 | `400` | 两次输入的密码不一致 |
| 用户名已存在 | `409` | 用户名已存在，请更换 |
| 标签非法 | `400` | 请选择有效的标签 |
| 注册失败 | `500` | 注册失败，请稍后重试 |
| 登录失败 | 现有认证错误 | 用户名或密码错误 |

## 9. 后端代码改动范围

实现阶段建议按以下模块拆分：

### 9.1 模型和 DTO

- `AppUser` 增加新字段及 JSON 标签映射。
- 新增 `Gender`、`TeachingStage` 等枚举，或使用受控字符串编码。
- 扩展原有 `CreateUserRequest`，增加头像、性别、学校、年级、标签、教学学段和擅长领域字段。
- 不新增 `StudentRegisterRequest` 或 `TeacherRegisterRequest`，避免产生两套创建用户请求模型。
- 扩展 `UserResponse`。

### 9.2 服务和控制器

- 保留 `UserController` 的 `POST /api/users` URL，扩展其未登录注册分流和角色白名单校验。
- 保留 `UserService.createUser(CreateUserRequest)` 作为唯一用户创建路径，补充新资料字段的赋值。
- 将当前接口授权改为“匿名或已登录用户均可创建学生/老师，管理员可创建全部角色”的权限分流。
- 注册事务内创建用户，失败时整体回滚。
- 后端不得仅依赖前端隐藏域判断角色。

### 9.3 数据访问

- 复用 `AppUserRepository` 的用户名查询能力。
- 若当前仓库没有按用户名查询方法，增加 `findByUsername` 或 `existsByUsername`。
- 不新增学生表、老师表、头像表或标签关联表。

## 10. 移动端前端设计

### 10.1 路由

建议在统一前端中增加以下公开路由：

```text
/login
/register/student
/register/teacher
```

这些路由不应被登录态保护。登录后访问登录或注册页时，根据当前登录状态跳转到角色首页。

### 10.2 组件建议

共享组件：

- `MobileAuthLayout`
- `AuthHeader`
- `AuthTextField`
- `PasswordField`
- `AvatarPicker`
- `GenderSegment`
- `TagPicker`
- `SelectField`
- `AuthSubmitButton`

页面组件：

- `StudentRegisterPage`
- `TeacherRegisterPage`
- `LoginPage`

学生页和老师页共享表单状态与基础校验，但不要通过一套包含大量条件分支的表单组件隐藏角色差异；角色专属字段应由页面配置显式表达。

### 10.3 表单状态

提交过程至少包含：

```text
idle -> validating -> submitting -> success
                         -> error
```

提交期间：

- 禁用注册按钮，避免重复创建账号。
- 保留用户已填写内容。
- 密码字段不写入 localStorage、URL 或错误日志。
- 服务端返回错误时优先展示字段级错误，无法定位时展示页面级错误。

### 10.4 移动端适配

1. 页面宽度以 `100dvw` 为基础，表单内容保留左右安全边距。
2. 主按钮和取消按钮高度不低于 48px。
3. 输入框字号不小于 16px，避免移动浏览器自动放大。
4. 头像、性别、标签采用可触摸的单选或多选控件。
5. 长表单支持页面滚动，键盘弹出时当前输入框必须保持可见。
6. 标签过多时允许横向滚动或换行，但不能让页面宽度溢出。
7. 页面不能依赖 hover 才能发现密码显示按钮或选中状态。

## 11. 安全与隐私

1. 密码使用现有 BCrypt 方案加密保存。
2. 注册请求和日志中禁止记录密码、确认密码和 token。
3. `UserResponse` 不返回 `password_hash`。
4. `avatar_key` 只允许白名单值，避免通过字段注入外部图片地址。
5. 学校、性别、年级和标签属于个人资料，默认只返回本人及有权限的管理接口。
6. 老师不能通过创建用户接口创建管理员或修改已有账号角色。
7. 匿名创建用户接口应增加基础限流和失败次数保护，避免被用于用户名枚举或暴力创建账号。
8. 生产环境必须使用 HTTPS，登录 token 按现有安全 cookie/JWT 方案处理。

## 12. 与现有业务的兼容性

### 12.1 已有账号

已有账号继续使用原字段登录。新增资料字段为空时：

- 头像使用对应角色默认头像。
- 性别展示为“未设置”。
- 学校、年级、标签显示为空状态。
- 老师未设置教学学段时，不阻塞已有老师使用旧功能；只有新注册老师必须填写。

### 12.2 现有接口

1. `POST /api/users` 仍是唯一创建用户接口，学生页和老师页都调用它。
2. `POST /api/auth/login` 请求格式不变。
3. `POST /api/auth/logout` 行为不变。
4. `GET /api/auth/me` 向响应中追加字段，不删除或重命名已有字段。
5. 管理员用户列表接口如返回 `UserResponse`，应同步返回新资料字段，但不得改变分页和权限行为。
6. 班级、学习计划、词书分配仍使用 `users.id`、`role` 和既有关系表，不改外键关系。

### 12.3 角色资料与业务资料的边界

学校、年级、教学学段和标签只表示注册资料，不代替既有业务关系：

- 学生是否属于某班级，仍由 `classroom_members` 表决定。
- 老师是否管理某班级，仍由 `classrooms.teacher_id` 决定。
- 老师与学生的教学关系，仍由 `teacher_student_relations` 或班级关系表达。
- 注册时填写的学校名称不自动创建或绑定学校组织。

## 13. 验收标准

### 13.1 学生注册

- 可以从登录页进入学生注册页。
- 必填项缺失时不能提交。
- 两次密码不一致时不能提交。
- 学生注册成功后，`users.role = 'STUDENT'`。
- 昵称保存到 `users.display_name`。
- 头像、性别、学校、年级、标签正确写入用户资料字段。
- 注册请求调用的是 `POST /api/users`，不是新增的学生注册接口。
- 注册成功后跳转登录页。

### 13.2 老师注册

- 可以从登录页进入老师注册页。
- 教学学段缺失时不能提交。
- 老师注册成功后，`users.role = 'TEACHER'`。
- 昵称保存到 `users.display_name`。
- 头像、性别、教学学段、擅长领域正确写入用户资料字段；老师注册页面不提交学校字段。
- 注册请求调用的是 `POST /api/users`，不是新增的老师注册接口。
- 老师不能通过请求参数创建管理员账号。

### 13.3 登录和兼容

- 新注册学生可以使用用户名密码登录。
- 新注册老师可以使用用户名密码登录。
- 原有管理员、老师、学生账号仍可以登录。
- `GET /api/auth/me` 能返回新增资料字段。
- 移动端登录后按角色进入正确首页。

### 13.4 安全

- 数据库中不存在明文密码。
- 错误日志和接口响应不泄露密码。
- 重复用户名返回明确的 `409` 错误。
- 未登录用户不能访问受保护业务接口。

## 14. 实施顺序

1. 确认字段编码、标签字典和默认头像清单。
2. 新增数据库 migration，使用当前仓库最高版本后的唯一版本号。
3. 扩展 `AppUser`、`CreateUserRequest` 和 `UserResponse`。
4. 改造原有 `POST /api/users` 的匿名注册分流和接口测试。
5. 实现两个独立的移动端学生注册、老师注册页面，并分别提交隐藏角色域。
6. 实现移动端登录页面，注册成功后跳转登录页。
7. 增加页面级和接口级校验测试。
8. 用已有账号执行兼容性回归。
9. 构建并启动三个容器，验证 Flyway、登录和用户创建流程。

## 15. 待确认事项

以下事项不影响本文档的主数据模型，但开发前需要产品确认：

1. 学校字段是否允许老师填写，还是只对学生显示。
2. 学生“年级”是否必须填写；效果图标记为必填，本文档按必填设计。
3. 老师注册是否需要管理员审核。
4. 注册成功后是否直接登录，还是跳回登录页。
5. 头像是否长期只使用内置头像，还是下一期增加上传头像。
6. 标签是固定枚举还是后续由管理员维护。
7. `interest_tags_json` 和 `expertise_tags_json` 使用 `JSONB` 还是项目统一的 `TEXT` JSON 格式。

## 16. StudyCompanion 移动端页面扩展范围

本节补充首页、伴读社区、伴读管理、伴读详情、编辑伴读和二维码页面的设计。下列页面全部是手机版前端浏览页面，不能按后台桌面端页面实现，也不能要求用户横向滚动查看主要内容。

对应效果图：

- 首页：`docs/design/1首页.png`
- 登录：`docs/design/4登录.png`
- 伴读社区：`docs/design/5伴读社区.png`
- 伴读管理：`docs/design/6伴读管理.png`
- 伴读详情：`docs/design/7伴读详情.png`
- 编辑伴读：`docs/design/8编辑伴读.png`
- 二维码：`docs/design/二维码.png`

已有页面也纳入同一套移动端范围：

- 学生注册：`docs/design/2学生注册.png`
- 老师注册：`docs/design/3老师注册.png`

### 16.1 统一头部

所有新增页面和学生注册、老师注册页面使用同一套蓝色头部规范：

1. 蓝色头部宽度与页面主体输入区域的内容宽度一致，不铺满桌面视口；在手机视口中占满可用内容宽度。
2. 头部包含返回按钮、当前页面标题和伴读社区品牌标识，具体标题随页面变化。
3. 头部高度、圆角、品牌区位置、蓝色背景和安全区处理以学生注册页效果图为基准。
4. 页面主体继续使用纵向滚动；头部不能遮挡表单、列表或底部操作栏。
5. 不能继续显示英文页面名或英文头像标识；用户可见文案使用中文。
6. 头部的返回按钮返回上一页；从首页进入的页面在无历史记录时回到首页。

### 16.2 页面和路由总表

| 页面 | 建议路由 | 登录要求 | 主要用途 |
| --- | --- | --- | --- |
| 首页 | `/` | 否 | 展示 StudyCompanion 入口和注册、登录、二维码入口 |
| 登录 | `/login` | 否 | 使用统一登录接口登录移动端用户 |
| 学生注册 | `/register/student` | 否 | 创建 `STUDENT` 用户，已在前文定义 |
| 老师注册 | `/register/teacher` | 否 | 创建 `TEACHER` 用户，已在前文定义 |
| 伴读社区 | `/community` | 是 | 查看当前用户可访问的伴读班级列表 |
| 伴读管理 | `/community/manage` | 是，老师或管理员 | 管理自己负责或管理员可管理的伴读班级 |
| 伴读详情 | `/community/:classroomId` | 是 | 查看班级介绍、标签、图片和伴读视频列表 |
| 编辑伴读 | `/community/:classroomId/edit` | 是，负责人老师或管理员 | 修改伴读班级资料 |
| 二维码 | `/qr` | 否 | 展示共享二维码及返回入口 |

路由名称是前端约定，实际项目可按现有路由组织调整，但页面职责和权限不得改变。

## 17. 首页、登录和二维码页

### 17.1 首页

首页按 `docs/design/1首页.png` 实现为手机版欢迎页，首页只负责导航，不复制认证和班级业务逻辑。首屏使用原型中的蓝紫色视觉背景、白色标题与副标题、右上角语言入口、开放书本插图及底部波形过渡，不再复用登录、伴读详情等页面的通用头部。Hero 区域下方保留四个入口卡片，并保持卡片的触摸反馈和原有跳转地址。

| 入口 | 跳转 |
| --- | --- |
| 学生注册 | `/register/student` |
| 老师注册 | `/register/teacher` |
| 登录 | `/login` |
| 二维码共享 | `/qr` |

如果当前用户已经登录，首页可以显示进入伴读社区的入口，但不能因为首页展示了入口就绕过统一认证。首页按钮需要支持触摸操作、加载状态和路由失败提示。

### 17.2 新移动端登录页

新登录页使用 `docs/design/4登录.png` 的手机版布局，但调用原系统统一登录接口：

```http
POST /api/auth/login
Content-Type: application/json
```

约束如下：

1. 新登录页是新前端页面，原系统已有登录页保持不变，不修改原页面布局、路由或成功页。
2. 请求体和认证方式沿用原登录接口，不新增移动端登录接口，不复制 token、session 或密码校验逻辑。
3. 登录成功后新页面固定跳转 `/community`，不跳转原系统登录成功页。
4. 登录失败沿用统一错误处理，页面显示“用户名或密码错误”等用户可理解的中文提示。
5. 已登录用户访问 `/login` 时可直接跳转 `/community`，避免重复登录。
6. `ADMIN` 登录后也先进入 `/community`；后台管理入口由既有系统导航继续提供，不在新登录页中改变管理员权限。

### 17.3 二维码页

二维码页使用 `docs/design/二维码.png` 的手机版布局，去掉顶部蓝色头部区域，直接以背景图铺满页面；真实二维码固定放在背景图右下角。页面不显示“扫描二维码，进入伴读社区”说明文字，也不显示 `http://124.174.44.175` 文本。页面配置保存在 `qr_page_settings` 单例记录中，管理员登录后可以编辑“伴读社区”文本并上传替换背景图，保存后所有访问者看到最新配置；非管理员只能查看和扫描。

二维码内容至少包含：

- 固定的公开入口 `http://124.174.44.175`，不使用浏览器当前 origin，确保微信扫描后跳转到指定公网地址；
- 由后端使用真实二维码编码生成的 PNG 图像，前端不得使用伪随机方格代替二维码；
- 不包含密码、登录 token 或用户隐私资料。

二维码页公开可访问，但若检测到当前登录用户为管理员，则显示编辑文本、更换背景图和保存控件。背景图上传复用伴读图片上传接口并保存图片地址；二维码生成失败时显示可重试状态。二维码页面的配置接口为 `GET /api/qr`，管理员保存使用 `PUT /api/qr`，后端只允许 `ADMIN` 更新。

## 18. 伴读社区页面

### 18.1 伴读社区列表

伴读社区页面使用 `docs/design/5伴读社区.png` 的手机版布局。页面展示所有未归档的伴读班级，不按登录人过滤，不新建“伴读班级”数据表。

访问范围：

| 用户 | 可见班级 |
| --- | --- |
| 管理员 | 原班级管理中所有未归档班级 |
| 老师 | `teacher_id` 等于当前用户的未归档班级 |
| 学生 | 通过 `classroom_members` 加入的未归档班级 |

社区浏览卡片展示：班级名称、负责人/老师名称、老师注册时保存的擅长领域、班级介绍摘要、伴读标签、介绍图片缩略图、评论数和点赞数，不显示“`N 名学生`”；评论数使用原型图中的无外圈对话气泡图标，点赞使用原型图中的红色实心拇指向上图标，二者位于每条卡片的伴读标签右侧；点击伴读标题进入 `/community/:classroomId`。伴读管理页仍可在管理卡片标题行显示班级学生数。

页面操作：

- 老师登录后，页面顶部蓝色头部右侧显示“老师管理”文字链接和右箭头，点击进入 `/community/manage`；
- 学生登录后不显示“老师管理”链接；
- 管理员是否显示该入口由原系统管理员导航决定，本期移动端社区页不改变管理员后台入口；
- 学生只能查看有权限的班级详情和班级视频；
- 无班级时显示空状态和返回首页/加入班级的入口，不能显示异常堆栈；
- 社区班级列表调用独立的社区公开分页接口，按所有未归档班级加载，不能复用管理页的“当前老师负责班级”过滤条件；固定每页 2 条，底部提供上一页/下一页分页。

社区页顶部设计：

1. 蓝色背景中央显示“伴读社区”。
2. 内容区使用书本图标和“伴读社区陪伴你每一天”文案，不显示旧的 `StudyCompanion` 和“找到一起学习的伙伴”。
2. 老师角色在蓝色背景右侧显示“伴读管理”或“老师管理”链接，文字和箭头合并为一个可点击区域；本需求统一使用产品文案“老师管理”。
3. 学生角色不渲染该入口，不能仅通过 CSS 隐藏后仍保留可访问按钮。
4. 入口权限以 `/api/auth/me` 返回的 `role` 为准，不信任前端 URL 或本地角色字段。

社区列表请求约定：

```text
GET /api/classrooms/page?page=1&size=10
```

伴读社区浏览统一使用：

```text
GET /api/classrooms/community/page?page=1&size=2
GET /api/classrooms/community/{classroomId}
GET /api/classrooms/{classroomId}/group-feed/community-messages?page=1&size=50&messageType=VIDEO
```

接口只排除 `ARCHIVED` 班级，不按登录人过滤；学生、老师和管理员进入 `/community` 都展示同一份全部伴读社区分页列表，每页 2 条。学生不能改用 `/api/students/me/classrooms`，该接口只用于学生个人班级工作区。管理页继续使用原分页班级接口，并由后端按当前登录老师的 `teacher_id` 过滤，不能接受前端传入其他老师 ID 作为查询条件。

社区详情读取班级伴读视频时使用只读的 `community-messages` 接口，只返回已发布的班级聊天视频，不改变原班级群聊接口对成员访问、发言和视频播放的权限控制。

社区卡片扩展响应字段：

| 字段 | 来源 | 展示 |
|---|---|---|
| `teacherExpertiseTags` | `users.expertise_tags_json` | 负责老师昵称右侧，以中文标签显示 |
| `commentCount` | `classrooms.companion_comment_count` | 伴读标签右侧的评论图标和数量 |
| `likeCount` | `classrooms.companion_like_count` | 伴读标签右侧的点赞图标和数量 |

社区评论和点赞均通过后端接口持久化，新增计数字段默认从 0 开始；前端不得自行修改统计值。社区列表和详情页读取同一班级记录中的评论、点赞计数。

### 18.2 伴读详情

伴读详情页使用 `docs/design/7伴读详情.png` 的手机版布局，数据来源为同一个 `classrooms` 班级记录及其既有关系数据。

页面分区：

1. 头部：返回按钮、班级名称和伴读社区品牌标识。
2. 教师信息：不显示 `N 名学生` 区块，也不显示伴读班级标题及右侧编辑按钮行；页面内容区直接左对齐显示班级负责老师头像、老师注册时的昵称，以及老师注册时保存的初始擅长领域。
3. 伴读视频：不显示单独的“伴读视频”标题，视频播放器紧跟教师信息下一行并直接嵌入详情页；点击播放占位后请求播放地址，在页面内使用 `<video controls playsInline>` 播放，禁止打开新窗口。播放器高度为原占位高度的两倍。播放器内容只显示视频名称，不显示伴读班级标题；主视频使用 `companionVideoId`，没有主视频时从该班聊天已发布的视频中取第一条作为可播放视频。
4. 介绍图片：读取 `classrooms.companion_image_urls_json` 中的图片地址，在播放器下方按原型显示；图片必须保持原始宽高比例并完整展示，不得使用裁切式 `cover` 或固定最大高度；图片不存在时不渲染空白图片框。
5. 班级介绍：读取 `classrooms.description`，在介绍图片下方以无外框文本显示，不再额外显示“班级介绍”标题框。
6. 互动区：显示评论数、点赞数、点赞用户头像和“等 N 人点赞”；点赞头像数量不得超过点赞数，点赞数为 0 时不显示头像，点赞数为 1 时最多显示 1 个头像。点赞请求使用班级与登录人唯一约束幂等写入，服务端事务同步更新 `classrooms.companion_like_count`；详情页刷新后重新读取点赞数和点赞人头像，社区列表读取同一班级记录，因此两页保持联动。
7. 全部评论：按时间倒序逐条显示评论人头像、昵称、评论内容和发送时间；评论消息使用现有班级群聊文本消息模型，不复制建立第二套评论表。若当前登录人是该伴读班级的负责老师，每条评论右侧显示“删除”按钮；删除必须由服务端校验负责人身份，删除消息后事务性减少 `classrooms.companion_comment_count`。
8. 发表评论：底部显示当前登录人头像、评论输入框和发送按钮，发送调用独立的 `community-comments` 接口；只要求登录人为老师或学生且伴读班级未归档，不要求登录人属于该班级，原班级群聊发送权限不变。
9. 进入管理：仅负责人老师和管理员显示编辑入口。

详情页接口约定：

```text
GET  /api/classrooms/community/{classroomId}
GET  /api/classrooms/{classroomId}/group-feed/community-messages?page=1&size=100
POST /api/classrooms/{classroomId}/group-feed/community-comments
DELETE /api/classrooms/{classroomId}/group-feed/messages/{messageId}
POST /api/classrooms/{classroomId}/community-like
GET  /api/classrooms/{classroomId}/group-feed/videos/{videoId}/play
```

`community-messages` 返回视频和文本消息，并补充 `authorAvatarKey`，用于详情页的视频发布人和评论头像。视频点击播放继续复用既有视频播放接口。点赞由 `classroom_companion_likes` 保存班级与登录人的唯一关系，`POST /community-like` 重复调用不会重复增加；详情响应返回 `likeUserAvatarKeys` 和 `likedByCurrentUser`。详情页发表评论使用 `POST /community-comments`，只校验登录人是老师或学生及班级未归档，不检查登录人是否属于该班级；原班级群聊 `POST /messages` 的成员权限保持不变。评论删除只允许该班级负责人老师或管理员调用，社区页面仅向负责人老师显示删除按钮。

### 18.3 班级伴读视频列表

“班级伴读视频列表”不是在 `classrooms` 表中复制一份视频数据，也不是由所有上传视频组成。它对应现有班级群聊中该班负责老师选择分享的视频：

1. 老师从自己有权访问的视频资产中选择视频并发送到该班级群聊。
2. 现有班级群聊消息记录班级、发送人和视频资产的关联。
3. 详情页按群聊中的视频消息读取列表，按发送时间排序并去重展示。
4. 只有该班负责人老师或管理员可以选择/取消分享视频；学生只能播放有权限的视频。
5. 视频播放继续复用现有班级群聊视频播放和完成接口，不新增一套伴读视频存储逻辑。

推荐复用的接口能力：

```text
GET  /api/classrooms/{classroomId}/group-feed/messages
GET  /api/classrooms/{classroomId}/group-feed/videos/{videoId}/play
POST /api/classrooms/{classroomId}/group-feed/videos/{videoId}/complete
```

若现有群聊接口返回的数据不足以渲染详情页，可扩展响应 DTO 增加视频标题、封面、时长和发送时间，但不改变已有字段含义。

## 19. 伴读管理和编辑伴读

### 19.1 伴读管理页

伴读管理页面使用 `docs/design/6伴读管理.png` 的手机版布局，展示负责人老师或管理员有权限管理的原班级记录。伴读管理中的“伴读班级名称”对应 `classrooms.name`，不能创建第二份班级名称。

页面从上到下分为两个主要区域：

1. **创建伴读区域**：位于蓝色页面头部下方，包含伴读班级名称、班级视频、上传班级介绍图片、伴读班级介绍、班级伴读标签和“创建”按钮；
2. **伴读社区分页列表**：位于创建区域下方，先显示“伴读社区”和当前列表总数，二者同一行两端对齐，数量文案使用“共 N 班”；再以横向卡片显示当前登录老师负责的伴读社区。每张卡片左侧显示班级介绍缩略图，中部第一行显示伴读标题，标题行右侧显示 `N 名学生`；标签紧跟在标题下方，编辑/删除操作与标签同一行并靠右对齐，负责老师头像和老师昵称显示在标签行下方。班级描述最多显示两行，超出部分在第二行末尾以省略号截断。

管理页蓝色头部下方直接进入创建伴读区域，不再显示额外的 `StudyCompanion` 或“管理你的伴读班级”二级标题块。

创建区域有“新建”和“编辑回显”两种状态：

- 初次进入页面或点击“创建”时清空表单，班级视频下拉框显示空选项“请选择视频”，不能把其他班级的视频预填到新班级；
- 点击下方伴读列表中的具体数据时，以该条数据的班级序号 `classrooms.id` 作为唯一标识，请求 `GET /api/classrooms/{id}` 回显班级名称、介绍图片、介绍、标签和当前主视频；
- 回显后再请求 `GET /api/classrooms/{id}/group-feed/messages?messageType=VIDEO`，把原系统班级聊天中该班发布的视频转换为下拉选项；
- 视频下拉选项只显示该班级聊天返回的 `VIDEO` 消息，选中项保存为 `companionVideoId`；切换到新建状态时必须清空视频选项和已选视频；
- 列表卡片的编辑按钮和卡片主体点击都按班级序号进入同一回显流程，保存后刷新当前分页，避免使用班级名称作为查询条件导致重名或错配。

创建区域字段与原班级创建接口的对应关系如下：

| 页面字段 | 请求字段 | 原班级字段/处理规则 |
| --- | --- | --- |
| 伴读班级名称 | `name` | `classrooms.name`，必填，复用原班级名称校验 |
| 班级视频 | `companionVideoId` | 可选的班级主视频；视频候选必须来自该班级聊天已发布的 `VIDEO` 消息 |
| 上传班级介绍图片 | `companionImageUrls` | `classrooms.companion_image_urls_json`，保存图片上传接口返回的 URL 数组 |
| 伴读班级介绍 | `description` | `classrooms.description`，必填，最长 500 字 |
| 班级伴读标签 | `companionTags` | `classrooms.companion_tags_json`，多选标签编码数组 |
| 负责老师 | 不展示 | 隐藏域语义，不信任前端传值；老师创建时由服务端写入当前登录人 `classrooms.teacher_id` |

班级伴读标签按原型使用多选标签按钮，默认选项为“英语”“高考备考”“词汇”“阅读”“成长”。点击已选标签取消选择，点击未选标签加入选择；创建和编辑时均按 `companionTags` 数组保存并回显，不使用自由文本输入。

“班级视频”不是独立的伴读视频表。创建班级后，前端按新班级 ID 请求班级聊天的 `VIDEO` 消息作为候选；保存的 `companionVideoId` 只能引用当前老师有权使用且仍可播放的视频。详情页的视频列表仍以班级聊天视频消息为准。

图片上传流程如下：

1. 前端点击“选择图片”，通过浏览器文件选择框选择本地图片；不再要求用户填写本地文件路径。
2. 前端以 `multipart/form-data` 调用 `POST /api/classrooms/companion-images`，字段名为 `file`。
3. 后端仅允许 `ADMIN`、`TEACHER` 上传，允许 `JPG`、`PNG`、`GIF`、`WebP`，单张最大 5MB；文件使用随机文件名保存到服务端 `uploads/companion-classrooms` 目录，并返回可访问的 `/uploads/companion-classrooms/{fileName}` URL。
4. 前端用返回 URL 显示预览，并在创建/编辑班级时将 URL 放入 `companionImageUrls`；班级表只保存 URL 数组，不保存本地路径或文件二进制内容。

上传接口成功响应：

```json
{
  "url": "/uploads/companion-classrooms/7b2f6d1e-8c3a-4f77-9e11-2b8e9a4d1c22.webp",
  "fileName": "7b2f6d1e-8c3a-4f77-9e11-2b8e9a4d1c22.webp"
}
```

上传失败时返回业务错误，前端保留当前表单内容并显示错误提示；用户未选择图片时允许继续创建伴读。

当登录角色为老师时，伴读管理列表只显示当前登录老师创建/负责的伴读社区数据，即查询条件为：

```text
classrooms.teacher_id = 当前登录用户.id
classrooms.status <> ARCHIVED
```

该过滤条件必须在后端执行。前端只传分页参数，不能通过 `teacherId` 查询或越权读取其他老师的伴读社区。

分页约定：

| 参数 | 默认值 | 规则 |
| --- | --- | --- |
| `page` | `1` | 从 1 开始，非法值按 1 处理 |
| `size` | `10` | 默认每页 10 条，服务端最大不超过 100 |
| `keyword` | 空 | 可选，按班级名称或介绍搜索 |
| `sortBy` | `createdAt` | 只允许受控排序字段 |
| `sortDir` | `desc` | 默认按创建时间倒序 |

分页响应使用现有 `Page<ClassroomResponse>` 结构，至少返回 `content`、`totalElements`、`totalPages`、`number`、`size`、`first`、`last`。页面需要显示加载中、空数据、加载失败、上一页/下一页或滚动加载状态。

列表卡片至少显示：介绍图片缩略图、伴读班级名称、伴读标签、负责老师、创建/更新时间、伴读班级介绍摘要，以及编辑和删除操作。列表右上角显示当前查询结果总数，例如“共 2 张”。翻页时只替换列表数据，不改变创建/编辑表单的权限规则；删除成功后重新请求当前页，当前页为空时回到上一页。

管理页面操作：

- 新建伴读班级：调用原班级创建接口，创建成功后刷新第一页并显示新记录；
- 编辑伴读：进入 `/community/:classroomId/edit`；
- 伴读标题本身是详情链接，进入 `/community/:classroomId`，不再显示独立的“查看详情”按钮；
- 删除/归档伴读：调用原班级删除接口，按原班级归档和关联数据保护规则执行；
- 管理班级成员、词书和群聊视频：继续使用原班级管理能力；

创建人映射：

- 伴读管理登录人对应原班级的负责人老师，即 `classrooms.teacher_id`；
- 老师创建班级时服务端忽略或校验客户端传入的 `teacherId`，默认使用当前登录老师；
- 管理员可以按原班级管理权限指定负责人老师；
- 学生不能创建、编辑或删除伴读班级。

老师管理入口与权限：

1. `/community` 页面只对 `TEACHER` 显示“老师管理”入口。
2. 点击入口进入 `/community/manage`，不进入原系统 `/admin/` 页面。
3. 直接访问 `/community/manage` 时，后端仍必须检查登录角色和班级负责人权限。
4. 学生访问 `/community/manage` 返回 `403` 或回到 `/community`，不能看到管理数据。
5. 老师只能管理自己负责的伴读班级；管理员继续遵循原系统管理权限。

### 19.2 编辑伴读页

编辑伴读页面使用 `docs/design/8编辑伴读.png` 的手机版布局，编辑以下资料：

| 页面字段 | 原表字段 | 说明 |
| --- | --- | --- |
| 伴读班级名称 | `classrooms.name` | 复用原班级名称，必填，保持原唯一性规则 |
| 伴读班级介绍 | `classrooms.description` | 复用原班级描述字段 |
| 班级介绍图片 | `classrooms.companion_image_urls_json` | 新增字段，保存图片 URL/资源标识数组 |
| 班级伴读标签 | `classrooms.companion_tags_json` | 新增字段，保存稳定标签编码数组 |
| 班级视频 | `classrooms.companion_video_id` | 新增字段，保存可选的班级主视频资产 ID |
| 评论数 | `classrooms.companion_comment_count` | 新增非负计数字段，默认 0 |
| 点赞数 | `classrooms.companion_like_count` | 新增非负计数字段，默认 0 |

“班级视频”字段是伴读班级的主视频/展示视频；详情页中的“伴读视频列表”仍以班级群聊中负责人选择的视频消息为准，不能把主视频字段误当成列表。

保存时：

1. 前端提交统一的原班级编辑请求；
2. 后端在原 `PUT /api/classrooms/{id}` 中补充伴读字段；
3. 服务端校验当前用户是管理员或该班 `teacher_id` 对应的负责人老师；
4. 先校验图片资源、视频资产和标签编码，再在同一事务内保存班级资料；
5. 未填写的可选字段保存为空值，不用空字符串或非法 JSON 占位。

## 20. 原班级表和接口扩展设计

### 20.1 复用的现有数据表

伴读社区管理与原系统班级管理使用同一个 `classrooms` 表，关系表继续复用：

| 表 | 伴读页面用途 |
| --- | --- |
| `classrooms` | 伴读班级基本资料、负责人和新增伴读资料 |
| `classroom_members` | 班级学生成员 |
| `classroom_group_feed_messages` | 班级聊天消息及负责人选择的视频消息 |
| `classroom_companion_likes` | 登录人对伴读班级的唯一点赞关系，按 `(classroom_id, user_id)` 去重 |
| `video_assets` | 视频资产本体和播放元数据 |
| `classroom_dictionary_assignments` | 班级词书分配，伴读页面不另建分配表 |

### 20.2 `classrooms` 新增字段

建议新增 Flyway migration，例如当前最高版本之后的唯一版本 `V46__add_study_companion_classroom_profile.sql`，实际版本号以仓库迁移目录为准：

| 字段 | 建议类型 | 是否必填 | 用途 |
| --- | --- | --- | --- |
| `companion_video_id` | `BIGINT` | 否 | 伴读班级主视频，关联 `video_assets.id`；为空表示未设置 |
| `companion_image_urls_json` | `JSONB` 或项目统一 JSON 文本 | 否 | 班级介绍图片 URL/资源标识数组 |
| `companion_tags_json` | `JSONB` 或项目统一 JSON 文本 | 否 | 班级伴读标签编码数组 |
| `companion_comment_count` | `BIGINT` | 否 | 社区评论数量，默认 0 |
| `companion_like_count` | `BIGINT` | 否 | 社区点赞数量，默认 0 |

新增点赞关系表 `classroom_companion_likes` 使用唯一约束 `(classroom_id, user_id)`，保存点赞人和点赞时间；点赞接口使用幂等插入，成功后同步 `classrooms.companion_like_count`。删除班级时先显式删除关系记录，不使用级联删除。

迁移示意：

```sql
ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS companion_video_id BIGINT,
    ADD COLUMN IF NOT EXISTS companion_image_urls_json JSONB,
    ADD COLUMN IF NOT EXISTS companion_tags_json JSONB;

-- V47__add_study_companion_social_counts.sql
ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS companion_comment_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS companion_like_count BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_classrooms_companion_video_id
    ON classrooms(companion_video_id);
```

如果项目统一使用 `TEXT` 保存 JSON，则两个数组字段统一使用 `TEXT`，由后端 DTO 序列化和反序列化，禁止前端直接拼接未校验的 JSON。`companion_video_id` 只能引用已存在且可播放的 `video_assets`；是否增加数据库外键需结合现有视频资产删除策略评审，不能引入级联删除。

### 20.3 原班级创建、编辑接口扩展

继续复用原接口，不新增伴读专用的班级创建接口：

```http
POST /api/classrooms
PUT  /api/classrooms/{id}
GET  /api/classrooms
GET  /api/classrooms/{id}
```

请求体在原字段基础上增加：

```json
{
  "name": "七年级英语伴读班",
  "description": "每周一起完成单词和阅读任务",
  "teacherId": 12,
  "companionVideoId": 88,
  "companionImageUrls": ["/uploads/classrooms/12/cover-1.webp"],
  "companionTags": ["ENGLISH", "READING", "DAILY_CHECKIN"]
}
```

响应 `ClassroomResponse` 追加相同语义的驼峰字段：

```json
{
  "companionVideoId": 88,
  "companionImageUrls": ["/uploads/classrooms/12/cover-1.webp"],
  "companionTags": ["ENGLISH", "READING", "DAILY_CHECKIN"]
}
```

后端代码改动范围：

1. `Classroom` 增加三个新字段；
2. `CreateClassroomRequest`、`UpdateClassroomRequest` 增加三个新字段；
3. `ClassroomResponse` 返回三个新字段；
4. `ClassroomService.createClassroom` 和 `updateClassroom` 完成字段校验和保存；
5. `ClassroomController` 保持现有 URL、HTTP 方法和权限注解；
6. 老接口请求不带新字段时，三个字段保持为空，原班级管理行为不变。

### 20.4 字段校验和权限

| 校验项 | 规则 |
| --- | --- |
| 班级名称 | 复用原班级名称非空、长度和唯一性规则 |
| 班级介绍图片 | 只接受服务端已登记的图片资源标识或允许域名 URL，限制数量、单张大小和格式 |
| 班级伴读标签 | 只接受后端白名单编码，去重并限制数量 |
| 班级主视频 | 必须是存在、可播放且当前负责人有权使用的视频资产 |
| 负责人 | 管理员可指定；老师创建时强制为当前用户；学生禁止操作 |
| 编辑权限 | 管理员或该班负责人老师；其他老师和学生返回 `403` |

新增字段均为可选，以兼容历史班级。历史班级不回填虚构图片、标签或视频；前端显示“暂无介绍资料”等空状态。

## 21. 前端、后端和数据流

### 21.1 典型用户流程

```text
首页
 ├─ 学生注册 -> /register/student -> POST /api/users -> /login
 ├─ 老师注册 -> /register/teacher -> POST /api/users -> /login
 ├─ 登录 -> /login -> POST /api/auth/login -> /community
 └─ 二维码 -> /qr

伴读社区 -> /community
 ├─ 班级卡片 -> /community/:classroomId
 └─ 老师/管理员管理 -> /community/manage
                         └─ 编辑 -> /community/:classroomId/edit
```

### 21.2 伴读详情数据流

1. 前端获取当前用户身份并调用可见班级接口。
2. 用户打开班级详情后，读取 `classrooms` 的班级基本资料和伴读扩展字段。
3. 同时读取班级群聊消息中的视频消息，形成详情页伴读视频列表。
4. 用户点击视频时调用现有视频播放接口，由后端再次检查班级成员、负责人或管理员权限。
5. 前端不把班级 ID、视频 ID 或二维码中的分享标识当作权限凭证。

### 21.3 缓存与更新

- 班级详情更新成功后失效该班级列表和详情缓存。
- 编辑图片、标签或主视频成功后，返回服务端规范化后的完整数据。
- 群聊视频列表以服务端结果为准，避免前端本地追加造成重复。
- 未登录或权限失效时跳转 `/login`，登录成功后回到原请求页面或 `/community`。

## 22. 验收标准补充

### 22.1 页面和跳转

- 首页、登录、注册、伴读社区、伴读管理、伴读详情、编辑伴读和二维码页均可在手机窄屏正常浏览。
- 所有页面的蓝色头部与学生注册页保持同一视觉规范，宽度与主体内容一致。
- 首页四个入口跳转地址正确；原系统旧登录页不发生变化。
- 新登录页调用 `/api/auth/login`，登录成功后进入 `/community`。
- 注册页面仍调用 `/api/users`，学生和老师分别提交隐藏角色值。

### 22.2 伴读班级数据

- 伴读班级名称读取和保存 `classrooms.name`。
- 伴读班级负责人读取和保存 `classrooms.teacher_id`。
- 伴读班级介绍读取和保存 `classrooms.description`。
- 班级介绍图片、班级伴读标签和班级主视频正确读写新增字段。
- 老师编辑时只能修改自己负责的班级，管理员按原权限管理。
- 伴读详情中的视频列表来自该班负责人在班级聊天中选择的视频，而不是另建视频列表表。
- 历史班级不因迁移失败或新页面上线而丢失原名称、描述、负责人、成员和词书关联。

### 22.3 兼容和安全

- 原班级管理页面继续使用原接口并兼容不带新增字段的请求。
- 不新增重复的伴读班级表、伴读视频表或移动端专用登录接口。
- 新增数组字段的非法标签、非法图片和无权限视频均被后端拒绝。
- 删除或归档班级时遵循现有关系数据保护规则，不使用级联删除清理群聊、成员或学习计划历史。
