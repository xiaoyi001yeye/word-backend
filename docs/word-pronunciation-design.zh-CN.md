# 单词发音实现设计文档

## 1. 文档目标

本文档参考 `/Users/wyn/code/WordSnap` 中的单词发音实现，整理出可迁移到 `word-backend` 及统一前端的设计方案，重点回答：

1. 单词发音数据从哪里来
2. 英音 / 美音如何区分
3. 音标、音频 URL 和播放动作如何分层
4. 网络失败、缺失音频、平台差异时如何降级
5. 当前 `word-backend` 已有模型如何承接这套能力

本文档只总结和设计，不直接修改业务代码。

## 2. WordSnap 现有实现摘要

WordSnap 的发音能力由三层组成：

1. Flutter 业务层：`lib/features/study/native_pronunciation_service.dart`
2. Flutter 学习页 UI 层：`lib/features/study/study_flow_pages.dart`
3. Android / iOS 原生播放层：
   - `android/app/src/main/kotlin/com/example/wordsnap/MainActivity.kt`
   - `ios/Runner/AppDelegate.swift`

核心结论：

1. Flutter 侧统一负责查词、缓存、选择英美音、构造降级 URL。
2. 原生侧不理解单词业务，只负责播放音频 URL，另保留 TTS 播放入口。
3. 优先使用第三方词典接口返回的音频 URL。
4. 如果第三方音频 URL 不存在或播放失败，则降级到有道 `dictvoice` 稳定单词发音接口。
5. UI 层显示英音 / 美音两个按钮，音标优先使用接口返回值，缺失时使用题目自带音标。

## 3. WordSnap 数据结构

### 3.1 口音枚举

WordSnap 使用 `WordPronunciationAccent` 表示发音口音：

| 值 | 含义 | URL 参数映射 |
|----|------|-------------|
| `uk` | 英音 | 有道 `type=1` |
| `us` | 美音 | 有道 `type=2` |

### 3.2 发音详情对象

`WordPronunciationDetail` 包含：

| 字段 | 含义 | 来源 |
|------|------|------|
| `word` | 词典接口返回的规范单词 | `data.word` |
| `ukPhonetic` | 英式音标 | `data.ukphone` |
| `usPhonetic` | 美式音标 | `data.usphone` |
| `ukSpeechUrl` | 英音音频 URL | `data.ukspeech` |
| `usSpeechUrl` | 美音音频 URL | `data.usspeech` |

解析时会做两类清洗：

1. 所有字符串统一 `trim`
2. 音频 URL 中的 `\u0026` 替换为真实 `&`

### 3.3 URL 选择规则

`speechUrlFor(accent)` 的逻辑是：

1. 先取当前口音自己的 URL
2. 如果当前口音 URL 为空，则取另一个口音的 URL
3. 如果两个都为空，返回空字符串，让调用方进入有道降级链路

这个策略保证英音 / 美音按钮即使缺一个音频，也尽量能播放到可用发音。

## 4. 查词与缓存流程

WordSnap 的查词接口为：

```text
GET https://v2.xxapi.cn/api/englishwords?word={word}
```

请求策略：

1. 单词先 `trim`，空字符串直接返回 `null`
2. 缓存 key 使用小写单词
3. `_detailCache` 缓存已完成结果，包括 `null` 结果
4. `_pendingDetails` 合并同一个单词的并发请求，避免重复打外部接口
5. HTTP 超时时间为 6 秒
6. 非 2xx、JSON 结构不符合预期、异常均返回 `null`

这意味着“查不到详情”不会阻断播放，因为后续仍可通过有道单词 URL 发音。

## 5. 播放流程

### 5.1 Flutter 侧主流程

`playWord(word, accent)` 的流程：

1. 校验单词不能为空
2. 校验平台必须是 Android 或 iOS
3. 调用 `fetchWordDetail(word)` 获取发音详情
4. 如果接口返回了规范单词，则使用规范单词，否则使用原始输入
5. 根据口音取第三方直连音频 URL
6. 同时构造有道降级 URL
7. 如果第三方直连 URL 非空，先调用原生 `playAudioUrl`
8. 如果第三方 URL 播放失败，吞掉异常并改播有道 URL
9. 如果没有第三方 URL，直接播放有道 URL

有道降级 URL 格式：

```text
https://dict.youdao.com/dictvoice?audio={word}&type={type}
```

其中 `type=1` 表示英音，`type=2` 表示美音。

### 5.2 MethodChannel 协议

Flutter 与原生之间使用固定通道：

```text
wordsnap/pronunciation
```

当前有效方法：

