# 单词项目后端

基于 Java 开发的单词学习应用后端服务，使用 Docker 管理服务状态，PostgreSQL 作为数据库。

## 技术栈

- **Java** - 后端开发语言
- **PostgreSQL** - 关系型数据库
- **Docker** - 容器化部署与管理

## 项目结构

```
.
├── src/                    # 源代码目录
├── docker-compose.yml      # Docker 编排配置
├── Dockerfile             # 应用镜像构建文件
└── README.md              # 项目说明文档
```

## 快速开始

### 环境要求

- JDK 17+
- Docker & Docker Compose
- Maven / Gradle

### 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 停止服务
docker-compose down
```

### 构建发布镜像

重新构建后端和前端镜像，并将它们导出到 `release/`：

```bash
./build-release.sh
```

也可以指定发布版本号，版本号会写入归档文件名：

```bash
./build-release.sh 1.0.0
```

后端镜像包含构建时的 `books/` 目录。生成的 `.tar.gz` 文件可以通过以下命令导入：

```bash
docker load -i release/words-images-1.0.0.tar.gz
```

也可以使用 `load` 脚本自动校验并导入 `release/` 中最新的发布包，然后启动服务：

```bash
./load
```

手动指定发布包：

```bash
./load release/words-images-1.0.0.tar.gz
```

### Production 部署

Production 使用 `compose.production.yml`。后端镜像已经包含构建时的 `books/` 目录，生产编排不得将宿主机
`./books` 挂载到 `/app/books`，否则空的宿主机目录会覆盖镜像内的词书文件，导致词书导入失败。

导入发布镜像后，使用以下命令重新创建应用并等待健康检查：

```bash
docker compose --env-file .env -f compose.production.yml up -d --no-build --wait --wait-timeout 240
```

部署后确认容器使用镜像内的词书文件：

```bash
docker exec words-app sh -lc "find /app/books -maxdepth 1 -type f | wc -l"
```

### 数据库配置

默认数据库连接信息：

| 配置项 | 值 |
|--------|-----|
| Host | localhost |
| Port | 5432 |
| Database | words |
| Username | postgres |
| Password | postgres |

## API 文档

启动服务后访问：`http://localhost:8080/api/docs`

## 开发指南

```bash
# 构建项目
./mvnw clean install

# 运行测试
./mvnw test

# 本地运行
./mvnw spring-boot:run
```

## License

MIT
