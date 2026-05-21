# 智能公众号客服系统 - 技术实现文档

## 1. 项目概述

本项目是一个基于深度学习技术的智能公众号客服系统，采用前后端分离架构设计。系统后端基于Spring Boot框架构建，前端采用Vue 3框架，通过集成深度学习大语言模型（DeepSeek）实现智能对话功能。系统支持多公众号管理、关键词自动回复、AI智能回复、素材管理、自定义菜单配置以及数据统计分析等功能。

### 1.1. 项目背景

传统的公众号客服系统主要依赖人工客服，存在响应速度慢、工作时间有限、人工成本高等问题。本系统通过引入深度学习技术，实现了7×24小时不间断的智能客服服务，能够自动识别用户意图并给出准确回复。同时，系统保留了关键词匹配作为补充，确保在特定场景下能够提供精准的预设回复。

### 1.2. 技术亮点

1. **AI与规则混合模式**：优先使用关键词匹配，匹配失败时调用AI回复
2. **多轮对话支持**：通过Redis存储对话上下文，支持连续对话
3. **异步消息处理**：使用RabbitMQ实现消息异步处理，提升系统响应速度
4. **Docker容器化部署**：一键启动，环境隔离，易于迁移

---

## 2. 技术架构

### 2.1. 整体架构

系统采用四层架构设计：

| 层级 | 职责 | 技术实现 |
|------|------|----------|
| 接入层 | 处理用户请求和微信回调 | Nginx、微信API |
| 应用层 | 前端展示和后端业务逻辑 | Vue 3、Spring Boot |
| 服务层 | 核心业务逻辑和外部服务集成 | Spring Services、DeepSeek API |
| 数据层 | 数据持久化和缓存 | MySQL、Redis、RabbitMQ |

### 2.2. 核心流程图

```
用户消息 → 微信服务器 → 签名验证 → 消息解析 → 关键词匹配 → AI回复 → 消息存储 → 回复用户
```

---

## 3. 后端技术实现

### 3.1. 目录结构

```
backend/src/main/java/com/ai/pub/
├── AiPubApplication.java      # 启动类
├── common/                    # 工具类
│   ├── JwtUtil.java          # JWT工具
│   ├── Result.java           # 统一响应封装
│   └── WechatUtil.java       # 微信工具
├── config/                   # 配置类
│   ├── SecurityConfig.java   # 安全配置
│   ├── RedisConfig.java      # Redis配置
│   ├── JwtConfig.java        # JWT配置
│   └── WechatConfig.java     # 微信配置
├── controller/               # REST控制器
│   ├── AuthController.java   # 认证接口
│   ├── ChatController.java   # 聊天接口
│   ├── WechatCallbackController.java  # 微信回调
│   ├── WechatAccountController.java   # 公众号管理
│   ├── WechatReplyRuleController.java # 回复规则
│   ├── WechatMaterialController.java  # 素材管理
│   ├── WechatMenuController.java      # 菜单管理
│   └── StatisticsController.java      # 数据统计
├── service/                  # 业务服务
│   ├── AuthService.java      # 认证服务
│   ├── ChatService.java      # AI聊天服务
│   ├── WechatReplyService.java       # 回复处理服务
│   ├── WechatAccountService.java     # 公众号服务
│   ├── WechatReplyRuleService.java   # 规则服务
│   ├── WechatMaterialService.java    # 素材服务
│   ├── WechatMenuService.java        # 菜单服务
│   └── StatisticsService.java        # 统计服务
├── repository/               # 数据访问层
└── entity/                   # 实体类
```

### 3.2. 核心技术实现

#### 3.2.1. JWT认证机制

```java
// Token生成逻辑
public String generateToken(String username, String role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", role);
    return Jwts.builder()
            .setClaims(claims)
            .setSubject(username)
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(SignatureAlgorithm.HS512, SECRET)
            .compact();
}
```

#### 3.2.2. 微信消息签名验证

```java
// SHA-1签名验证
public static boolean checkSignature(String signature, String timestamp, 
                                    String nonce, String token) {
    String[] arr = new String[]{token, timestamp, nonce};
    Arrays.sort(arr);
    StringBuilder sb = new StringBuilder();
    for (String s : arr) {
        sb.append(s);
    }
    String expectedSignature = DigestUtils.sha1Hex(sb.toString());
    return expectedSignature.equals(signature);
}
```

#### 3.2.3. AI对话上下文管理

系统使用Redis存储对话上下文，实现多轮对话：

