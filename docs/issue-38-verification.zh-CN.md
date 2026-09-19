# Issue 38 构建验证

以下命令在 2026-09-19 验证。Maven 会读取仓库内 `.mvn/maven.config`
指定的 `settings.xml`。

## 后端与 Docker

```powershell
mvn -Dtest=DictionaryWordAiAutofillServiceTest test
mvn -DskipTests clean package
docker compose up -d --build app frontend
```

AI 单词自动补全测试 4 个用例均通过。`-DskipTests clean package` 未执行测试，
但完成了 `testCompile` 并生成 JAR。Docker 镜像构建与服务启动均通过；前端根路径
和 `/api/auth/quote` 均返回 HTTP 200。Dockerfile 使用 `-DskipTests`，不会跳过
测试源码编译。

## 生成缓存

```powershell
git check-ignore -v -- tools/__pycache__ tools/example.pyc tools/example.pyo
```

三条路径均由 `.gitignore` 忽略规则匹配。

## 前端

在 `frontend/`：

```powershell
npm ci
npm run test
npm run build
```

干净安装和统一前端生产构建均通过。测试有 51 个通过、1 个既有失败：
`frontend/tests/unified-container-config.test.mjs` 仍断言旧的 `8083:80` 端口映射，
而当前生产配置要求前端公开 `80:80`；该失败与 Issue 38 无关。

在 `frontend/admin/`：

```powershell
npm ci
npm run test
npm run build
```

干净安装、管理端测试（18 个文件、116 个测试）和生产构建均通过。构建中的
Browserslist、CSS 兼容性和大体积 chunk 警告不影响退出状态。

本机的 `npm` 启动脚本指向缺失的用户级安装，因此本次实际验证通过系统 npm CLI
完成；它与以上标准 `npm` 命令等价，不属于项目问题。
