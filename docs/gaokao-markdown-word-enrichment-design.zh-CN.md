# 高考 3500 Markdown 词条增强设计（最小范围）

## 1. 结论与边界

`generated/gaokao-3500-words-markdown/` 的每个文件都是一个可追溯的词条素材：

- Front matter 稳定提供单词和 DOCX 来源文件；
- `## 内容` 包含原始的词性、中文释义，少数词条还包含词源故事或记忆材料；
- 内容并不保证每个单词都有拓展信息。例如 `apple.md` 只有基础释义，而 `academy.md` 含较长的词源说明。

因此，不能把 Markdown 原文直接当作“完整词典数据”覆盖现有 `phoneticDetail`、`syllableDetail`、`partOfSpeechDetail`。本设计只新增一个可选 JSONB 对象 `learningDetail`，其中**六个属性**用于保存学生真正会用到的补充信息。Markdown 只作为 AI 的输入依据，不把来源文件和原始短摘录展示在学习页。缺失信息应保留为空，禁止为了填满字段而编造。

不在本次范围：音频、图片、词频、同反义词扩容、整篇原文入库、全文检索、学生学习状态。

## 2. 六个新增属性

| 属性 | 类型 | 用途 | Markdown / AI 取值规则 |
| --- | --- | --- | --- |
| `learningMaterial` | `string` | 完整保留 Markdown 中可学习的拓展材料 | 导入时提取并原样保存方括号中的构词、词源、联想、故事等内容；不得由 AI 摘要或删减 |
| `memoryHint` | `string` | 给学生一条短记忆提示 | AI 可基于 `learningMaterial` 生成不超过 80 字的提示；素材为空时为空字符串 |
| `samePatternWords` | `{word, translation, rootBreakdown}[]` | 展示与当前词使用相同构词模式的单词及字根拆解 | 完整保留教材材料中列出的同构词；AI 可在其后补充 0–4 个高置信度同构词。无可靠项时为 `[]` |
| `examPhrases` | `string[]` | 展示少量高考学习常用搭配 | AI 依据单词和已确认词性生成 0–3 条；素材没有依据时允许空数组 |
| `wordFamily` | `{word, pos, translation}[]` | 帮助学生建立派生词和词性转换关系 | AI 生成 0–4 个与当前词同根的高频派生词；不含当前单词本身 |
| `confusableWords` | `{word, distinction}[]` | 解释易混词之间最关键的差异 | AI 生成 0–2 个高频易混词；`distinction` 用简短中文说明区别 |

`learningMaterial` 是教材的完整学习材料，其余五个字段是 AI 学习补充。页面不展示素材文件名，但必须允许学生查看完整 `learningMaterial`，不能只显示 AI 摘要。

## 3. 对应 JSON 格式

建议在 `meta_words` 增加 `learning_detail JSONB`，Java 字段名为 `learningDetail`。它与现有 `partOfSpeechDetail` 并列，而不是塞入某个词性或释义数组中。

```json
{
  "word": "academy",
  "phonetic": { "uk": "/əˈkædəmi/", "us": "/əˈkædəmi/" },
  "partOfSpeech": [],
  "difficulty": 3,
  "learningDetail": {
    "learningMaterial": "在希腊神话传说中……英语单词 academy 就来源于柏拉图创建的 Akademeia 学园。",
    "memoryHint": "联想柏拉图的学园：academy 最初指学术研究的场所。",
    "samePatternWords": [],
    "examPhrases": ["the academy of sciences", "a military academy"],
    "wordFamily": [
      { "word": "academic", "pos": "adj.", "translation": "学术的" }
    ],
    "confusableWords": [
      { "word": "college", "distinction": "college 通常指大学或学院；academy 更强调专门的教学或研究机构。" }
    ]
  }
}
```

约束：

