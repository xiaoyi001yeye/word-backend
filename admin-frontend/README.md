# Word Atelier Admin

一个独立于现有 `frontend/` 的后台前端服务，使用 `SolidJS + Tailwind + shadcn-solid` 风格组件体系实现，面向管理员和老师。

## 功能范围

- 登录与登录态守卫
- 管理员总览 / 老师工作台
- 用户与师生关系管理
- 班级管理
- 词书资源管理与班级分配
- 学习计划创建、发布、概览查看
- 词书批量导入中心

## 本地开发

```bash
cd admin-frontend
npm install
npm run dev
```

默认开发端口是 `4174`，并通过 Vite 代理把 `/api` 转发到 `http://127.0.0.1:8080`。

如果后端不在这个地址启动，可以临时覆盖：

```bash
ADMIN_FRONTEND_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
```

## 生产构建

```bash
cd admin-frontend
npm run build
```

## Docker 运行

主 `docker-compose.yml` 已经包含这个管理端服务，不需要额外的 compose 文件。

```bash
docker-compose up -d --build
```

启动后访问：

- `http://localhost/`
- `http://localhost:8081/`

## 说明

- 当前阶段由这个目录承接登录和后台入口，`frontend/` 容器不再对外暴露端口。
- UI 组件采用 shadcn-solid 的 manual installation 方式组织，后续可以继续往 `src/components/ui/` 扩展。