```java
// 获取对话上下文
public ChatContext getContext(String accountId, String openId) {
    String key = "chat:context:" + accountId + ":" + openId;
    String contextJson = redisTemplate.opsForValue().get(key);
    if (contextJson == null) {
        return new ChatContext();
    }
    return objectMapper.readValue(contextJson, ChatContext.class);
}

// 更新对话上下文（过期时间30分钟）
public void updateContext(String accountId, String openId, ChatContext context) {
    String key = "chat:context:" + accountId + ":" + openId;
    String contextJson = objectMapper.writeValueAsString(context);
    redisTemplate.opsForValue().set(key, contextJson, 30, TimeUnit.MINUTES);
}
```

#### 3.2.4. 消息匹配策略

```java
// 消息处理流程
public ReplyResult processMessage(WechatMessage message) {
    // 1. 关键词匹配
    WechatReplyRule rule = replyRuleService.matchRule(message);
    if (rule != null) {
        return ReplyResult.keyword(rule.getReplyContent());
    }
    
    // 2. AI回复（如果启用）
    WechatAccount account = accountService.findById(message.getAccountId());
    if (account.isAiEnabled()) {
        String aiReply = chatService.generateReply(message);
        return ReplyResult.ai(aiReply);
    }
    
    // 3. 默认回复
    return ReplyResult.defaultReply(account.getDefaultReply());
}
```

#### 3.2.5. RabbitMQ异步消息处理

```java
// 消息生产者
@RabbitListener(queues = "wechat_message_queue")
public void processMessage(MessageDTO message) {
    // 异步处理消息
    messageService.save(message);
    replyService.process(message);
}
```

---

## 4. 数据库设计

### 4.1. 核心实体关系

```
SysUser 1 -- * WechatAccount
WechatAccount 1 -- * WechatReplyRule
WechatAccount 1 -- * WechatMessage
WechatAccount 1 -- * WechatMaterial
WechatAccount 1 -- * WechatMenu
WechatMenu 1 -- * WechatMenu (父子关系)
```

### 4.2. 关键表结构

#### wechat_reply_rule（自动回复规则表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键 |
| account_id | BIGINT | 公众号ID |
| name | VARCHAR(100) | 规则名称 |
| keyword | VARCHAR(500) | 关键词（逗号分隔） |
| match_type | VARCHAR(20) | 匹配类型：EXACT/CONTAINS |
| reply_type | VARCHAR(20) | 回复类型：text/image/news |
| reply_content | TEXT | 回复内容 |
| sort_order | INT | 排序优先级 |
| status | TINYINT | 状态：0禁用/1启用 |

#### wechat_message（消息记录表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键 |
| account_id | BIGINT | 公众号ID |
| openid | VARCHAR(100) | 用户OpenID |
| msg_type | VARCHAR(20) | 消息类型 |
| content | TEXT | 消息内容 |
| reply_type | VARCHAR(20) | 回复类型：KEYWORD/AI/DEFAULT |
| reply_content | TEXT | 回复内容 |
| created_at | DATETIME | 创建时间 |

---

## 5. 前端技术实现

### 5.1. 目录结构

```
frontend/src/
├── main.js              # 入口文件
├── App.vue              # 根组件
├── router/index.js      # 路由配置
├── store/user.js        # 用户状态管理
├── api/
│   ├── request.js       # HTTP请求封装
│   └── index.js         # API接口定义
└── views/
    ├── Login.vue        # 登录页
    ├── Layout.vue       # 布局组件
    ├── Dashboard.vue    # 仪表盘
    ├── AccountList.vue  # 公众号列表
    ├── AccountEdit.vue  # 公众号编辑
    ├── ReplyRule.vue    # 回复规则管理
    ├── MaterialManage.vue # 素材管理
    ├── MenuManage.vue   # 菜单管理
    ├── MessageList.vue  # 消息列表
    └── UserManage.vue   # 用户管理
```

### 5.2. 核心实现

#### 5.2.1. 请求拦截器

```javascript
// 添加Token到请求头
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 响应拦截器处理Token过期
axios.interceptors.response.use(response => response, error => {
    if (error.response.status === 401) {
        localStorage.removeItem('token');
        router.push('/login');
    }
    return Promise.reject(error);
});
```

#### 5.2.2. 路由守卫

```javascript
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token');
    if (to.path !== '/login' && !token) {
        next('/login');
    } else {
        next();
    }
});
```

---

## 6. 部署方案

### 6.1. Docker Compose配置

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: ai_pub
      TZ: Asia/Shanghai
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"
  
  redis:
    image: redis:7
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"
  
  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"
  
  backend:
    build: ../backend
    environment:
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
    ports:
      - "8080:8080"
    depends_on:
      - mysql
      - redis
      - rabbitmq
  
  frontend:
    build: ../frontend
    ports:
      - "3000:80"
    depends_on:
      - backend

