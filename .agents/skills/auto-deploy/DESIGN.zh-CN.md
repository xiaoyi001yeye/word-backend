# 自动部署 Skill 设计文档

## 1. 文档目标

本文档设计一个可被 AI 代理（agent）调用的「自动部署」skill，把本项目的 **构建 → 打包 → 传输 → 部署 → 验证 → 回滚** 全流程自动化，覆盖本地发布与远程生产部署两种场景，并给出明确的健康检查、回滚与排障路径。

本 skill 的定位：**编排与门禁**。它复用仓库内已验证的构建/加载脚本（`build-release.sh`、`load`），补齐远程部署自动化、前置检查、测试门禁、验证验收、回滚与安全约束，不重写现有 Docker 编排逻辑。

本文档只做设计，不直接实现 skill（SKILL.md 在下一步实现）。

## 2. 当前部署资产盘点

### 2.1 已有资产

| 资产 | 作用 | 现状 |
|------|------|------|
| `docker-compose.yml` | 开发编排：db(5432) + app(8082) + frontend(80)，镜像 `words-app:latest` / `words-frontend:latest` | ✅ 可用 |
| `compose.production.yml` | 生产编排：db + app + frontend(80)，app 不对外暴露端口，健康检查齐全，密钥全部走 `.env` | ✅ 可用 |
| `Dockerfile` | 后端镜像：maven 多阶段构建（使用 `maven-repo/` + `settings.xml`，`-DskipTests`）→ temurin-17-jre，构建期包含 `books/` | ✅ 可用 |
| `frontend/Dockerfile` | 前端镜像：node:20-alpine 构建（根应用 + admin 子应用）→ nginx:alpine | ✅ 可用 |
| `frontend/nginx.conf` | `/api` 反代到 `app:8080`、缓存策略、admin 子应用路由、`client_max_body_size 500m` | ✅ 可用 |
| `build-release.sh` | 构建两个镜像（`--pull --no-cache`）→ 导出 `release/words-images-<版本>.tar.gz` + `.sha256` | ✅ 可用，仅本机 |
| `load` | 校验 checksum → `docker load` → `docker compose up -d --no-build --wait` | ✅ 可用，仅本机、走开发编排 |
| `config/deployment-target.yml` | 生产目标元数据：host `124.174.44.175`、ssh alias `word-prod`、user `root`、目录 `/opt/word-backend` | ⚠️ 死配置，无脚本使用 |
| `README.md` | 手工流程文档：build → load → `docker compose --env-file .env -f compose.production.yml up -d --no-build --wait --wait-timeout 240` | ✅ 有文档 |

### 2.2 缺口分析

1. **远程部署完全靠手工**：没有任何脚本使用 `config/deployment-target.yml`；传输发布包、远端加载、远端编排、远端验证均无自动化。
2. **无前置门禁**：不检查 Docker daemon、ssh 连通性、`.env` 密钥是否齐全、git 工作区状态、磁盘空间。
3. **无部署前测试门禁**：发布镜像构建时后端 `-DskipTests`，前端不跑测试；上线前无强制测试关卡。
4. **无验收环节**：部署后不自动验证 `/api/auth/quote` 健康检查、前端 80 端口、`/api` 反代、`books/` 词书文件数量。
5. **无回滚能力**：旧版本发布包虽有留存，但无"加载上一版本并恢复服务"的自动化路径；也缺少 Flyway 迁移不可逆的提醒。
6. **无版本/清理策略**：`release/` 会无限堆积，无保留 N 份、清理旧包策略。
7. **无统一状态报告**：部署结果（版本、镜像 digest、容器健康、验收项）没有结构化输出。

## 3. 设计目标

### 3.1 核心目标

- 一句话指令即可完成一次标准部署（本地发布 / 远程生产部署），agent 按既定流程逐步执行并报告。
- 部署前自动做环境与门禁检查，问题提前暴露，而不是部署到一半失败。
- 部署后自动验收：应用健康、前端可访问、`/api` 反代可用、词书文件在位。
- 提供明确的回滚路径，并对 Flyway 迁移不可逆给出显式警告。
- 全程不泄漏密钥，遵守 AGENTS.md 与 README 的生产约束。

