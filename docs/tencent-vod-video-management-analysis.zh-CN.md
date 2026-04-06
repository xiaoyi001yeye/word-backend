# 腾讯云点播视频资源管理业务分析文档

## 1. 文档目标

本文档用于结合腾讯云点播官方 API 文档与当前 `word-backend` 项目中的视频资源管理实现，分析：

1. 当前业务已经覆盖了哪些腾讯云点播能力
2. 现阶段视频资源管理真正需要接入哪些 VOD 接口
3. 哪些接口属于首期必需，哪些适合后续增强
4. 后续设计和实现时需要重点关注哪些业务约束

本文档只做业务与接口分析，不直接修改业务代码。

## 2. 参考资料

本文档主要参考以下官方资料：

- [腾讯云点播简介](https://cloud.tencent.com/document/api/266/31752)
- [腾讯云点播 API 概览](https://cloud.tencent.com/document/product/266/31753)
- [腾讯云点播应用体系](https://cloud.tencent.com/document/product/266/14574)
- [查询应用列表 DescribeSubAppIds](https://cloud.tencent.com/document/api/266/36304)
- [使用任务流模板进行视频处理 ProcessMediaByProcedure](https://cloud.tencent.com/document/api/266/34782)
- [查询任务详情 DescribeTaskDetail](https://cloud.tencent.com/document/api/266/33431)
- [修改事件通知配置 ModifyEventConfig](https://cloud.tencent.com/document/api/266/55244)
- [获取媒体详细信息 DescribeMediaInfos](https://cloud.tencent.com/document/product/266/31763)
- [删除媒体 DeleteMedia](https://cloud.tencent.com/document/product/266/31764)
- [修改媒体文件属性 ModifyMediaInfo](https://cloud.tencent.com/document/api/266/31762)
- [视频播放综述](https://cloud.tencent.com/document/product/266/45539)

## 3. 当前项目中的视频业务现状

结合当前代码实现，项目已经具备一套基础的视频资源管理链路。

### 3.1 已有后台能力

当前后台管理端已经支持：

- 管理员维护视频存储配置
- 为存储配置保存 `SecretId`、`SecretKey`、`Region`、`SubAppId`、`ProcedureName`
- 设置默认启用的视频存储配置
- 管理员和老师上传视频文件
- 本地保存视频记录及腾讯云返回的媒资标识
- 后台手动同步腾讯云媒资状态
- 后台直接预览已可播放的视频资源
- 删除本地视频时同时删除腾讯云媒资

### 3.2 本地核心数据模型

当前本地表意已经比较清晰：

- `video_storage_configs`
  - 保存腾讯云点播连接配置
  - 包含 `region`、`subAppId`、`procedureName`
- `video_assets`
  - 保存本地视频资源索引
  - 包含 `tencentFileId`、`mediaUrl`、`coverUrl`、`durationSeconds`、`status`

当前视频资源状态模型为：

- `PROCESSING`
- `READY`
- `FAILED`

当前资源权限模型为：

- `SYSTEM`
- `TEACHER`

这说明本系统的视频业务定位不是开放式视频平台，而是一个“后台上传与管理型”的教学视频资源系统。

## 4. 从腾讯云点播简介文档得到的核心结论

根据 [腾讯云点播简介](https://cloud.tencent.com/document/api/266/31752)：

- 腾讯云点播提供的是一站式能力，覆盖音视频存储、转码处理、加速播放、加密与 AI 能力
- 当前推荐使用的是 API 3.0
- 关键业务标识包括：
  - `secretid`
  - `secretkey`
  - `fileid`
  - `taskid`

结合当前项目，最重要的结论有三点：

1. 后续所有新能力都应基于 VOD API 3.0 设计
2. 本地视频记录必须围绕腾讯云的 `FileId` 建模
3. 如果引入任务流、转码、审核等异步能力，本地还应围绕 `TaskId` 建模

## 5. 当前业务与腾讯云能力的映射关系

### 5.1 业务主链路

当前业务主链路可以概括为：

1. 管理员配置腾讯云点播账号与应用信息
2. 管理员选择默认配置
3. 老师或管理员上传视频
4. 腾讯云返回 `FileId`
5. 系统拉取媒资详情并本地落库
6. 后台展示视频库
7. 预览已就绪视频
8. 删除时同步删除腾讯云媒资

### 5.2 这条链路对应的点播能力

这条链路实际覆盖了腾讯云点播中的四类能力：

- 应用与账号访问
- 服务端上传
- 媒资查询
- 媒资删除

如果后续要把视频管理做得更完整，还会自然扩展到：

- 任务流处理
- 异步任务跟踪
- 事件通知消费
- 媒资属性维护
- 播放签名与播放器集成

## 6. 现阶段真正需要的腾讯云接口

下面按“当前业务是否实际需要”来整理接口。

### 6.1 首期必需接口

#### 6.1.1 DescribeSubAppIds

用途：

- 校验当前配置的 `SecretId`、`SecretKey` 是否可用
- 校验 `SubAppId` 是否属于当前账号
- 为“测试配置”能力提供更准确的健康检查

为什么必需：

- 当前系统已经支持 `SubAppId`
- 腾讯云应用体系要求 API 访问明确指向应用
- 这是最适合作为配置联通性校验的接口

结论：

- 该接口是“配置测试”场景的首选接口

#### 6.1.2 服务端上传 SDK

腾讯云在 [应用体系](https://cloud.tencent.com/document/product/266/14574) 中明确提到：

- 服务端 API 上传涉及 `ApplyUpload` 和 `CommitUpload`
- 更推荐直接使用 SDK

当前项目已经在 Java 侧使用上传 SDK，这个方向是正确的。

在业务分析层面，应将“上传能力”理解为：

- 首选：腾讯云服务端上传 SDK
- 对应底层接口：`ApplyUpload` + `CommitUpload`

结论：

- 当前上传方案不需要改方向
- 但要把“上传到哪个应用”作为一等约束处理

#### 6.1.3 DescribeMediaInfos

用途：

- 根据 `FileId` 获取媒体详情
- 回填可播放地址、封面地址、时长、媒资状态
- 支撑“同步状态”操作

为什么必需：

- 当前本地表已经保存 `tencentFileId`
- 上传后立即回填与后续手动同步都依赖该接口

结论：

- 这是当前视频库最核心的媒资查询接口

#### 6.1.4 DeleteMedia

用途：

- 删除腾讯云上的媒体文件
- 删除时同时清理相关衍生文件

为什么必需：

- 当前后台已经支持“删除视频”
- 当前业务语义是“本地删除时云端一并删除”

结论：

- 这是删除链路的必需接口

### 6.2 强烈建议接入的接口

#### 6.2.1 ProcessMediaByProcedure

用途：

- 对已上传的视频应用任务流模板
- 用于转码、截图、雪碧图、审核等处理链路

为什么强烈建议：

- 当前配置模型里已经存在 `procedureName`
- 说明产品设计上已经预留了任务流能力
- 如果要支持更稳定的播放、封面生成、审核与课件视频处理，这个接口非常重要

适用场景：

- 上传完成后触发任务流
- 对历史视频补跑任务流
- 对不同业务配置不同任务流模板

#### 6.2.2 DescribeTaskDetail

用途：

- 查询任务流执行结果
- 获取具体任务阶段信息
- 区分“上传完成但转码未完成”和“处理失败”

为什么强烈建议：

- 当前本地只有 `PROCESSING / READY / FAILED`
- 仅靠 `DescribeMediaInfos` 难以准确表达异步任务执行细节
- 如果启用任务流，本地就应该跟踪 `TaskId`

结论：

- 一旦启用任务流，该接口就会从“建议”升级为“必需”

#### 6.2.3 ModifyEventConfig + PullEvents + ConfirmEvents

用途：

- 开启腾讯云点播事件通知
- 拉取上传完成、任务流完成等事件
- 消费成功后确认事件

为什么强烈建议：

- 当前视频状态同步依赖手动触发
- 手动同步适合首期验证，不适合长期稳定运营
- 一旦视频数量增长，系统应当由事件驱动回写本地状态

建议使用方式：

- 优先考虑可靠通知模式
- 后台定时任务或专门消费服务调用 `PullEvents`
- 成功处理后调用 `ConfirmEvents`

结论：

- 这是从“手动运营”升级到“自动同步”的关键能力

### 6.3 中后期增强接口

#### 6.3.1 ModifyMediaInfo

用途：

- 修改腾讯云侧媒体名称、描述、标签、封面、字幕等属性

适用场景：

- 后台编辑视频标题与描述时，同步回写腾讯云
- 后续引入标签、分类、封面管理时使用

为什么不是首期必需：

- 当前本地视频标题和描述主要在本地管理
- 业务上还没有强依赖“云端属性必须与本地一致”

#### 6.3.2 SearchMedia

用途：

- 搜索云端媒资
- 做云端与本地对账
- 排查孤儿媒资、漏同步数据

适用场景：

- 管理后台巡检工具
- 数据补偿任务
- 故障排查

为什么不是首期必需：

- 当前主链路依赖 `FileId` 已足够
- 只有在运维和治理能力增强时才会显著受益

#### 6.3.3 DescribeProcedureTemplates

用途：

- 拉取腾讯云任务流模板列表

适用场景：

- 后台配置页不再手填 `ProcedureName`
- 改为下拉选择腾讯云现有任务流模板

为什么不是首期必需：

- 当前系统只保存任务流名称字符串
- 业务可以先通过手工录入完成验证

## 7. 结合当前业务后的接口清单

| 优先级 | 接口 | 当前业务用途 |
|--------|------|-------------|
| 高 | `DescribeSubAppIds` | 测试视频存储配置，校验账号与应用 |
| 高 | 上传 SDK / `ApplyUpload` + `CommitUpload` | 服务端上传视频 |
| 高 | `DescribeMediaInfos` | 回填与同步媒资详情 |
| 高 | `DeleteMedia` | 删除腾讯云媒资 |
| 中 | `ProcessMediaByProcedure` | 上传后自动转码、截图、审核 |
| 中 | `DescribeTaskDetail` | 查询任务流执行状态 |
| 中 | `ModifyEventConfig` | 开启事件通知 |
| 中 | `PullEvents` | 拉取事件通知 |
| 中 | `ConfirmEvents` | 确认已消费事件 |
| 低 | `ModifyMediaInfo` | 同步修改云端媒资属性 |
| 低 | `SearchMedia` | 媒资检索、对账、巡检 |
| 低 | `DescribeProcedureTemplates` | 后台任务流模板选择器 |

## 8. 当前业务设计中必须注意的约束

### 8.1 `SubAppId` 不能弱化为可选备注字段

根据腾讯云多篇文档说明，访问点播应用资源时应明确带上 `SubAppId`。

结合当前项目，这意味着：

- 配置测试必须校验 `SubAppId`
- 查询媒资时应尽量带上 `SubAppId`
- 删除媒资时应带上 `SubAppId`
- 上传时应确保资源归属到目标应用

`SubAppId` 在业务上不是附加信息，而是资源归属边界。

### 8.2 `FileId` 是本地视频记录和腾讯云媒资之间的唯一连接点

这意味着：

- 本地删除、同步、预览、回填都必须围绕 `FileId`
- `FileId` 丢失时，本地视频记录几乎不可恢复
- 后续若做数据补偿，应优先基于 `FileId` 而不是标题检索

### 8.3 一旦引入任务流，就应显式管理 `TaskId`

当前本地模型中还没有任务维度信息。

如果后续启用任务流处理，建议在业务设计上增加：

- 任务 ID
- 最近任务类型
- 最近任务状态
- 最近失败原因
- 最近同步时间

否则异步流程会停留在“黑盒状态”。

### 8.4 当前“直接用 `mediaUrl` 预览”适合后台，不一定适合学生正式播放

根据 [视频播放综述](https://cloud.tencent.com/document/product/266/45539)，腾讯云更推荐播放器 SDK、自适应码流、播放签名等完整播放方案。

当前项目的做法适合：

- 管理后台预览
- 内部测试验证
- 小规模教学视频管理

但如果后续视频要进入正式学习端，需评估：

- 播放签名
- 播放器 SDK
- 自适应码流
- 防盗链或加密播放

## 9. 对当前项目的阶段性建议

### 9.1 第一阶段：把现有链路做稳

建议聚焦以下接口：

- `DescribeSubAppIds`
- 上传 SDK
- `DescribeMediaInfos`
- `DeleteMedia`

目标：

- 配置测试准确
- 上传稳定
- 状态同步可靠
- 删除语义清晰

### 9.2 第二阶段：补齐异步处理能力

建议引入：

- `ProcessMediaByProcedure`
- `DescribeTaskDetail`

目标：

- 上传后自动处理
- 支持转码、封面、审核等业务
- 支持后台查看处理进度和失败原因

### 9.3 第三阶段：从手动同步升级为事件驱动

建议引入：

- `ModifyEventConfig`
- `PullEvents`
- `ConfirmEvents`

目标：

- 自动更新本地视频状态
- 降低人工干预
- 提升后台一致性

### 9.4 第四阶段：补足治理与运营能力

建议引入：

- `ModifyMediaInfo`
- `SearchMedia`
- `DescribeProcedureTemplates`

目标：

- 云端和本地属性一致
- 支持巡检与对账
- 提升配置页可用性

## 10. 最终结论

结合腾讯云点播官方文档与当前项目代码，现阶段视频资源管理真正需要关注的不是“把所有 VOD 接口都接进来”，而是围绕当前业务主链路，优先打通以下能力：

1. 配置正确校验
2. 服务端上传
3. 媒资详情回填
4. 云端删除
5. 任务流处理
6. 异步状态同步

从业务价值上看，最关键的接口优先级可以归纳为：

- 首期必需：
  - `DescribeSubAppIds`
  - 上传 SDK / `ApplyUpload` + `CommitUpload`
  - `DescribeMediaInfos`
  - `DeleteMedia`
- 第二优先级：
  - `ProcessMediaByProcedure`
  - `DescribeTaskDetail`
  - `ModifyEventConfig`
  - `PullEvents`
  - `ConfirmEvents`
- 后续增强：
  - `ModifyMediaInfo`
  - `SearchMedia`
  - `DescribeProcedureTemplates`

因此，后续设计和实现建议继续坚持“围绕当前教学视频后台管理场景做深做稳”，而不是一开始就追求接入腾讯云点播的全部高级能力。
