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
