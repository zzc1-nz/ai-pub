# AI-Pub 智能公众号管理系统 — 部署手册

## 1. 环境要求

| 组件 | 最低版本 | 说明 |
|------|---------|------|
| Docker | 20.10+ | 含 Docker Compose v2 |
| 操作系统 | Windows 10+ / Linux / macOS | 需支持 Docker |
| 内存 | 8GB+ | MySQL + Redis + RabbitMQ + Java + Nginx |
| 磁盘 | 10GB+ 可用空间 | Docker 镜像约 2GB |
| 端口 | 3000, 8080, 3307, 6379, 5672, 15672 | 确保未被占用 |

## 2. 快速部署

### 2.1 拉取并启动所有服务

```bash
cd deploy
docker compose up -d
```

首次启动会自动构建前后端镜像，约需 3-5 分钟。

### 2.2 查看服务状态

```bash
docker compose ps
```

所有服务 STATUS 显示 `healthy` 或 `Up` 即为正常：

```
NAME               STATUS
ai-pub-mysql       Up (healthy)
ai-pub-redis       Up (healthy)
ai-pub-rabbitmq    Up (healthy)
ai-pub-backend     Up
ai-pub-frontend    Up
```

### 2.3 访问系统

| 地址 | 用途 |
|------|------|
| http://localhost:3000 | 管理后台前端 |
| http://localhost:8080/doc.html | API 接口文档 |
| http://localhost:15672 | RabbitMQ 管理界面 |

**默认管理员账号**：`admin` / `admin123456`

## 3. 服务端口与账号

| 服务 | 端口 | 账号/密码 |
|------|------|-----------|
| 前端 Nginx | 3000 | — |
| 后端 API | 8080 | — |
| MySQL | 3307 | root / root |
| Redis | 6379 | 无密码 |
| RabbitMQ | 5672 (AMQP), 15672 (管理) | guest / guest |

## 4. 配置说明

### 4.1 环境变量（docker-compose.yml 中配置）

后端容器接收以下环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| SPRING_DATASOURCE_URL | jdbc:mysql://mysql:3306/ai_pub... | 数据库连接 |
| SPRING_DATASOURCE_USERNAME | root | 数据库用户 |
| SPRING_DATASOURCE_PASSWORD | root | 数据库密码 |
| SPRING_DATA_REDIS_HOST | redis | Redis 主机 |
| SPRING_RABBITMQ_HOST | rabbitmq | RabbitMQ 主机 |
| DEEPSEEK_API_KEY | sk-d68a6da7... | AI 模型 API Key |

### 4.2 修改 AI API Key

编辑 `deploy/.env` 文件：

```bash
DEEPSEEK_API_KEY=你的API密钥
```

修改后重启：

```bash
docker compose up -d --force-recreate backend
```

### 4.3 数据库持久化

数据库文件存储在 Docker Volume `deploy_mysql-data` 中，容器重启不会丢失。

备份数据：

```bash
docker exec ai-pub-mysql mysqldump -uroot -proot ai_pub > backup.sql
```

恢复数据：

```bash
docker exec -i ai-pub-mysql mysql -uroot -proot ai_pub < backup.sql
```

## 5. 验证步骤

### 5.1 健康检查

```bash
# MySQL
docker exec ai-pub-mysql mysqladmin ping -uroot -proot

# Redis
docker exec ai-pub-redis redis-cli ping

# RabbitMQ
docker exec ai-pub-rabbitmq rabbitmq-diagnostics ping

# 后端
curl http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123456"}'
```

### 5.2 功能验证清单

- [ ] 登录：http://localhost:3000 → admin/admin123456
- [ ] 控制台：Dashboard 页面显示统计数据
- [ ] 公众号管理：可查看/新增/编辑公众号
- [ ] 菜单管理：可配置自定义菜单
- [ ] 素材管理：可上传/管理图文素材
- [ ] 回复规则：可创建关键词匹配规则
- [ ] 消息列表：可查看历史消息
- [ ] 用户管理：可管理管理员账号
- [ ] API文档：http://localhost:8080/doc.html 可见所有接口

## 6. 常用运维命令

```bash
# 停止所有服务
docker compose down

# 重启单个服务
docker compose restart backend

# 查看后端日志
docker compose logs -f backend

# 查看最近 100 行日志
docker compose logs --tail 100 backend

# 重新构建并启动
docker compose up -d --build

# 完全清除（含数据卷）
docker compose down -v
```

## 7. 常见问题

### Q: 启动后前端页面空白？

查看后端日志：`docker compose logs backend`，确认 Spring Boot 已启动完毕（看到 `Started AiPubApplication`）。

### Q: MySQL 连接失败？

确认 3307 端口未被占用。Windows 上检查：
```bash
netstat -ano | findstr 3307
```

### Q: 端口被占用？

编辑 `docker-compose.yml` 修改端口映射，例如将前端改为 `"3001:80"`。

### Q: Docker 构建时下载依赖慢？

Maven 构建阶段可配置阿里云镜像加速。编辑 `backend/Dockerfile`，在 `RUN mvn` 前添加：
```dockerfile
COPY settings-docker.xml /root/.m2/settings.xml
```

### Q: DeepSeek API 调用失败？

检查 `DEEPSEEK_API_KEY` 是否有效，确认网络可访问 `https://api.deepseek.com`。

## 8. 项目结构

```
bishe/
├── deploy/                    # 部署目录
│   ├── docker-compose.yml     # 服务编排
│   ├── start-ai-pub.bat       # Windows 一键启动
│   └── DEPLOY.md              # 本文件
├── backend/                   # 后端 Spring Boot
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── frontend/                  # 前端 Vue 3
│   ├── Dockerfile
│   └── src/
└── README.md
```