1. `samePatternWords` 的教材项不设数量上限：素材有 10 个则保留 10 个，有 7 个保留 7 个，有 5 个保留 5 个；AI 可在教材项之后额外补充 0–4 个高置信度同构词。`examPhrases` 最多 3 项；`wordFamily` 最多 4 项；`confusableWords` 最多 2 项。
2. `learningMaterial` 最大 6,000 个字符，保存完整教材学习材料；`memoryHint` 最大 80 个中文字符；`samePatternWords[].rootBreakdown` 最大 160 个字符；其余数组字符串或对象字段最大 80 个字符。
3. 空信息使用 `""`，数组使用 `[]`；`wordFamily.word` 不能等于当前单词。
4. Markdown 导入程序写入 `learningMaterial`，并应优先解析其中已列出的 `samePatternWords`；AI 自动补全更新其余字段并可补齐 `samePatternWords`，绝不得覆盖、摘要或删减 `learningMaterial`。

## 4. 页面设计：单词详细数据弹窗

现有“查看详细数据”弹窗保留基础信息、发音音节、词性释义等区块。在“词性与释义”之后增加一个折叠区块：**学习拓展**。

| 页面元素 | 绑定字段 | 呈现规则 |
| --- | --- | --- |
| 完整学习材料 | `learningMaterial` | 默认显示前 4 行并提供“展开全部”按钮；完整内容只读展示，保留原有换行 |
| 记忆提示 | `memoryHint` | 只读多行输入框，并标识“AI 学习补充” |
| 同构词 | `samePatternWords` | 每项显示“单词 · 中文释义 · 字根拆解”；完整显示所有项，不截断为固定数量；为空时显示“暂无同构词” |
| 常用搭配 | `examPhrases` | 每项一个 Badge；为空时显示“暂无推荐搭配” |
| 派生词族 | `wordFamily` | 每项显示“单词 · 词性 · 中文释义”；为空时显示“暂无常用派生词” |
| 易混词辨析 | `confusableWords` | 每项显示易混词和一行差异说明；为空时显示“暂无易混词提示” |

弹窗中的“AI 自动补全”按钮生成完成后，应重新读取详情，连同 `learningDetail` 一起刷新。

## 5. 提示词设计

调用 AI 时，用户消息应附带单词、已解析的教材短摘录，以及允许为空的 Schema；不要直接拼接整份 Markdown 原文。

```text
你正在为英语学习系统补充单词学习资料。只返回一个 JSON 对象，不要输出 Markdown 或说明。

单词："academy"
已确认词性和释义："n. 专科院校；研究院，学院"
教材完整学习材料（可能为空）："..."

请仅生成：
{
  "memoryHint": "不超过 80 个中文字符；没有可靠提示则为空字符串",
  "samePatternWords": [{ "word": "同构词", "translation": "中文释义", "rootBreakdown": "词根 / 前缀 / 后缀拆解" }],
  "examPhrases": ["0 到 3 条常用搭配，每条为英文短语"],
  "wordFamily": [{ "word": "派生词", "pos": "词性缩写", "translation": "中文释义" }],
  "confusableWords": [{ "word": "易混词", "distinction": "不超过 80 个中文字符的关键区别" }]
}

规则：
1. 不得修改单词拼写、音标、词性或释义。
2. `learningMaterial` 已由后端保存，仅作为生成依据，禁止在本次 AI 输出中返回、修改或全文复述它。
3. 对 `samePatternWords`，必须完整返回教材材料中明确列出的每一个同构词，并提供 `rootBreakdown` 字根拆解；在此基础上可按顺序追加 0–4 个高置信度同构词。没有可靠项时返回 `[]`。
4. 不确定时返回空字符串或空数组，绝不臆造故事、同构词、派生词或易混词。
5. 搭配必须与给出的单词及词性一致，不输出完整例句；同构词、派生词和易混词必须是高频学习项。
```

系统提示词仍应保留“只返回合法 JSON”的约束。后端先从 Markdown 解析已确认词性、释义、完整学习材料和材料中已列出的同构词并先行写入；AI 生成其余五个字段，并补齐同构词的中文释义、字根拆解，或在教材项之后追加最多 4 个高置信度项目；输出经过 JSON Schema 校验后合并。

