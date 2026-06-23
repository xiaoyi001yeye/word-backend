# 安全删除审计

分支：`codex/deletion-audit`
日期：2026-06-22

范围：这份报告记录了删除审计和已执行的第一批安全清理。本报告基于 `git ls-files`、`git status --ignored`、`find`、`du` 和 `rg` 检查了已跟踪文件、被忽略的本地产物、Docker/Maven/前端构建配置，以及源码引用关系。

## 已执行删除

已删除审计中“高置信度可删除文件”列出的文件，并清理了旧 `admin-frontend/` 目录下的本地 ignored 残留。已删除内容包括：

- `admin-frontend/.gitignore`
- `admin-frontend/dist/`
- `admin-frontend/node_modules/`
- `frontend/src/assets/react.svg`
- `frontend/admin/src/assets/vite.svg`
- `frontend/admin/src/assets/solid.svg`
- `frontend/admin/src/assets/hero.png`
- `sample-data/sample_words.json`
- `meta-word-restructuring/deepseek-元单词格式调整深度分析.md`
- `meta-word-restructuring/元单词格式调整分析.md`
- `.opencode/skill-sync-graph.json`

删除后，`admin-frontend/`、`frontend/src/assets/`、`frontend/admin/src/assets/`、`sample-data/` 和 `meta-word-restructuring/` 均已成为空目录并被移除。

## 高置信度可删除文件

从应用运行和构建角度看，这些文件可以安全删除，因为源码、Docker Compose、Dockerfile、Maven、npm 脚本和 Vite 入口都没有引用它们。本节内容已在本分支执行。

| 候选项 | 为什么安全 | 备注 |
| --- | --- | --- |
| `admin-frontend/.gitignore` | 这是 `admin-frontend/` 下唯一还被 Git 跟踪的文件。当前 compose 文件里只有一个 `frontend` 服务，且 `AGENTS.md` 已说明管理端代码现在位于 `frontend/admin/`。 | 删除这个已跟踪文件后，本地被忽略的 `admin-frontend/dist/` 和 `admin-frontend/node_modules/` 也可以一并本地删除。 |
| `frontend/src/assets/react.svg` | 源码和 HTML 都没有引用 `react.svg`；这是 Vite/React 脚手架残留。 | 文件级删除。 |
| `frontend/admin/src/assets/vite.svg` | 源码和 HTML 都没有引用该资源；管理端 favicon 来自 `frontend/admin/public/favicon.svg`。 | 文件级删除。 |
| `frontend/admin/src/assets/solid.svg` | 源码和 HTML 都没有引用该资源。 | 文件级删除。 |
| `frontend/admin/src/assets/hero.png` | 源码和 CSS 都没有引用 `hero.png`。 | 文件级删除。 |
| `sample-data/sample_words.json` | 没有发现源码、测试、Docker、Maven、npm 或文档引用。 | 只有在仓库外仍被手工使用时才需要保留。 |
| `meta-word-restructuring/deepseek-元单词格式调整深度分析.md` | 历史分析文档；没有构建或运行时引用。 | 如果当前文档历史不需要保留它，可以安全删除。 |
| `meta-word-restructuring/元单词格式调整分析.md` | 历史分析文档；没有构建或运行时引用。 | 如果当前文档历史不需要保留它，可以安全删除。 |
| `.opencode/skill-sync-graph.json` | 应用构建和运行时都没有引用它。它看起来是 agent/tooling 状态文件。 | 对产品代码来说安全；只有本地 opencode 工作流还需要这个图文件时才保留。 |

## 文档和原型产物

这些内容从运行和构建角度也可以安全删除，但是否删除更偏产品/流程取舍，因为它们可能保留了设计历史。

| 候选项 | 为什么安全 | 备注 |
| --- | --- | --- |
| `CSV_IMPORT_ANALYSIS.md` | 独立的历史 CSV 导入分析；没有运行时或构建引用。 | 如果仍有价值，可以考虑移动到归档目录。 |
| `CSV_IMPORT_TECHNICAL_PLAN.md` | 独立的历史计划；没有运行时或构建引用。 | 同上。 |
| `CSV_IMPORT_USER_GUIDE.md` | 独立指南；没有运行时或构建引用。 | 只有在已被当前用户文档替代时才删除。 |
| `docs/DOCKER_FRONTEND_MODIFICATION_PLAN.md` | 历史 Docker/前端计划；没有运行时或构建引用。 | 已实现的配置现在位于 `docker-compose.yml` 和 `frontend/Dockerfile`。 |
| `docs/superpowers/**` | agent/原型规格、计划、截图和对比产物。没有构建或运行时引用。 | 多个文件仍提到旧的 `admin-frontend` 路径；如果不想在仓库里保留 agent 流程历史，可以安全删除。 |
| `design-qa.md` | 指向原型产物和本地绝对路径；没有构建或运行时引用。 | 如果不再需要保留视觉 QA 证据，可以安全删除。 |