| 方法 | 参数 | 作用 |
|------|------|------|
| `playAudioUrl` | `{ "url": string }` | 播放远程音频 URL |
| `speakWord` | `{ "word": string }` | 使用系统 TTS 朗读单词，当前 Dart 主流程未优先使用 |

设计上推荐继续把播放细节放在原生层，业务层只传 URL 或 word。

## 6. 原生播放实现

### 6.1 Android

Android 侧使用 `MediaPlayer` 播放远程 URL：

1. 收到 `playAudioUrl`
2. 校验 URL 非空
3. 停止系统 TTS
4. 释放上一个 `pronunciationPlayer`
5. 创建 `MediaPlayer`
6. 设置 `AudioAttributes.USAGE_MEDIA` 和 `CONTENT_TYPE_SPEECH`
7. `setDataSource(url)`
8. `prepareAsync()`
9. `onPrepared` 后开始播放并返回成功
10. 播放完成或失败后释放播放器

Android 也保留 `TextToSpeech`：

1. 初始化时优先设置 `Locale.US`
2. 不支持时降级到系统默认 locale
3. 语速设置为 `0.95f`
4. 使用 `QUEUE_FLUSH` 保证新发音打断旧发音

### 6.2 iOS

iOS 侧使用 `AVPlayer` 播放远程 URL：

1. 收到 `playAudioUrl`
2. 校验 URL 非空且可解析
3. 如果 `AVSpeechSynthesizer` 正在讲话，立即停止
4. 设置 `AVAudioSession` 为 `.playback`，并允许 `.mixWithOthers`
5. 创建 `AVPlayer(url:)`
6. 保存到 `pronunciationPlayer`
7. 调用 `play()`
8. 立即返回成功

iOS 也保留 `AVSpeechSynthesizer`：

1. 使用 `AVSpeechUtterance`
2. 语音固定为 `en-US`
3. 语速为 `0.46`
4. 新发音会先停止旧发音

## 7. UI 使用方式

学习页 `ExamPage` 中维护两份页面级状态：

1. `_pronunciationDetails`：单词到发音详情的缓存
2. `_loadingPronunciationDetails`：正在加载的单词集合

页面进入或切题时调用 `_loadPronunciationDetail(word)` 预加载当前题目的发音详情。

题目头部 `_QuestionHeader` 渲染 `_PronunciationPanel`，面板包含两个按钮：

1. 英音按钮
2. 美音按钮

每个按钮显示：

1. 音量图标
2. 口音标签
3. 对应音标

音标展示优先级：

1. 第三方接口返回的对应口音音标
2. 题目数据中的 `fallbackPhonetic`
3. 加载中时显示“加载中”
4. 都没有时显示口音标签

点击按钮后调用 `_speakWord(word, accent)`，错误统一用 `SnackBar` 提示。

## 8. 当前 word-backend 可承接能力

`word-backend` 当前已经具备部分数据基础：

1. `MetaWord.phonetic`：旧版单一音标文本
2. `MetaWord.phoneticDetail`：JSONB，结构为 `{ "uk": "...", "us": "..." }`
3. `Phonetic` 模型：包含 `uk`、`us`
4. `PhoneticDto`：导入 / 请求层同样包含 `uk`、`us`
5. `MetaWordEntryDtoV2.phonetic`：支持导入英美音标
6. 导入逻辑会把 V2 词条里的 phonetic 写入 `phonetic_detail`

缺口是：

1. 当前没有保存单词级英 / 美音音频 URL
2. 当前没有后端统一查外部词典的发音详情接口
3. 当前详情响应 `MetaWordDetailResponse` 仍只返回旧版 `phonetic`，没有返回 `phoneticDetail`
4. 播放动作应留在前端 / 原生层，后端只应提供发音元数据

## 9. 推荐迁移设计

### 9.1 设计结论

推荐采用“后端缓存发音元数据，前端负责播放”的方案：

1. 后端新增发音详情接口，统一查询和缓存第三方词典结果
2. 后端 `MetaWord` 增补英 / 美音音频 URL 字段或 JSONB 字段
3. 前端仍按 WordSnap 逻辑构造有道降级 URL
4. 原生 App 或 Web 前端各自负责实际播放

这样可以减少客户端直接依赖第三方查词接口，也便于后端复用已有 `phonetic_detail` 数据。

### 9.2 后端数据模型建议

优先推荐新增一个 JSONB 字段，而不是增加多个分散列：

```json
{
  "uk": {
    "phonetic": "/.../",
    "audioUrl": "https://..."
  },
  "us": {
    "phonetic": "/.../",
    "audioUrl": "https://..."
  },
  "source": "xxapi",
  "fetchedAt": "2026-06-22T00:00:00"
}
```

如果希望保持当前模型简单，也可以继续使用：