### 3.2 非目标（边界）

- **不做数据库迁移回滚**：Flyway 迁移是前向的，回滚只恢复应用/前端镜像版本，不撤销已执行的 migration；涉及迁移时给出显式警告（可参考 `docs/flyway-migration-minimal-impact-recommendation.zh-CN.md`）。
- **不做多环境管理**：当前只有 production 一个部署目标（`config/deployment-target.yml`），skill 按单目标设计，多环境留作扩展。
- **不做 CI 触发**：不在本 skill 内实现 GitHub Actions；可留接口由 CI 调用构建脚本。
- **不替代现有脚本**：`build-release.sh`、`load` 的已验证逻辑直接复用，skill 只做编排与扩展（远程传输/远端执行）。
- **不处理词书热更新**：`books/` 在构建期打入镜像，词书更新必须重新构建发布包（生产禁止宿主机挂载 `./books`）。
- **不做灰度/蓝绿**：当前是"导入镜像 → 重建容器"的全量更新方式。

## 4. 功能清单

### F1 环境与前置检查（Preflight）

**目的**：在任何构建/部署动作之前确认环境可用，失败即中止并给出可执行建议。

- 本地：`docker info` 可达；`docker compose version`；`shasum`/`sha256sum` 存在；磁盘空间充足（预计发布包体积）；`git status` 是否干净、当前分支是否为 `main`（警告，不强制）。
- 密钥：`compose.production.yml` 所需密钥（`POSTGRES_PASSWORD`、`SECURITY_JWT_SECRET`、`AI_CONFIG_ENCRYPTION_KEY`、`VIDEO_STORAGE_CONFIG_ENCRYPTION_KEY`）在 `.env` 中存在且非空；`.env` 必须被 `.gitignore` 忽略、绝不出现在 `git status` 中。
- 远程（仅远程模式）：`ssh word-prod` 连通（`ssh -o BatchMode=yes -o ConnectTimeout=10` 探测）；远端 docker 可达；远端目标目录 `/opt/word-backend` 存在或可创建；远端 `.env` 存在且密钥齐全。
- 输出：检查清单（每一项 ✅/❌ + 修复建议），任一 ❌ 即中止。

### F2 构建（Build）

**目的**：产出最新的 `words-app:latest` 与 `words-frontend:latest` 镜像。

- 标准模式：复用 `build-release.sh` 的 `docker compose build --pull --no-cache app frontend`。
- 可选加速模式：`--no-cache` 改为增量构建（用户显式要求时）。
- 可选测试门禁（默认开启，可跳过）：后端 `./mvnw verify`、前端 `npm run build`/相关测试通过后才进入镜像构建；失败即中止，不产出发布包。
- 验证：构建后 `docker image inspect words-app:latest words-frontend:latest` 确认存在，并记录镜像 digest。

### F3 打包（Package）

**目的**：生成可传输、可校验的发布归档。

- 版本号：默认 `YYYYMMDD-HHMMSS`（对齐 `build-release.sh`），支持显式指定；格式校验 `^[a-zA-Z0-9._-]+$`。
- 归档：`release/words-images-<版本>.tar.gz` + `.sha256`（复用 `build-release.sh` 的原子写入：临时文件 + rename + trap）。
- 可选扩展：归档内附 `compose.production.yml`、`config/deployment-target.yml`、`.env.example`（不含真实密钥），便于远端自包含部署。
- 清理策略：默认保留最近 N 份（如 5）发布包，超出部分在部署成功后清理（需用户确认）。

### F4 本地部署（Local Deploy）

**目的**：在当前机器上完成"加载发布包 → 启动服务 → 验收"。

- 复用 `load`：校验 checksum → `docker load` → `docker compose up -d --no-build --wait --wait-timeout 180`。
- 可选使用生产编排：显式要求时改用 `docker compose --env-file .env -f compose.production.yml up -d --no-build --wait --wait-timeout 240`（需本机 `.env`）。
- 失败处理：`--wait` 超时后收集 `docker compose ps` 与各容器日志定位原因。

### F5 远程生产部署（Remote Deploy）

**目的**：把发布包部署到 `config/deployment-target.yml` 描述的生产主机。