## 可安全删除的文件片段

这些是可以安全移除或替换的文件片段，但需要注意对应的后续动作。

| 文件 | 片段 | 原因 |
| --- | --- | --- |
| `pom.xml` | 删除重复的 `maven-compiler-plugin` 声明之一。 | 该插件被声明了两次，且都配置了相同的 Lombok 注解处理器。建议保留后面带显式版本 `3.11.0` 的声明，删除前面未指定版本的重复声明。 |
| `frontend/index.html` + `frontend/public/vite.svg` | 先替换或移除默认 Vite favicon 引用，再删除 `frontend/public/vite.svg`。 | `frontend/public/vite.svg` 仍被 `<link rel="icon" ... href="/vite.svg" />` 引用，所以只有修改该 HTML 引用后，删除 SVG 才安全。 |
| `docs/superpowers/plans/*.md` 和更早的设计文档 | 如果保留这些文档，应删除其中过期的 `admin-frontend/` 引用。 | 当前管理端代码位于 `frontend/admin/`；旧引用会误导后续维护者。 |
| `AGENTS.md` / `README.md` | 替换 `./mvnw` 命令，或补充 Maven Wrapper 文件。 | 当前仓库没有被跟踪的 `mvnw` 或 `.mvn/`，所以这些命令片段已经过期。这属于文档清理，不是代码删除。 |

## 仅限本地清理

这些是磁盘上存在的 ignored 产物。它们可以在本地删除，不需要产生 Git 变更；因为它们没有被跟踪，所以也不应该作为删除提交。

| 本地路径 | 为什么本地删除安全 | 注意事项 |
| --- | --- | --- |
| `.DS_Store`、`src/.DS_Store`、`src/main/.DS_Store`、`src/main/java/.DS_Store`、`src/main/java/com/.DS_Store`、`src/main/java/com/example/.DS_Store`、`logs/.DS_Store` | macOS 元数据，已被 `.gitignore` 忽略。 | 无。 |
| `.idea/` | IDE 元数据，已被 `.gitignore` 忽略。 | 如果你想保留本地 IntelliJ 设置，就不要删。 |
| `target/` | Maven 构建输出，已被 `.gitignore` 忽略。 | 可由 Maven 重新生成。 |
| `frontend/node_modules/` | npm 安装产物，已被 `frontend/.gitignore` 忽略。 | 可在 `frontend/` 下通过 `npm ci` 重新生成。 |
| `frontend/dist/` | Vite 构建输出，已被 `frontend/.gitignore` 忽略。 | 可在 `frontend/` 下通过 `npm run build` 重新生成。 |
| `admin-frontend/node_modules/` | 旧管理端位置残留的依赖目录。 | 确认没有人再运行旧的 `admin-frontend/` 应用后可删。 |
| `admin-frontend/dist/` | 旧管理端位置残留的构建输出。 | 删除过时的已跟踪文件 `admin-frontend/.gitignore` 后可删。 |
| `.opencode/node_modules/` | 工具依赖缓存，已被忽略。 | 如果需要，工具会重新生成。 |
| `logs/` | 运行时日志，已被忽略。 | Docker Compose 可能会重新创建该目录，因为 `app` 挂载了 `./logs:/app/logs`。 |

## 暂时不要删除

| 候选项 | 为什么不要删 |
| --- | --- |
| `books/` | 应用会从这个目录导入辞书，并且 Docker Compose 挂载了 `./books:/app/books`。它很大，但属于功能数据。 |
| `maven-repo/` | 它被忽略，看起来像缓存，但 `Dockerfile` 明确执行 `COPY maven-repo ./maven-repo`，并用 `-Dmaven.repo.local=/workspace/maven-repo` 构建。除非先修改 Dockerfile，否则删除它可能导致 Docker 构建失败。 |
| `pom.xml` 中的 checkstyle 插件块 | 它指向缺失的 `checkstyle.xml`，所以质量检查命令很可能是坏的，但删除这个块属于规范策略决定。更安全的做法是补上 `checkstyle.xml`，或同时移除 AGENTS/README 中对 checkstyle 的承诺。 |

## 建议的第一批清理 PR

1. 删除过时的已跟踪文件：`admin-frontend/.gitignore`、未使用的前端脚手架资源、`sample-data/sample_words.json`，以及可能可以删除的 `.opencode/skill-sync-graph.json`。
2. 从 `pom.xml` 删除重复的 `maven-compiler-plugin` 块。
3. 替换根应用的 Vite favicon 引用，然后删除 `frontend/public/vite.svg`。
4. 运行 `mvn -q -DskipTests package` 和 `npm --prefix frontend run build`。
5. 如果修改了任何 `frontend/` 文件，在确认清理完成前，需要按仓库要求运行 `docker-compose up -d --build frontend` 重建统一前端容器。