1. `phonetic_detail` 保存 `{uk, us}` 音标
2. 新增 `pronunciation_audio_detail` 保存 `{uk, us, source, fetchedAt}` 音频信息

推荐第二种，因为它对现有 `Phonetic` 模型侵入更小。

### 9.3 后端接口建议

新增接口：

```text
GET /api/meta-words/word/{word}/pronunciation
```

响应：

```json
{
  "word": "example",
  "phonetic": {
    "uk": "/ig'za:mpəl/",
    "us": "/ɪɡˈzæmpəl/"
  },
  "audio": {
    "uk": "https://...",
    "us": "https://..."
  },
  "fallbackAudio": {
    "uk": "https://dict.youdao.com/dictvoice?audio=example&type=1",
    "us": "https://dict.youdao.com/dictvoice?audio=example&type=2"
  },
  "source": "cache"
}
```

接口规则：

1. 先按 `normalized_word` 查本地 `MetaWord`
2. 如果本地有完整音标 / 音频，直接返回
3. 如果缺音频或音标，则调用第三方词典接口补齐
4. 第三方成功时回写本地缓存
5. 第三方失败时仍返回有道 fallback URL
6. 空单词返回 `400 Bad Request`
7. 外部接口失败不应导致前端不可播放

### 9.4 前端播放建议

前端保留 WordSnap 的播放决策：

1. 用户点击英音 / 美音按钮
2. 优先播放响应中的 `audio.{accent}`
3. 如果缺失或播放失败，播放 `fallbackAudio.{accent}`
4. 如果仍失败，提示“当前无法播放单词发音，请稍后重试”

Web 前端可以使用 `HTMLAudioElement`：

```text
new Audio(url).play()
```

移动端或 Flutter 客户端继续通过原生播放，避免平台音频策略差异影响体验。

## 10. 降级与异常策略

推荐降级链路：

```text
本地缓存音频 URL
  -> 第三方词典音频 URL
  -> 有道 dictvoice 单词 URL
  -> 系统 TTS
  -> 用户可见错误提示
```

注意：

1. WordSnap 当前 Dart 主流程没有主动降级到 `speakWord` TTS，只是原生层保留了该能力
2. 如果在 `word-backend` 的统一前端里实现，可以把 TTS 作为最后一级补充能力
3. 第三方查词失败应被视为可恢复失败，不应阻断有道 URL 播放
4. 播放失败应释放播放器，避免多个音频实例互相占用资源
5. 新播放请求应停止旧播放请求，避免重叠发音

## 11. 测试建议

### 11.1 后端单元测试

1. 空单词返回 `400`
2. 命中本地缓存时不调用外部词典
3. 外部词典成功时正确解析 `ukphone/usphone/ukspeech/usspeech`
4. 外部词典失败时仍返回有道 fallback URL
5. 英音 URL 缺失时允许返回美音 URL 作为可播放候选

### 11.2 前端测试

1. 英音按钮显示英式音标
2. 美音按钮显示美式音标
3. 发音详情加载中显示“加载中”
4. 直连 URL 播放失败后会尝试 fallback URL
5. 空单词或不支持平台显示错误提示

### 11.3 移动端原生测试

1. Android `MediaPlayer` 能播放远程 URL
2. Android 新发音会释放旧播放器
3. iOS `AVPlayer` 能播放远程 URL
4. iOS 播放音频前会停止 `AVSpeechSynthesizer`
5. 播放失败不会导致应用崩溃

## 12. 实施优先级建议

推荐拆成三步：

1. 后端先补 `/api/meta-words/word/{word}/pronunciation`，返回音标、音频和 fallback URL
2. Web 前端接入发音按钮，用 `Audio` 播放 URL
3. 如果后续还有移动端，复用 WordSnap 的 MethodChannel + 原生播放器方案

首期不要把“生成音频文件并存储到服务器”作为目标。直接使用词典音频 URL 和有道 fallback 成本更低，也更贴近 WordSnap 已验证的实现。

## 13. 参考源码

1. `/Users/wyn/code/WordSnap/lib/features/study/native_pronunciation_service.dart`
2. `/Users/wyn/code/WordSnap/lib/features/study/study_flow_pages.dart`
3. `/Users/wyn/code/WordSnap/android/app/src/main/kotlin/com/example/wordsnap/MainActivity.kt`
4. `/Users/wyn/code/WordSnap/ios/Runner/AppDelegate.swift`
5. `/Users/wyn/code/word-backend/src/main/java/com/example/words/model/MetaWord.java`
6. `/Users/wyn/code/word-backend/src/main/java/com/example/words/model/Phonetic.java`
7. `/Users/wyn/code/word-backend/src/main/java/com/example/words/dto/PhoneticDto.java`