- 读取目标配置：`config/deployment-target.yml`（host / ssh alias / user / project_directory）。
- 传输：`scp`（或 `rsync -z`）把 `.tar.gz` + `.sha256` 传到远端 `project_directory/release/`；传输后本地与远端比对 sha256。
- 远端执行（通过 ssh，逐条命令带 `set -euo pipefail`）：
  1. 校验 checksum；
  2. `docker load -i <归档>`；
  3. `docker image inspect words-app:latest words-frontend:latest`；
  4. `docker compose --env-file .env -f compose.production.yml up -d --no-build --wait --wait-timeout 240`（远端目录内执行）。
- 传输出错/远端执行失败：中止并保留现场（容器、日志），不自动回滚（回滚需显式指令）。

### F6 验证与健康检查（Verify）

**目的**：部署后逐项验收，输出验收清单。

- 后端健康：`docker inspect` 健康状态；`/api/auth/quote` 返回 200（后端健康检查端点）。
- 前端可用：`http://<host>:80/` 返回 200；`/admin/` 可访问。
- 反代链路：通过 nginx 访问 `/api/...` 确认代理到 `app:8080` 正常。
- 词书文件：`docker exec words-app sh -lc "find /app/books -maxdepth 1 -type f | wc -l"` 数量 > 0（README 明确此验收项）。
- 编排状态：`docker compose ps` 三服务全部 healthy/running。
- 数据库：`pg_isready`（db 容器健康检查已含）；可选抽查关键表行数。
- 任一验收失败：报告失败项与日志线索，给出修复建议，不掩盖问题。

### F7 回滚（Rollback）

**目的**：恢复到上一个（或指定）发布版本。

- 本地：选择 `release/` 中指定归档 → 校验 → `docker load` → `docker compose up -d --no-build --wait`。
- 远程：把指定归档传输到远端 → 远端校验 + load + compose up。
- 前置警告：若本次部署包含新的 Flyway migration，回滚应用版本**不会撤销已执行的迁移**；提示评估是否需要手工迁移补救或保留新版本数据。
- 保护：回滚前确认当前运行版本与目标版本；回滚后照常执行 F6 验收。

### F8 日志与排障（Troubleshoot）

**目的**：部署失败或运行异常时快速定位。

- `docker compose logs --tail=N [-f] app|frontend|db`；支持时间范围过滤。
- 容器状态与重启次数：`docker inspect` 关键字段（RestartCount、Health、LastExitCode）。
- 常见故障手册（写进 skill）：
  - 镜像找不到：发布包未加载/load 失败 → 重跑 load；
  - 健康检查超时：密钥缺失（`InvalidDataAccessResourceUsageException`/启动失败）、数据库未就绪、端口冲突 → 查 app 日志与 `.env`；
  - 词书缺失：宿主机挂载覆盖了镜像内 `books/` → 检查编排是否有 `./books` 挂载；
  - 前端 502：app 未就绪或 `/api` 反代配置问题 → 查 nginx 错误日志与 app 健康；
  - Flyway 迁移失败：版本冲突/表结构变更 → 查 app 启动日志，迁移需单独处理。

### F9 状态报告（Report）

**目的**：给用户结构化、可复查的部署结论。

- 部署信息：模式、版本号、归档路径、镜像 digest、部署时间。
- 验收清单：F6 各项结果。
- 现场信息：`docker compose ps` 摘要、健康状态、日志位置。
- 结论：✅ 成功 / ⚠️ 部分失败 / ❌ 失败 + 下一步建议。

### F10 安全与合规约束（Safety）

- 密钥（JWT secret、加密密钥、DB 密码）**绝不**写入日志、报告或命令输出；`.env` 不提交、不传输明文（远端 `.env` 由运维预置）。
- 生产规则（AGENTS.md）：frontend 暴露宿主机 **80:80**；db/backend **不得**直接暴露公网；`/api` 必须经 frontend nginx 反代。
- 生产编排**不得**把宿主机 `./books` 挂载到 `/app/books`（README 明确禁令）。
- 破坏性操作（重建容器、清理旧发布包、回滚）执行前需用户确认。
- 不修改 AGENTS.md 中的其他工程约束（如禁用级联删除、学生积分单实例处理）。

## 5. 部署模式与工作流

