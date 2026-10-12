# AI-Pub 智能公众号管理系统

基于 **Spring Boot 3.2 + Spring AI + Vue 3** 的微信公众号管理与智能自动回复系统。

面向公众号运营者，把微信服务器推送的用户消息经过 **关键词规则 → 大模型生成 → 兜底话术** 三级决策后自动回复，同时提供公众号配置、菜单同步、素材管理、消息检索与数据统计能力。

---

## 目录

- [核心特性](#核心特性)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [环境变量](#环境变量)
- [默认账号](#默认账号)
- [微信配置](#微信配置)
- [项目结构](#项目结构)
- [测试](#测试)
- [论文实验](#论文实验)
- [性能压测](#性能压测)
- [监控](#监控)
- [安全说明](#安全说明)
- [已知限制](#已知限制)
- [许可证](#许可证)

---

## 核心特性

### 1. 三级混合回复决策

```
① 关键词规则匹配（命中即返回：0 延迟、0 成本、100% 可控）
        ↓ 未命中
② 大模型生成（理解自然语言，覆盖规则之外的长尾问题）
        ↓ 失败 / 未启用
③ 默认话术兜底（保证"永不沉默"）
```

选择混合而非纯 AI 的原因：约 60–70% 的咨询是固定问题（营业时间、退款流程），用大模型回答浪费成本；涉及价格与承诺的话术运营必须能 100% 控制原文；且大模型 API 会超时、限流、欠费，规则匹配不依赖外部服务。

### 2. AI 四层算法

| 层次 | 方法 | 说明 |
|---|---|---|
| 第一层 | 文本预处理 | 中文 unigram + bigram 切分，过滤 60 个停用词 |
| 第二层 | 意图识别 | TF-IDF 加权 + 余弦相似度，5 类意图（问候/投诉/退换/咨询/感谢） |
| 第三层 | 情感分析 | 情感词典加权平均，5 档极性 |
| 第四层 | 融合决策 | `D = 0.5·意图 + 0.3·情感 + 0.2·紧急度`，动态拼装 System Prompt |

配合 **Token 预算滑动窗口**（默认 2000 token，从最新消息往前贪心填充）控制成本与上下文长度。

### 3. 消息可靠性

| 机制 | 实现 |
|---|---|
| 异步削峰 | RabbitMQ 队列，微信回调立即返回 `success`（避免 5 秒超时重推） |
| 手动 ACK | `AcknowledgeMode.MANUAL`，处理成功才确认 |
| 幂等 | 消费前检查 `replyContent` 非空则跳过 |
| 死信队列 | `basicNack(requeue=false)` → DLX，毒消息不阻塞队列 |
| 去重 | 按微信 `msgId` 查库跳过重推 |

### 4. access_token 三层缓存

微信 access_token 全局唯一、有效期 7200 秒、并发刷新会使旧 token 失效。

```
第一层：Redis 缓存命中 → 毫秒级返回
        └ TTL < 300s 时触发异步续期，不阻塞当前请求
第二层：未命中 → Redis 分布式锁（防并发击穿）
        ├ 抢到锁 → 双重检查 → 调微信 API → 缓存 TTL = expires_in - 100
        └ 未抢到 → 轮询等待最多 3 秒
```

### 5. 实时消息推送

WebSocket（SockJS + STOMP）订阅 `/topic/messages`，消息到达与 AI 回复生成时各推送一次，后台可实时看到"消息进来 → 回复生成"的过程。

### 6. 其他功能

- **公众号管理**：多公众号配置、微信接入验证、敏感字段加密存储
- **菜单管理**：树形配置、一键同步到微信（微信仅支持两级菜单）
- **素材管理**：图片/语音/视频/图文分类，本地存储 + 同步微信永久素材库
- **自动回复规则**：7 种匹配模式（CONTAINS / EXACT / STARTS_WITH / ENDS_WITH / ALL_CONTAINS / FUZZY / REGEX）
- **消息检索**：按 openid、类型、时间区间分页查询
- **数据统计**：消息量趋势、回复类型分布、素材分布、Top 10 高频问题

---

## 系统架构

```
                    ┌──────────────────────────────┐
   微信用户 ──────▶ │  微信服务器                   │
                    └───────────┬──────────────────┘
                                │ ① 推送 XML 消息
                                ▼
   ┌────────────────────────────────────────────────────────┐
   │  Nginx (:80/:3000)                                      │
   │   /api → backend   /wx → backend   /ws → backend        │
   └────────────────────────┬───────────────────────────────┘
                            ▼
   ┌────────────────────────────────────────────────────────┐
   │  Spring Boot Backend (:8080)                            │
   │                                                          │
   │  WechatCallbackController                                │
   │    ├ SHA1 签名校验                                       │
   │    ├ XML 解析（含 GBK 乱码兜底）                          │
   │    ├ msgId 去重 → 入库 wechat_message                    │
   │    ├ WebSocket 推 /topic/messages ──────▶ 前端实时上屏    │
   │    └ 投递 RabbitMQ ─────┐                                │
   │                          ▼                               │
   │  MessageConsumer（手动ACK + 死信队列 + 幂等）              │
   │    ├ ① WechatReplyService.findReply（关键词规则）         │
   │    ├ ② ChatService.chat（四层算法 + 滑动窗口）             │
   │    └ ③ defaultReply 兜底                                 │
   │                          │                               │
   │                          ├─▶ 微信客服消息 API 回复        │
   │                          └─▶ 回写 replyContent/replyType │
   └───────┬──────────────┬───────────────┬─────────────────┘
           ▼              ▼               ▼
      ┌─────────┐   ┌──────────┐   ┌────────────┐
      │ MySQL 8 │   │ Redis 7  │   │ RabbitMQ 3 │
      │ 业务数据 │   │ 对话上下文│   │ 消息队列    │
      │         │   │ token缓存 │   │ + 死信队列  │
      └─────────┘   └──────────┘   └────────────┘
           │
           ▼
      ┌──────────────────────────────────┐
      │ Prometheus (:9090) → Grafana (:3030) │
      └──────────────────────────────────┘
```

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 17 | **必需**（Spring Boot 3.x 最低要求） |
| Spring Boot | 3.2.0 | 应用框架 |
| Spring Data JPA / Hibernate | 6.3.1 | 持久化 |
| MySQL | 8.0 | 业务数据库（utf8mb4） |
| Spring Data Redis (Lettuce) | 7 | 对话上下文、access_token 缓存 |
| Spring AMQP / RabbitMQ | 3 | 异步消息处理 |
| Spring Security + JJWT | 0.11.5 | JWT 无状态认证 |
| Spring AI | 1.0.0-M4 | OpenAI 协议兼容层（对接 DeepSeek） |
| Actuator + Micrometer | — | Prometheus 指标 |
| Knife4j | 4.5.0 | 接口文档 |
| Lombok | 1.18.30 | 减少样板代码 |

### 前端

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue | 3.5 | Composition API |
| Vite | 5.4 | 构建 + 开发代理 |
| Element Plus | 2.14 | UI 组件库 |
| Pinia | 2.3 | 状态管理 |
| Vue Router | 4.6 | 路由 + 权限守卫 |
| Axios | 1.16 | HTTP 封装 |
| ECharts | 5.6 | 数据可视化 |
| SockJS + STOMP | 7.3 | WebSocket 实时推送 |

### 基础设施

Docker Compose 编排 8 个服务：MySQL、Redis、RabbitMQ、Backend、Frontend、Prometheus、Grafana。

---

## 快速开始

### 方式一：Docker Compose（推荐）

```bash
# 1. 准备环境变量
cd deploy
cp .env.example .env
#    编辑 .env，至少填入 DEEPSEEK_API_KEY
#    AES_SECRET_KEY / JWT_SECRET 不填会用内置默认值并在启动日志中告警

# 2. 一键启动全部服务
docker-compose up -d

# 3. 查看启动日志（首次构建镜像需要几分钟）
docker-compose logs -f backend
```

### 方式二：本地开发

```bash
# 1. 只启动基础设施
cd deploy
docker-compose up -d mysql redis rabbitmq

# 2. 启动后端（需 JDK 17）
cd ../backend
mvn spring-boot:run

# 3. 启动前端
cd ../frontend
npm install
npm run dev
```

### 访问地址

| 服务 | 地址 | 凭据 |
|---|---|---|
| 前端管理后台 | http://localhost:3000 | admin / admin123456 |
| 后端 API | http://localhost:8080 | — |
| 接口文档（Knife4j） | http://localhost:8080/doc.html | — |
| RabbitMQ 管理台 | http://localhost:15672 | guest / guest |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3030 | admin / admin123 |

---

## 环境变量

在 `deploy/.env` 中配置（模板见 `deploy/.env.example`，`.env` 已被 gitignore）。

| 变量 | 必需 | 说明 |
|---|---|---|
| `DEEPSEEK_API_KEY` | 建议 | DeepSeek API Key。未配置时 AI 回复会全部降级为默认话术（不报错，但功能不可用） |
| `AES_SECRET_KEY` | 生产必需 | AES-256 密钥，用于加密存储公众号 AppSecret。未配置时使用内置默认值并告警 |
| `JWT_SECRET` | 生产必需 | JWT 签名密钥。未配置时使用配置文件默认值并告警 |
| `APP_UPLOAD_DIR` | 否 | 素材存储目录，默认 `./uploads`（容器内为 `/app/uploads`） |
| `SHOW_SQL` | 否 | 是否输出 SQL 日志，默认 `false` |

生成随机密钥：

```bash
openssl rand -base64 32     # AES_SECRET_KEY
openssl rand -base64 48     # JWT_SECRET
```

Windows PowerShell：

```powershell
$a = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($a)
[Convert]::ToBase64String($a)
```

---

## 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123456` | ADMIN |

> 系统启动时会自动补建该管理员账号（见 `DataInitializer`），`deploy/schema.sql` 中也有对应的种子数据，即使初始化脚本未执行也能登录。**生产环境请立即修改密码。**

---

## 微信配置

1. 在「公众号管理」中新增公众号，填写 AppID / AppSecret / Token
2. 在微信公众平台配置服务器地址：
   ```
   https://你的域名/wx/callback/{accountId}
   ```
   `{accountId}` 为后台该公众号的 ID
3. 填写与后台一致的 Token（用于 SHA1 签名校验）
4. 在后台点击「测试接入」验证配置

> **本地调试提示**：微信服务器需要访问公网地址，本地开发可用内网穿透工具（如 ngrok、frp）把 8080 端口暴露出去。

---

## 项目结构

```
bishe/
├── backend/                                  # Spring Boot 后端
│   ├── src/main/java/com/ai/pub/
│   │   ├── AiPubApplication.java             启动类（@EnableRabbit）
│   │   ├── common/                           通用工具
│   │   │   ├── JwtUtil.java                  JWT 签发/校验
│   │   │   ├── Result.java                   统一响应体
│   │   │   └── WechatUtil.java               签名校验 + XML 解析
│   │   ├── config/                           配置类
│   │   │   ├── SecurityConfig.java           过滤器链 + 401/403 处理器
│   │   │   ├── AiConfig.java                 AI 客户端（含超时配置）
│   │   │   ├── RabbitMQConfig.java           队列 / 死信 / 手动 ACK
│   │   │   ├── RedisConfig.java              序列化配置
│   │   │   ├── JacksonConfig.java            日期格式（收发一致）
│   │   │   ├── WebSocketConfig.java          STOMP 端点 /ws
│   │   │   ├── WebMvcConfig.java             静态资源映射 + CORS
│   │   │   └── DataInitializer.java          启动自检 + 管理员补建
│   │   ├── entity/                           6 个 JPA 实体
│   │   ├── repository/                       数据访问层
│   │   ├── service/                          11 个业务服务
│   │   │   ├── ChatService.java              ★ 四层算法 + 滑动窗口
│   │   │   ├── WechatReplyService.java       关键词规则匹配（7 种模式）
│   │   │   ├── WechatApiService.java         微信 API（token 三层缓存）
│   │   │   └── StatisticsService.java        统计聚合
│   │   ├── controller/                       10 个 REST 控制器
│   │   ├── consumer/MessageConsumer.java     MQ 消费者（三段式决策）
│   │   ├── dto/                              分页 / 登录 / 统计 DTO
│   │   ├── exception/                        全局异常处理
│   │   └── util/AesUtil.java                 AES-256-GCM 加密工具
│   ├── src/test/java/                        19 个测试类 / 95 个测试
│   ├── src/test/jmeter/                      JMeter 压测计划
│   ├── src/experiment/java/                  论文实验代码
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                                 # Vue 3 前端
│   ├── src/
│   │   ├── api/
│   │   │   ├── index.js                      24 个接口封装
│   │   │   └── request.js                    Axios 拦截器（token / 401 处理）
│   │   ├── router/index.js                   路由表 + 权限守卫
│   │   ├── store/user.js                     Pinia（token / 用户 / 当前公众号）
│   │   ├── utils/date.js                     日期格式化
│   │   └── views/                            10 个页面
│   │       ├── Login.vue                     登录
│   │       ├── Layout.vue                    主框架（菜单 + 公众号选择器）
│   │       ├── Dashboard.vue                 数据统计（ECharts）
│   │       ├── AccountList.vue / AccountEdit.vue
│   │       ├── MenuManage.vue                菜单管理
│   │       ├── MaterialManage.vue            素材管理
│   │       ├── ReplyRule.vue                 自动回复规则
│   │       ├── MessageList.vue               消息记录（WebSocket）
│   │       └── UserManage.vue                用户管理（仅 ADMIN）
│   ├── package.json
│   ├── vite.config.js                        开发代理配置
│   ├── nginx.conf                            生产反代配置
│   └── Dockerfile
│
└── deploy/                                   # 部署配置
    ├── docker-compose.yml                    8 服务编排
    ├── schema.sql                            建表 + 种子数据
    ├── .env.example                          环境变量模板
    ├── prometheus.yml                        指标采集
    ├── grafana-datasource.yml                Grafana 数据源
    ├── grafana-dashboards/                   Grafana 面板（8 个）
    └── start-ai-pub.bat                      Windows 一键启动
```

---

## 测试

```bash
cd backend

# 常规测试（95 个）
mvn test

# 论文实验（4 个，结果输出到 target/experiment/*.csv）
mvn test -Pexperiment
```

**测试分层**：

| 层次 | 技术 | 覆盖 |
|---|---|---|
| 纯单元 | Mockito（不启动 Spring） | 算法、加密、JWT、规则匹配 |
| 集成 | `@SpringBootTest` + MockMvc + H2 | 控制器全链路、统计聚合 |

测试通过 `TestAutoConfig` 替换 Redis / RabbitMQ 连接，**不依赖真实中间件**，可直接在 CI 运行。

**测试类清单**（18 个测试类，共 95 个测试）：

| 测试类 | 数量 | 覆盖点 |
|---|---|---|
| `JwtUtilTest` | 10 | 签发/校验/过期/篡改/换密钥/畸形 token |
| `AesUtilTest` | 9 | 加解密往返、跨密钥不可解、幂等、旧明文兼容 |
| `ChatServiceTest` | 10 | AI 回复、多轮上下文、超时/限流/Redis 故障降级 |
| `ChatServiceIntentTest` | 7 | 意图识别准确率、阈值、降级话术 |
| `WechatApiServiceTest` | 10 | token 缓存/刷新/并发、菜单同步、客服消息 |
| `WechatReplyServiceTest` | 9 | 7 种匹配模式 + 非法正则 + null 处理 |
| `StatisticsServiceTest` | 3 | 聚合数值、跨公众号隔离、空数据 |
| `WechatMenuServiceTest` | 2 | 菜单部分更新语义 |
| `WechatMessageServiceTest` | 2 | `replyType` 落库、字段不被误清空 |
| `AuthServiceTest` | 4 | 登录成功/密码错误/用户不存在/账号禁用 |
| `MessageConsumerTest` | 4 | 幂等跳过、三条决策分支 |
| 控制器测试（7 个类） | 25 | MockMvc 全链路、鉴权、凭证不下发 |

---

## 论文实验

`backend/src/experiment/` 下的实验代码产出 6 个 CSV 到 `target/experiment/`：

| 实验 | 内容 |
|---|---|
| 实验一 | 意图识别阈值敏感性（θ 从 0.05 扫描到 0.35，输出准确率/精确率/召回率/F1） |
| 实验二 | 滑动窗口大小对多轮对话连贯性的影响 |
| 实验三 | 并发响应时间与错误率 |
| 对比实验 | 关键词匹配 vs 单轮 AI vs 本系统完整算法 |

> 实验代码直接调用生产环境的 `ChatService`，不复制算法逻辑，避免"实验中的算法"与"线上算法"漂移导致数据失真。

---

## 性能压测

```bash
# 需先启动后端服务
jmeter -n -t backend/src/test/jmeter/ai-pub-stress-test.jmx -l result.jtl -e -o report/
```

| 场景 | 并发 | 时长 | 端点 |
|---|---|---|---|
| 登录接口 | 50 | 60s | `POST /api/auth/login` |
| 公众号列表 | 100 | 60s | `GET /api/wechat/accounts` |
| 回复规则 | 100 | 60s | `GET /api/wechat/reply-rules` |
| 混合场景 | 200 | 120s | 随机轮转上述接口 |

计划中包含 Setup 线程组自动登录获取 Token，并为每个采样器配置了响应断言（HTTP 200 + 业务码 200）。

---

## 监控

后端通过 Actuator 暴露 `/actuator/prometheus`，Prometheus 每 15 秒采集一次，Grafana 提供 8 个面板：

API QPS · API 平均响应时间 · JVM 堆内存 · 堆内存使用率 · 活跃线程 · GC 暂停 · 服务存活 · 运行时长

---

## 安全说明

| 层面 | 措施 |
|---|---|
| 认证 | JWT（HS256），24 小时过期 |
| 授权 | 路由级 + 方法级 `@PreAuthorize("hasRole('ADMIN')")` |
| 密码 | BCrypt 加盐哈希 |
| 敏感配置 | AppSecret / EncodingAESKey 使用 AES-256-GCM 加密入库 |
| 密钥管理 | `AES_SECRET_KEY` / `JWT_SECRET` 环境变量注入，未配置时启动告警 |
| 接口防护 | 微信回调 SHA1 签名校验；`msgId` 去重防重放 |
| 响应脱敏 | `appSecret` / `encodingAesKey` / `password` 标记为只写，不随接口下发 |

> **注意**：仓库中的默认密钥（`application.yml` 与 `JwtConfig`）仅用于让项目开箱可运行，**生产环境务必通过环境变量覆盖**。

---

## 已知限制

1. **意图识别语料规模较小** —— 5 类意图 × 10–19 个手工构造的关键词，扩展到更多意图需要引入词向量或微调
2. **情感分析基于词典** —— 无法处理反讽与多重否定（如"不是不好"会被判为负面）
3. **不支持指代消解** —— 每轮独立识别意图，无法理解"那退款呢"中的省略
4. **统计部分指标为全局口径** —— `accountCount` / `messageCount` 等不区分公众号，前端未做区分标注
5. **无接口限流** —— AI 接口若被高频调用会持续消耗 token 成本
6. **菜单拖拽排序未实现** —— 当前通过数值排序

---

## 许可证

MIT License
