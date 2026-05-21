# AI-Pub 智能公众号管理系统

基于 Spring Boot + Spring AI + Vue 3 的智能公众号管理与自动回复系统

## 技术栈

### 后端
- Spring Boot 3.2
- Spring AI (DeepSeek 集成)
- Spring Data JPA
- Spring Security + JWT
- MySQL 8.0
- Redis 7
- RabbitMQ 3
- Lombok

### 前端
- Vue 3
- Element Plus
- Vue Router
- Pinia
- Axios
- ECharts
- Vite

## 功能特性

### 1. 用户管理
- 管理员登录（JWT认证）
- 运营人员账号CRUD

### 2. 公众号管理
- 多公众号配置管理
- 微信接入验证
- 自定义菜单管理（同步到微信）
- 素材管理（图文、图片、语音）
- 自动回复规则配置

### 3. 消息处理
- 微信回调接收与验证
- 消息入库存储
- 消息检索查询

### 4. AI智能对话
- DeepSeek 大模型集成
- 多轮对话上下文（Redis存储）
- 关键词优先匹配
- 超时降级处理

### 5. 异步处理
- RabbitMQ 异步消息处理
- Redis 缓存 Access Token

### 6. 数据统计
- 消息量统计
- 回复类型分布
- 趋势图表展示

## 快速开始

### 方式一：Docker Compose 部署（推荐）

1. 设置环境变量
```bash
export DEEPSEEK_API_KEY=your_deepseek_api_key
```

2. 启动所有服务
```bash
cd deploy
docker-compose up -d
```

3. 访问应用
- 前端: http://localhost:3000
- 后端API: http://localhost:8080
- RabbitMQ管理: http://localhost:15672 (guest/guest)

### 方式二：本地开发

#### 启动基础设施
```bash
cd deploy
docker-compose up -d mysql redis rabbitmq
```

#### 后端启动
```bash
cd backend
mvn spring-boot:run
```

#### 前端启动
```bash
cd frontend
npm install
npm run dev
```

## 默认账号

- 管理员账号: `admin`
- 密码: `admin123456`

## 项目结构

```
bishe/
├── backend/                 # 后端项目
│   ├── src/main/java/com/ai/pub/
│   │   ├── entity/     # 实体类
│   │   ├── controller/ # 控制器
│   │   ├── service/    # 服务层
│   │   ├── repository/ # 数据访问
│   │   ├── config/     # 配置类
│   │   └── common/     # 公共类
│   ├── pom.xml
│   └── Dockerfile
├── frontend/               # 前端项目
│   ├── src/
│   │   ├── views/          # 页面
│   │   ├── router/         # 路由
│   │   ├── store/          # 状态管理
│   │   └── api/            # API封装
│   ├── package.json
│   └── Dockerfile
└── deploy/                 # 部署文件
    ├── docker-compose.yml
    ├── nginx.conf
    ├── Dockerfile
    └── start-ai-pub.bat
```

## API 文档

启动后端服务后，可访问 Swagger 文档（如配置）:
http://localhost:8080/swagger-ui.html

## 微信配置

1. 在后台添加公众号配置
2. 配置服务器地址: `https://your-domain/wx/callback/{accountId}`
3. 配置 Token 和 EncodingAESKey
4. 点击"测试接入"验证配置

## 许可证

MIT License