volumes:
  mysql_data:
  redis_data:
```

### 6.2. 一键启动脚本

```batch
@echo off
echo 正在启动AI公众号客服系统...

:: 检查环境变量
if "%DEEPSEEK_API_KEY%"=="" (
    echo 请先设置环境变量: set DEEPSEEK_API_KEY=your_key
    pause
    exit /b
)

:: 启动容器
docker-compose up -d

:: 等待服务启动
echo 等待服务启动...
timeout /t 15 /nobreak >nul

:: 显示服务状态
echo.
echo 服务已启动！
echo ==============================
echo 前端地址: http://localhost:3000
echo 后端API: http://localhost:8080
echo RabbitMQ: http://localhost:15672
echo ==============================
```

---

## 7. 安全设计

### 7.1. 接口安全

1. **JWT认证**：所有接口需携带有效Token
2. **权限验证**：基于角色的访问控制（RBAC）
3. **参数验证**：使用Bean Validation验证请求参数
4. **SQL注入防护**：使用预编译语句和JPA参数绑定

### 7.2. 数据安全

1. **密码加密**：使用BCrypt加密存储密码
2. **敏感信息保护**：AppSecret等敏感配置加密存储
3. **HTTPS传输**：生产环境启用HTTPS

### 7.3. 日志审计

1. **操作日志**：记录关键业务操作
2. **登录日志**：记录登录时间和IP
3. **错误日志**：记录系统异常

---

## 8. 性能优化

### 8.1. 缓存策略

1. **AccessToken缓存**：Redis缓存微信AccessToken，过期时间7000秒
2. **对话上下文缓存**：Redis缓存，30分钟过期
3. **热点数据缓存**：使用Spring Cache缓存公众号配置

### 8.2. 异步处理

1. **消息异步处理**：RabbitMQ解耦消息生产和消费
2. **统计数据预计算**：定时任务预先计算统计数据

### 8.3. 数据库优化

1. **索引优化**：在常用查询字段建立索引
2. **分页查询**：使用JPA分页避免全表扫描
3. **连接池配置**：合理配置数据库连接池大小

---

## 9. 测试策略

### 9.1. 单元测试

- 使用JUnit 5和Mockito进行单元测试
- 覆盖Service层核心业务逻辑
- 测试用例覆盖正常流程和异常情况

### 9.2. 集成测试

- 使用H2内存数据库进行集成测试
- 测试完整的API调用链路
- 模拟微信回调接口测试

### 9.3. 压力测试

- 使用JMeter进行压力测试
- 测试场景：用户登录、消息处理、AI对话
- 评估系统最大承载能力

---

## 10. 功能清单

| 模块 | 功能 | 状态 |
|------|------|------|
| 用户管理 | 管理员登录、用户CRUD、角色权限 | ✅ |
| 公众号管理 | 多公众号配置、微信验证、Token管理 | ✅ |
| 自动回复规则 | 关键词配置、匹配类型、优先级排序 | ✅ |
| AI对话 | DeepSeek集成、多轮对话、上下文管理 | ✅ |
| 消息处理 | 微信回调、消息存储、消息查询 | ✅ |
| 素材管理 | 图片/语音/视频/图文素材管理 | ✅ |
| 菜单管理 | 自定义菜单配置、同步到微信 | ✅ |
| 数据统计 | 消息趋势、回复类型分布、图表展示 | ✅ |

---

## 11. 扩展建议

1. **知识图谱集成**：构建领域知识库，提升问答准确率
2. **多模型支持**：支持切换不同的AI模型
3. **智能推荐**：基于用户画像推荐个性化内容
4. **移动端适配**：优化移动端体验
5. **实时通知**：WebSocket实时推送消息提醒

---

## 附录

### API接口清单

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/auth/login | POST | 用户登录 |
| /api/wx/callback/{id} | GET/POST | 微信消息回调 |
| /api/accounts | GET/POST | 公众号列表/创建 |
| /api/accounts/{id} | GET/PUT/DELETE | 公众号详情/更新/删除 |
| /api/rules | GET/POST | 回复规则列表/创建 |
| /api/rules/{id} | GET/PUT/DELETE | 规则详情/更新/删除 |
| /api/materials | GET/POST | 素材列表/上传 |
| /api/menus | GET/POST | 菜单列表/创建 |
| /api/messages | GET | 消息列表查询 |
| /api/statistics | GET | 数据统计 |

### 默认账号

- **用户名**: admin
- **密码**: admin123456