## 6. 最小实现切片与验收

1. **数据切片**：增加 `learning_detail` JSONB、DTO 和 API 返回字段；Markdown 导入时完整写入 `learningMaterial`。
2. **AI 切片**：扩展现有 V2 提示词与响应 DTO，生成并校验除 `learningMaterial` 外的五个字段后合并保存。
3. **页面切片**：详情弹窗增加“教材来源与学习拓展”区块，AI 按钮完成后刷新。

验收标准：

- `apple.md` 这类没有方括号拓展材料的词条，`learningMaterial` 和 `samePatternWords` 可以为空；其余四个字段也允许为空，或只填入可靠的高频搭配/词族。
- `academy.md` 这类包含长词源材料的词条，`learningMaterial` 必须完整保存并可展开查看；AI 记忆提示只作为辅助。
- 任意一次 AI 自动补全都不得改变 `learningMaterial`，且必须遵守其余字段的长度与数组数量限制。
- 详情页能正确显示六个字段的空态、数组态和已填充态，且同构词不因数量超过 3、5 或 10 而截断。

## 7. 实际素材演练结果：`ability`

本节使用实际文件 [`generated/gaokao-3500-words-markdown/ability.md`](../generated/gaokao-3500-words-markdown/ability.md) 验证设计的输入边界。该素材实际解析到：

```text
单词：ability
词性与释义：n. 能力，才能
可用学习材料：由 able + -ity 构成抽象名词；列出了 knowability、changeability、readability、usability、dependability、movability、adaptability 等同类构词。
```

Markdown 导入先完整写入教材材料和全部 7 个已列出的同构词；随后 AI 生成其余字段，并在教材项之后额外补充 4 个高置信度同构词。通过本设计的长度、数量和 JSON 结构校验后，`learningDetail` 的实际目标结果如下：

```json
{
  "learningMaterial": "由-able+-ity 而构成抽象名词，表示“可...性”、“易...性”、“可...”\nknowability 可知性\nchangeability 可变性\nreadability 可读性\nusability 可用性，能用\ndependability 可靠性\nmovability 可移动性\nadaptability 可适应性",
  "memoryHint": "ability = able + -ity：把“能够”变成“能力、才能”这个抽象名词。",
  "samePatternWords": [
    { "word": "knowability", "translation": "可知性", "rootBreakdown": "know（知道）+ able（能够）+ -ity（名词后缀）" },
    { "word": "changeability", "translation": "可变性", "rootBreakdown": "change（改变）+ able（能够）+ -ity（名词后缀）" },
    { "word": "readability", "translation": "可读性", "rootBreakdown": "read（阅读）+ able（能够）+ -ity（名词后缀）" },
    { "word": "usability", "translation": "可用性，能用", "rootBreakdown": "use（使用）+ able（能够）+ -ity（名词后缀）" },
    { "word": "dependability", "translation": "可靠性", "rootBreakdown": "depend（依靠）+ able（能够）+ -ity（名词后缀）" },
    { "word": "movability", "translation": "可移动性", "rootBreakdown": "move（移动）+ able（能够）+ -ity（名词后缀）" },
    { "word": "adaptability", "translation": "可适应性", "rootBreakdown": "adapt（适应）+ able（能够）+ -ity（名词后缀）" },
    { "word": "acceptability", "translation": "可接受性", "rootBreakdown": "accept（接受）+ able（能够）+ -ity（名词后缀）" },
    { "word": "accessibility", "translation": "可获得性；易接近性", "rootBreakdown": "access（接近；使用）+ ible（能够）+ -ity（名词后缀）" },
    { "word": "accountability", "translation": "责任；可问责性", "rootBreakdown": "account（解释；说明）+ able（能够）+ -ity（名词后缀）" },
    { "word": "affordability", "translation": "支付能力；负担得起", "rootBreakdown": "afford（负担得起）+ able（能够）+ -ity（名词后缀）" }
  ],
  "examPhrases": [
    "have the ability to do sth",
    "the ability to do sth",
    "natural ability"
  ],
  "wordFamily": [
    { "word": "able", "pos": "adj.", "translation": "能够的；有能力的" },
    { "word": "unable", "pos": "adj.", "translation": "不能的；无能力的" },
    { "word": "disability", "pos": "n.", "translation": "残疾；无能力" }
  ],
  "confusableWords": [
    {
      "word": "capacity",
      "distinction": "ability 强调个人具备的能力；capacity 更强调容量、容纳量或潜在能力。"
    }
  ]
}
```

