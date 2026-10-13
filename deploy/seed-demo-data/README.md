# 演示数据

本目录提供一套**与毕业论文内容对应**的演示数据，用于填充空数据库，方便功能演示与答辩截图。

## 文件说明

| 文件 | 说明 |
|---|---|
| `seed-demo.sql` | 可直接执行的 SQL 脚本（含清空 + 插入） |
| `generate_seed.py` | 生成上述 SQL 的 Python 脚本（随机种子固定，可重复生成） |

## 数据内容

数据依据论文《基于 AI 大模型的多公众号智能运营与管理系统》设计：

| 表 | 条数 | 论文依据 |
|---|---|---|
| `sys_user` | 3 | 3.1.4 表 3.2：ADMIN / OPERATOR 两种角色 |
| `wechat_account` | 3 | 4.2.6 领域场景实验：电商 / 教育 / 餐饮 |
| `wechat_reply_rule` | 28 | 3.3.1：覆盖全部 **7 种匹配类型** |
| `wechat_menu` | 19 | 3.5：两级菜单结构（微信限制） |
| `wechat_material` | 15 | image / news / video / voice 四类 |
| `wechat_message` | 320 | 4.1：约 300 条真实咨询消息 |

**消息时间跨度 14 天**，便于 Dashboard 的「近 7 天消息趋势」折线图展示。

**回复类型分布**（对应论文 4.2.5 的三类回复）：

| 回复方式 | 条数 |
|---|---|
| AI | 147 |
| KEYWORD | 128 |
| DEFAULT | 34 |

## 使用方法

### 方式一：导入到 Docker 容器（推荐）

```powershell
cd C:\Users\24657\Documents\trae_projects\bishe\deploy

# 复制脚本进容器
docker cp seed-demo-data/seed-demo.sql ai-pub-mysql:/tmp/seed.sql

# 执行
docker exec ai-pub-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 ai_pub < /tmp/seed.sql"
```

### 方式二：用本机 MySQL 客户端

```bash
mysql -h 127.0.0.1 -P 3307 -uroot -proot --default-character-set=utf8mb4 ai_pub < seed-demo.sql
```

### 重新生成 SQL

修改 `generate_seed.py` 后运行：

```bash
python generate_seed.py
```

## 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123456` | ADMIN（全部权限） |
| `operator01` | `operator123` | OPERATOR（运营-小王） |
| `operator02` | `operator123` | OPERATOR（运营-小李） |

> 用 OPERATOR 登录可验证权限控制：菜单中不会出现「用户管理」，直接访问 `/users` 也会被重定向。

## 三个公众号

| ID | 名称 | 领域 |
|---|---|---|
| 1 | 优选好物商城 | 电商 |
| 2 | 启明教育在线 | 教育 |
| 3 | 味多多餐饮服务 | 餐饮 |

顶栏切换公众号可验证「数据按公众号隔离」——不同账号的消息、规则、素材、菜单互不干扰。

## 关键词匹配测试

登录后访问 **自动回复** 页面，可测试 7 种匹配模式（账号 1 配置最全）：

| 输入 | 命中的规则 | 匹配类型 |
|---|---|---|
| `帮助` | 会员帮助 | EXACT |
| `查询订单` | 订单查询 | STARTS_WITH |
| `有货吗` | 疑问结尾 | ENDS_WITH |
| `我要退款，质量有问题` | 退款且质量问题 | ALL_CONTAINS |
| `13800138000` | 手机号识别 | REGEX |
| `退换货` | 退换模糊匹配 | FUZZY |
| `你好` | 问候语 | CONTAINS |

## 注意事项

1. **脚本会先 TRUNCATE 业务表**（消息/规则/素材/菜单/公众号），`sys_user` 使用 `ON DUPLICATE KEY UPDATE` 保留原有账号。
2. **AppSecret 以明文写入**：`AesUtil.decrypt` 对无 `enc:v1:` 前缀的历史明文会原样返回，因此可正常读取；后续在后台编辑保存时会自动加密。
3. 素材的 `url` 是**占位路径**（`/uploads/demo/...`），实际文件不存在，图片预览会显示裂图——这不影响列表、分类、统计等功能演示。若需真实图片，可上传本地文件替换。
4. 消息记录中的 `msg_id` 为模拟值，不与真实微信消息冲突。