skill 支持四种模式，由用户一句话触发：

| 模式 | 触发示例 | 流程 |
|------|---------|------|
| A. 本地发布 | "本地发布一版" | F1 → F2(可选测试) → F3 → F4 → F6 → F9 |
| B. 远程生产部署 | "部署到生产" | F1 → F2(可选测试) → F3 → F5 → F6 → F9 |
| C. 仅远程更新（用已有包） | "用 release/xxx.tar.gz 部署生产" | F1(远程) → F5 → F6 → F9 |
| D. 回滚 | "回滚到上一个版本" | F1(相关部分) → F7 → F6 → F9 |

标准时序（模式 B）：

```
用户指令
  → F1 前置检查（本地+远程）
  → [门禁] 测试通过？
  → F2 构建镜像 → F3 打包+校验
  → F5 传输 → 远端校验 → 远端 load → 远端 compose up --wait
  → F6 验收（健康/前端/反代/词书/编排）
  → F9 报告
```

任意一步失败：中止、保留现场、报告失败点与修复建议；除非用户显式要求，不自动回滚。

## 6. 配置与输入

### 6.1 部署目标（`config/deployment-target.yml`，沿用并作为唯一事实源）

```yaml
environment: production
host: 124.174.44.175
ssh:
  alias: word-prod
  user: root
  project_directory: /opt/word-backend
```

skill 读取该文件获取远程信息；扩展预留字段（如 `compose_file`、`env_file`），当前使用默认值。

### 6.2 必需密钥（远端与本地 `.env`，仅远端/生产模式必检）

`POSTGRES_PASSWORD`、`SECURITY_JWT_SECRET`、`AI_CONFIG_ENCRYPTION_KEY`、`VIDEO_STORAGE_CONFIG_ENCRYPTION_KEY`

### 6.3 输入参数（agent 从用户指令解析）

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| 模式 | 是 | — | local / remote / remote-with-archive / rollback |
| 版本号 | 否 | `YYYYMMDD-HHMMSS` | 格式 `^[a-zA-Z0-9._-]+$` |
| 发布包路径 | 模式 C/D 必填 | release/ 最新 | 指定归档 |
| 跳过测试 | 否 | false | `--skip-tests` |
| 增量构建 | 否 | false | 不用 `--no-cache` |
| 保留份数 | 否 | 5 | 发布包清理策略 |

## 7. 与现有脚本的关系

- **直接复用**：`build-release.sh`（构建+导出+校验）、`load`（校验+加载+启动）——skill 调用它们，不复制逻辑。
- **扩展点**：新增远程传输/远端执行封装；`load` 增加 `-f compose.production.yml` 分支；`config/deployment-target.yml` 首次被真正消费。
- **兼容性**：skill 不改变现有脚本的独立可用性，不破坏 README 中已文档化的手工流程。

## 8. 验收标准

1. 模式 A 全流程在本机跑通：构建 → 打包 → 加载 → 服务健康，验收清单全绿。
2. 模式 B 在预置 ssh 密钥与远端 `.env` 的前提下跑通：传输校验、远端部署、健康验收全绿。
3. 模式 C 用已有发布包可完成远程更新。
4. 模式 D 可恢复到上一版本并验收通过；含迁移的部署回滚会给出 Flyway 不可逆警告。
5. 全程日志与报告不含任何密钥明文。
6. 部署结果报告包含版本、digest、验收清单、容器状态，可复查。

## 9. 未来扩展

- **CI 集成**：GitHub Actions 在 tag push 时调用 `build-release.sh` 产出发布包，skill 专注"部署/回滚"。
- **多环境/多目标**：`config/deployment-target.yml` 支持多目标文件或目标选择参数。
- **部署前迁移备份**：对涉及 Flyway 迁移的发布，自动在远端先做 `pg_dump` 备份。
- **灰度/健康路由**：先起新版本做探活，再切换流量。

## 10. Skill 文件结构

```
.agents/skills/auto-deploy/
├── SKILL.md            # 技能指令（下一步实现，含前置条件、流程、命令、故障手册）
├── DESIGN.zh-CN.md     # 本文档
└── scripts/            # 可选：skill 自带辅助脚本（远程传输/远端执行封装）
```