页面结果应为：

- “完整学习材料”默认显示前 4 行，可展开后看到教材列出的全部 7 个同类构词；
- “记忆提示”显示一段短文本；
- “同构词”完整显示 11 项（7 个教材项 + 4 个 AI 补充项）；每项同时显示中文释义和 `词根 + able + -ity` 的字根拆解；
- “常用搭配”显示 3 个 Badge；
- “派生词族”显示 3 行“单词 · 词性 · 中文释义”；
- “易混词辨析”显示 `capacity` 与一行区别说明。

这个样例说明了字段分工：教材中的构词材料完整保存在 `learningMaterial`，其中列出的 7 个同构词完整写入 `samePatternWords`；AI 在它们之后追加 4 个高置信度同构词，并补充短记忆提示、高频搭配、词族和易混词。这样既不丢失教材材料，也能受控地扩展学习内容。

## 8. 实际素材演练结果：`abnormal`

本节使用实际文件 [`generated/gaokao-3500-words-markdown/abnormal.md`](../generated/gaokao-3500-words-markdown/abnormal.md)。该文件除目标词的构词说明外，还明确列出 `normal`、`enormous` 和 `enormity` 三个共享 `norm`（正规、常规）构词核心的词，因此这些材料必须完整进入 JSON，而不能只留下“ab- 表示离开”的摘要。

```json
{
  "learningMaterial": "词根词缀： ab- 分离，脱离 + -norm- 正规，常规 + -al 形容词词尾 → 离开常规的\nnormal adj. 正常的【词根词缀： -norm- 正规，常规 + -al 形容词词尾】\nenormous adj. 庞大的；巨大的【e- 向外。-norm 正常，见 normal。即超出正常的，巨大的。】\nenormity n. 巨大；暴行【词根词缀： e- 出，向外 + -norm- 正规，常规 + -ity 名词词尾 → 超出常规的】",
  "memoryHint": "ab- 表示“脱离”，norm 表示“常规”：脱离常规就是 abnormal（反常的）。",
  "samePatternWords": [
    { "word": "normal", "translation": "正常的", "rootBreakdown": "-norm-（正规，常规）+ -al（形容词词尾）" },
    { "word": "enormous", "translation": "庞大的；巨大的", "rootBreakdown": "e-（向外）+ -norm-（正常）+ -ous（形容词后缀）" },
    { "word": "enormity", "translation": "巨大；暴行", "rootBreakdown": "e-（向外）+ -norm-（正规，常规）+ -ity（名词后缀）" }
  ],
  "examPhrases": [
    "abnormal behavior",
    "abnormal condition",
    "abnormal temperature"
  ],
  "wordFamily": [
    { "word": "normally", "pos": "adv.", "translation": "通常；正常地" },
    { "word": "abnormally", "pos": "adv.", "translation": "反常地；不正常地" },
    { "word": "abnormality", "pos": "n.", "translation": "反常；异常" }
  ],
  "confusableWords": [
    {
      "word": "unusual",
      "distinction": "unusual 指不常见；abnormal 强调偏离正常标准，常用于医学或科学语境。"
    }
  ]
}
```

页面结果应完整显示 3 个 `samePatternWords`，并为每个词显示字根拆解；“完整学习材料”的展开态继续保留教材的完整构词解释。这一例验证：同构词不仅可以是同一后缀模式，也可以是教材明确列出的同一词根构词组；只要材料中有多少项，就必须保存和显示多少项。
