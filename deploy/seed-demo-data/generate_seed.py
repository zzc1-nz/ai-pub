# -*- coding: utf-8 -*-
"""
根据周子城《基于 AI 大模型的多公众号智能运营与管理系统》毕业论文生成演示数据。

论文依据：
- 3.1.4 数据库设计：users / wechat_accounts / messages / auto_reply_rules / 素材表 / 菜单表
- 3.3.1 意图类别：{问候, 投诉, 退换, 咨询, 感谢, 默认} 六类
- 3.3.1 匹配类型：精确/包含/前缀/后缀/模糊/正则/全词（7 种）
- 4.1 数据集：某公众号 200 条真实咨询 + 150 单轮 + 25 多轮场景，共约 300 条
- 4.2.6 领域场景：电商 / 教育 / 餐饮 三个领域各 30 条
- 4.2.5 对比实验：关键词匹配 42.5% / 单轮 AI 73.8% / 本系统 85.0%，
          无法回复率 68.0% / 15.0% / 9.2%
- 3.3.3 Token 预算：T_max=2000, T_system=300, T_query=200, T_history=1500
- 3.3.4 回复最大长度 600 字符
"""
import os
import random
from datetime import datetime, timedelta

random.seed(20260613)  # 固定随机种子，保证可重复

# 输出到脚本所在目录，便于随项目一起使用
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "seed-demo.sql")

L = []
def w(s=""):
    L.append(s)

w("-- ============================================================================")
w("--  AI-Pub 演示数据")
w("--  依据：周子城《基于 AI 大模型的多公众号智能运营与管理系统》毕业论文")
w("--  生成脚本：seed.py（随机种子固定，可重复生成）")
w("-- ============================================================================")
w("SET NAMES utf8mb4;")
w("USE ai_pub;")
w("")
w("-- 清空业务数据（保留 sys_user 中的 admin，避免影响登录）")
w("SET FOREIGN_KEY_CHECKS = 0;")
for t in ("wechat_message", "wechat_reply_rule", "wechat_material", "wechat_menu", "wechat_account"):
    w(f"TRUNCATE TABLE {t};")
w("SET FOREIGN_KEY_CHECKS = 1;")
w("")

# ============================================================================
# 1. 系统用户（论文 3.1.4 表 3.2：role = ADMIN / OPERATOR）
# ============================================================================
w("-- ---------------------------------------------------------------------------")
w("-- 1. 系统用户（论文 3.1.4：ADMIN / OPERATOR 两种角色）")
w("-- ---------------------------------------------------------------------------")
# admin 密码 admin123456 的 BCrypt 哈希（此前已实际生成并验证）
ADMIN_HASH = "$2a$10$sJMqPEShwo175smqeOW87ubKEzxdUDGYIH5sJ6..L8YZyhqAR4v9K"
# operator 密码 operator123 的 BCrypt 哈希（实际生成并验证通过）
OP_HASH = "$2a$10$VWfrhUqu2kBFW897LRHqZONozAi8Q9ft49wSQHIGax8aZuVqrFaWS"
w("INSERT INTO sys_user (username, password, nickname, role, status, created_at, updated_at)")
w(f"VALUES ('admin', '{ADMIN_HASH}', '系统管理员', 'ADMIN', 1, NOW(), NOW())")
w("ON DUPLICATE KEY UPDATE nickname='系统管理员', role='ADMIN', status=1;")
w("INSERT INTO sys_user (username, password, nickname, role, status, created_at, updated_at)")
w(f"VALUES ('operator01', '{OP_HASH}', '运营-小王', 'OPERATOR', 1, NOW(), NOW())")
w("ON DUPLICATE KEY UPDATE nickname='运营-小王', role='OPERATOR', status=1;")
w("INSERT INTO sys_user (username, password, nickname, role, status, created_at, updated_at)")
w(f"VALUES ('operator02', '{OP_HASH}', '运营-小李', 'OPERATOR', 1, NOW(), NOW())")
w("ON DUPLICATE KEY UPDATE nickname='运营-小李', role='OPERATOR', status=1;")
w("")

# ============================================================================
# 2. 公众号（论文 4.2.6 三个领域：电商 / 教育 / 餐饮）
# ============================================================================
ACCOUNTS = [
    (1, "优选好物商城", "wx_demo_ecommerce_001", "a1b2c3d4e5f60718293a4b5c6d7e8f90",
     "ecommerce_token_2026", "ai_enabled=1"),
    (2, "启明教育在线", "wx_demo_education_002", "b2c3d4e5f60718293a4b5c6d7e8f90a1",
     "education_token_2026", "ai_enabled=1"),
    (3, "味多多餐饮服务", "wx_demo_catering_003", "c3d4e5f60718293a4b5c6d7e8f90a1b2",
     "catering_token_2026", "ai_enabled=1"),
]

w("-- ---------------------------------------------------------------------------")
w("-- 2. 公众号配置（论文 4.2.6 领域场景实验：电商 / 教育 / 餐饮）")
w("--    注：app_secret 以明文写入，AesUtil.decrypt 对无 enc:v1: 前缀的历史")
w("--        明文会原样返回，因此可正常读取；后续在后台编辑保存时会自动加密。")
w("-- ---------------------------------------------------------------------------")
for aid, name, appid, secret, token, _ in ACCOUNTS:
    w("INSERT INTO wechat_account (id, name, app_id, app_secret, token, encoding_aes_key,")
    w("    access_token, access_token_expires_at, ai_enabled, default_reply, status, created_at, updated_at)")
    w(f"VALUES ({aid}, '{name}', '{appid}', '{secret}', '{token}', NULL,")
    w(f"    NULL, NULL, b'1', '正在为您转接人工客服，请稍候…', 1,")
    w(f"    DATE_SUB(NOW(), INTERVAL 60 DAY), NOW());")
w("")

# ============================================================================
# 3. 自动回复规则（论文 3.3.1 七种匹配类型 + 意图语料库）
# ============================================================================
# (account_id, name, keyword, match_type, reply_type, reply_content, sort_order)
RULES = [
    # ---- 电商号：覆盖全部 7 种匹配类型 ----
    (1, "问候语", "你好,您好,hi,hello,在吗", "CONTAINS", "TEXT",
     "您好！欢迎光临优选好物商城～\n回复【优惠】查看本月活动，回复【订单】查询物流。", 1),
    (1, "价格咨询", "价格,多少钱,怎么卖,报价", "CONTAINS", "TEXT",
     "商品价格以详情页标价为准。\n本月满 199 减 30，满 299 减 50，可与优惠券叠加使用。", 2),
    (1, "物流查询", "发货,快递,物流,几天到", "CONTAINS", "TEXT",
     "订单付款后 24 小时内发货，默认中通/圆通。\n一般 2-3 天送达，偏远地区 4-5 天。", 3),
    (1, "退换货政策", "退款,退货,换货,售后", "CONTAINS", "TEXT",
     "支持 7 天无理由退换（不影响二次销售）。\n质量问题由我们承担运费，请提供订单号与照片。", 4),
    (1, "退款且质量问题", "退款,质量", "ALL_CONTAINS", "TEXT",
     "非常抱歉给您带来不好的体验！质量问题可全额退款并承担来回运费。\n请发送订单号 + 问题照片，我们 2 小时内处理。", 5),
    (1, "会员帮助", "帮助", "EXACT", "TEXT",
     "【帮助中心】\n1. 订单查询  2. 退换货  3. 优惠活动  4. 联系人工\n请回复对应数字。", 6),
    (1, "订单查询", "查询,订单", "STARTS_WITH", "TEXT",
     "请发送您的订单号（如 DD20261013001），我马上帮您查询。", 7),
    (1, "疑问结尾", "吗", "ENDS_WITH", "TEXT",
     "您的问题我们已收到，客服稍后会详细回复您～", 8),
    (1, "手机号识别", "1[3-9]\\d{9}", "REGEX", "TEXT",
     "已识别到您的手机号，稍后客服会电话联系您确认订单信息。", 9),
    (1, "退换模糊匹配", "退换货", "FUZZY", "TEXT",
     "您好，关于退换货：支持 7 天无理由，质量问题免费退换。\n请提供订单号，我们尽快为您处理。", 10),
    (1, "投诉处理", "投诉,差评,垃圾,骗人,举报,维权,不满", "CONTAINS", "TEXT",
     "非常抱歉给您带来不好的体验！我们已记录您的问题，\n客服主管将在 30 分钟内与您联系。", 11),
    (1, "感谢回复", "谢谢,感谢,多谢,好评", "CONTAINS", "TEXT",
     "感谢您的支持！期待再次为您服务～", 12),

    # ---- 教育号 ----
    (2, "问候语", "你好,您好,hi,hello", "CONTAINS", "TEXT",
     "您好！这里是启明教育在线～\n回复【课程】查看在售课程，回复【试听】预约免费试听。", 1),
    (2, "课程咨询", "课程,班型,课时,怎么上课", "CONTAINS", "TEXT",
     "我们目前开设：\n· 少儿编程（6-12 岁）\n· 初高中数理提升\n· 成人职业技能\n回复课程名可查看详情。", 2),
    (2, "试听预约", "试听,体验课,免费", "CONTAINS", "TEXT",
     "免费试听预约成功！\n请留下「学员姓名 + 年级 + 联系电话」，老师会在 24 小时内联系您。", 3),
    (2, "收费退费", "学费,收费,退费,多少钱", "CONTAINS", "TEXT",
     "各课程收费标准不同，支持分期。\n开课前 7 天可全额退费，开课后按课时结算。", 4),
    (2, "上课时间", "上课时间,几点,排课", "CONTAINS", "TEXT",
     "工作日 18:00-21:00，周末 9:00-18:00，可自由约课。", 5),
    (2, "投诉处理", "投诉,差评,不满,退费难", "CONTAINS", "TEXT",
     "非常抱歉！请留下您的联系方式，教务主管会第一时间与您沟通解决。", 6),
    (2, "帮助菜单", "帮助", "EXACT", "TEXT",
     "【启明教育】\n1. 课程介绍  2. 预约试听  3. 收费说明  4. 转人工", 7),
    (2, "感谢回复", "谢谢,感谢,好评", "CONTAINS", "TEXT",
     "感谢您的认可！祝学习进步～", 8),

    # ---- 餐饮号 ----
    (3, "问候语", "你好,您好,hi,hello", "CONTAINS", "TEXT",
     "您好！欢迎光临味多多餐饮～\n回复【菜单】查看今日菜品，回复【订座】在线预约。", 1),
    (3, "营业时间", "营业时间,几点,开门,关门", "CONTAINS", "TEXT",
     "营业时间：每日 10:30-14:00，17:00-21:30，全年无休。", 2),
    (3, "订座", "订座,预订,包间,几位", "CONTAINS", "TEXT",
     "在线订座请提供「日期 + 时间 + 人数 + 联系电话」，我们会为您预留座位。", 3),
    (3, "外卖配送", "外卖,配送,送餐,起送", "CONTAINS", "TEXT",
     "3 公里内满 30 元起送，配送费 5 元；满 88 元免配送费。", 4),
    (3, "地址电话", "地址,在哪,电话,位置", "CONTAINS", "TEXT",
     "地址：哈尔滨市南岗区XX路 88 号（地铁 1 号线XX站 B 口）\n电话：0451-8888XXXX", 5),
    (3, "优惠活动", "优惠,活动,打折,团购", "CONTAINS", "TEXT",
     "本周活动：\n· 午市套餐 8 折\n· 充值 500 送 80\n· 三人同行一人半价", 6),
    (3, "投诉处理", "投诉,差评,不满意,难吃", "CONTAINS", "TEXT",
     "非常抱歉让您有不愉快的用餐体验！请告知具体问题，店长会亲自跟进处理。", 7),
    (3, "感谢回复", "谢谢,感谢,好吃,好评", "CONTAINS", "TEXT",
     "感谢您的光临与支持！期待您再次惠顾～", 8),
]

w("-- ---------------------------------------------------------------------------")
w("-- 3. 自动回复规则（论文 3.3.1：包含/精确/前缀/后缀/全词/模糊/正则 七种匹配）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO wechat_reply_rule (account_id, name, keyword, match_type, reply_type,")
w("    reply_content, sort_order, status, created_at, updated_at) VALUES")
vals = []
for aid, name, kw, mt, rt, rc, so in RULES:
    k = kw.replace("\\", "\\\\").replace("'", "''")
    c = rc.replace("\\", "\\\\").replace("'", "''").replace("\n", "\\n")
    vals.append(f"({aid}, '{name}', '{k}', '{mt}', '{rt}', '{c}', {so}, 1, "
                f"DATE_SUB(NOW(), INTERVAL 50 DAY), NOW())")
w(",\n".join(vals) + ";")
w("")

# ============================================================================
# 4. 自定义菜单（微信限制：最多两级）
# ============================================================================
MENUS = [
    # 电商
    (1, 0, "🛍 逛商城", "view", None, "https://example.com/mall", 1),
    (1, 0, "📦 我的订单", "click", "MY_ORDER", None, 2),
    (1, 0, "🎁 优惠活动", "click", "PROMOTION", None, 3),
    (1, 1, "新品上架", "view", None, "https://example.com/new", 1),
    (1, 1, "热销榜单", "view", None, "https://example.com/hot", 2),
    (1, 2, "订单查询", "click", "QUERY_ORDER", None, 1),
    (1, 2, "物流跟踪", "click", "QUERY_LOGISTICS", None, 2),
    (1, 2, "售后申请", "click", "AFTER_SALE", None, 3),
    # 教育
    (2, 0, "📚 课程中心", "view", None, "https://example.com/courses", 1),
    (2, 0, "✏️ 预约试听", "click", "BOOK_TRIAL", None, 2),
    (2, 0, "📞 联系老师", "click", "CONTACT_TEACHER", None, 3),
    (2, 1, "少儿编程", "view", None, "https://example.com/kids", 1),
    (2, 1, "初高中提升", "view", None, "https://example.com/k12", 2),
    (2, 1, "成人技能", "view", None, "https://example.com/adult", 3),
    # 餐饮
    (3, 0, "🍽 今日菜单", "view", None, "https://example.com/menu", 1),
    (3, 0, "🪑 在线订座", "click", "BOOK_TABLE", None, 2),
    (3, 0, "🛵 外卖点单", "view", None, "https://example.com/delivery", 3),
    (3, 2, "查看包间", "click", "VIEW_ROOM", None, 1),
    (3, 2, "取消订座", "click", "CANCEL_BOOK", None, 2),
]

w("-- ---------------------------------------------------------------------------")
w("-- 4. 自定义菜单（微信仅支持两级菜单）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO wechat_menu (account_id, parent_id, name, type, `key`, url, sort_order, created_at, updated_at) VALUES")
vals = []
for aid, pid, name, typ, key, url, so in MENUS:
    k = f"'{key}'" if key else "NULL"
    u = f"'{url}'" if url else "NULL"
    vals.append(f"({aid}, {pid}, '{name}', '{typ}', {k}, {u}, {so}, "
                f"DATE_SUB(NOW(), INTERVAL 45 DAY), NOW())")
w(",\n".join(vals) + ";")
w("")

# 建立 parent_id 关联（一级菜单自增 id 依次为 1..N）
w("-- 关联二级菜单到对应一级菜单（按上面 INSERT 的自增 id 顺序回填）")
w("UPDATE wechat_menu SET parent_id = (SELECT id FROM (SELECT id FROM wechat_menu WHERE account_id=1 AND name='🛍 逛商城') x) WHERE account_id=1 AND name IN ('新品上架','热销榜单');")
w("UPDATE wechat_menu SET parent_id = (SELECT id FROM (SELECT id FROM wechat_menu WHERE account_id=1 AND name='📦 我的订单') x) WHERE account_id=1 AND name IN ('订单查询','物流跟踪','售后申请');")
w("UPDATE wechat_menu SET parent_id = (SELECT id FROM (SELECT id FROM wechat_menu WHERE account_id=2 AND name='📚 课程中心') x) WHERE account_id=2 AND name IN ('少儿编程','初高中提升','成人技能');")
w("UPDATE wechat_menu SET parent_id = (SELECT id FROM (SELECT id FROM wechat_menu WHERE account_id=3 AND name='🪑 在线订座') x) WHERE account_id=3 AND name IN ('查看包间','取消订座');")
w("")

# ============================================================================
# 5. 素材
# ============================================================================
MATERIALS = [
    (1, "image", "商城首页轮播图-春季大促", "/uploads/demo/banner-spring.jpg", "春季大促主视觉"),
    (1, "image", "商品主图-无线蓝牙耳机", "/uploads/demo/product-earphone.jpg", "热销商品主图"),
    (1, "image", "商品主图-智能保温杯", "/uploads/demo/product-cup.jpg", "热销商品主图"),
    (1, "news", "2026 春季新品发布会图文", "/uploads/demo/news-spring.jpg",
     "春季新品发布", "本周上新 32 款商品，涵盖数码、家居、服饰三大类目，全场 8 折起。"),
    (1, "news", "会员权益升级公告", "/uploads/demo/news-vip.jpg",
     "会员权益升级", "自 4 月起，会员积分可抵扣现金，100 积分 = 1 元，上不封顶。"),
    (1, "video", "商品使用教程视频", "/uploads/demo/video-tutorial.mp4", "3 分钟教你用好智能保温杯"),
    (1, "voice", "客服欢迎语音", "/uploads/demo/voice-welcome.mp3", "欢迎致电优选好物商城"),
    (2, "image", "课程宣传图-少儿编程", "/uploads/demo/course-kids.jpg", "少儿编程课程宣传"),
    (2, "image", "师资介绍图", "/uploads/demo/teacher.jpg", "名师团队介绍"),
    (2, "news", "2026 春季招生简章", "/uploads/demo/news-enroll.jpg",
     "春季招生简章", "春季班开始招生，少儿编程 / 初高中提升 / 成人技能三大方向，早鸟价 8.5 折。"),
    (2, "video", "免费试听课片段", "/uploads/demo/video-trial.mp4", "少儿编程试听课 15 分钟"),
    (3, "image", "招牌菜品图-红烧肉", "/uploads/demo/dish-hongshaorou.jpg", "招牌红烧肉"),
    (3, "image", "门店环境图", "/uploads/demo/store.jpg", "门店用餐环境"),
    (3, "news", "春季新菜上市", "/uploads/demo/news-newdish.jpg",
     "春季新菜上市", "新增 8 道时令菜品，春笋、香椿、荠菜系列，欢迎品尝。"),
    (3, "voice", "订座确认语音", "/uploads/demo/voice-booking.mp3", "订座成功提示音"),
]

w("-- ---------------------------------------------------------------------------")
w("-- 5. 素材（图片 / 图文 / 视频 / 语音）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO wechat_material (account_id, type, title, url, description, author, digest, show_cover_pic, created_at, updated_at) VALUES")
vals = []
for item in MATERIALS:
    aid, typ, title, url, desc = item[0], item[1], item[2], item[3], item[4]
    d = desc.replace("'", "''")
    t = title.replace("'", "''")
    vals.append(f"({aid}, '{typ}', '{t}', '{url}', '{d}', '运营部', '{d[:60]}', 1, "
                f"DATE_SUB(NOW(), INTERVAL {random.randint(5, 55)} DAY), NOW())")
w(",\n".join(vals) + ";")
w("")

# ============================================================================
# 6. 消息记录（论文 4.1 数据集：约 300 条；4.2.5 回复类型分布）
# ============================================================================
# 论文 3.3.1 六类意图的典型用户消息
CORPUS = {
    "问候": ["你好，请问有什么活动吗", "您好，在吗", "hi，有人吗", "早上好", "嗨，我想问一下",
             "你好呀", "在不在", "有人吗", "您好，想咨询一下", "哈喽"],
    "投诉": ["你们这个太差了，我要投诉", "垃圾产品，非常不满", "服务态度太差了，差评",
             "骗人的，要举报你们", "太坑了，我要维权", "什么破质量，太失望了",
             "这服务真的让人愤怒", "我要曝光你们", "太差劲了", "完全不满意"],
    "退换": ["我要退款，东西坏了", "想退货，质量有问题", "可以换货吗，发错了",
             "七天无理由退货怎么操作", "售后在哪里，要退换", "收到货就破损了，要退",
             "少发了一件，怎么办", "这个能退吗", "我要申请售后", "发错货了怎么处理"],
    "咨询": ["这个多少钱", "怎么买，在哪里下单", "你们的地址和电话是什么",
             "有优惠活动吗，怎么买", "营业时间是几点到几点", "包邮吗",
             "有货吗，多久能发", "支持哪些支付方式", "能开发票吗", "库存还有多少"],
    "感谢": ["谢谢你的帮助", "太感谢了，服务很好", "非常满意，点赞",
             "你们的服务很棒", "辛苦了，好评", "多谢解答", "很贴心，谢谢",
             "解决得很快，满意", "给客服点赞", "太好了，感谢"],
    "默认": ["今天天气不错", "随便说点什么", "嗯嗯", "哈哈哈哈哈", "哦",
             "？？？", "在吗在吗", "。。。", "测试一下", "asdfgh"],
}

# 意图 → 回复类型与回复内容（模拟真实回复）
REPLIES = {
    "问候": ("KEYWORD", "您好！欢迎光临～回复【优惠】查看本月活动，回复【订单】查询物流。"),
    "投诉": ("AI", "非常抱歉给您带来这么差的体验，这确实是我们的问题。\n"
                   "我已经记录下您的反馈，并为您优先安排客服主管跟进，会在 30 分钟内联系您。\n"
                   "请问方便提供一下订单号吗？我先帮您核实具体情况。"),
    "退换": ("KEYWORD", "支持 7 天无理由退换（不影响二次销售）。\n质量问题由我们承担运费，请提供订单号与照片。"),
    "咨询": ("AI", "您好，关于您咨询的问题：\n"
                   "· 价格以商品详情页标价为准，本月满 199 减 30\n"
                   "· 付款后 24 小时内发货，一般 2-3 天送达\n"
                   "如需更详细信息，可以回复具体商品名称，我帮您查询～"),
    "感谢": ("KEYWORD", "感谢您的支持！期待再次为您服务～"),
    "默认": ("DEFAULT", "正在为您转接人工客服，请稍候…"),
}

OPENIDS = [f"oDemoUser{i:03d}XyZ" for i in range(1, 25)]

w("-- ---------------------------------------------------------------------------")
w("-- 6. 消息记录（论文 4.1：约 300 条；4.2.5：AI/关键词/默认三类回复）")
w("--    时间跨度 14 天，便于 Dashboard 的「近 7 天消息趋势」图表展示")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO wechat_message (account_id, msg_id, to_user_name, openid, msg_type,")
w("    content, reply_type, reply_content, created_at) VALUES")

rows = []
mid = 1000000000
now = datetime(2026, 10, 13, 20, 30, 0)

# 按论文的回复类型分布：关键词 ~42%、AI ~49%、默认 ~9%
plan = [("KEYWORD", 128), ("AI", 147), ("DEFAULT", 27)]  # 共 302 条
for rtype, cnt in plan:
    if rtype == "KEYWORD":
        intents = ["问候", "退换", "感谢"]
    elif rtype == "AI":
        intents = ["投诉", "咨询"]
    else:
        intents = ["默认"]

    for _ in range(cnt):
        intent = random.choice(intents)
        content = random.choice(CORPUS[intent])
        rt, rc = REPLIES[intent]
        # 若与计划不符则按计划修正（保证三类分布稳定）
        rt = rtype
        if rtype == "DEFAULT":
            rc = REPLIES["默认"][1]

        aid = random.choice([1, 1, 1, 2, 2, 3])          # 电商号消息更多
        openid = random.choice(OPENIDS)
        # 时间分布：越近的日期消息越多
        days_ago = random.choices(range(0, 14), weights=[14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1])[0]
        ts = now - timedelta(days=days_ago,
                             hours=random.randint(0, 12),
                             minutes=random.randint(0, 59))
        mid += random.randint(1, 9)
        c = content.replace("'", "''")
        r = rc.replace("'", "''").replace("\n", "\\n")
        rows.append(f"({aid}, '{mid}', 'gh_demo_{aid:03d}', '{openid}', 'text', "
                    f"'{c}', '{rt}', '{r}', '{ts.strftime('%Y-%m-%d %H:%M:%S')}')")

# 补充少量非文本消息（图片 / 事件），丰富消息类型分布
for _ in range(18):
    aid = random.choice([1, 2, 3])
    openid = random.choice(OPENIDS)
    days_ago = random.randint(0, 13)
    ts = now - timedelta(days=days_ago, hours=random.randint(0, 12))
    mid += 7
    if random.random() < 0.6:
        rows.append(f"({aid}, '{mid}', 'gh_demo_{aid:03d}', '{openid}', 'image', "
                    f"'[图片]', NULL, NULL, '{ts.strftime('%Y-%m-%d %H:%M:%S')}')")
    else:
        rows.append(f"({aid}, '{mid}', 'gh_demo_{aid:03d}', '{openid}', 'event', "
                    f"'subscribe', 'DEFAULT', '感谢关注！回复【帮助】查看功能菜单。', "
                    f"'{ts.strftime('%Y-%m-%d %H:%M:%S')}')")

random.shuffle(rows)
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 数据统计")
w("-- ---------------------------------------------------------------------------")
w("SELECT 'sys_user' AS 表名, COUNT(*) AS 记录数 FROM sys_user")
w("UNION ALL SELECT 'wechat_account', COUNT(*) FROM wechat_account")
w("UNION ALL SELECT 'wechat_menu', COUNT(*) FROM wechat_menu")
w("UNION ALL SELECT 'wechat_material', COUNT(*) FROM wechat_material")
w("UNION ALL SELECT 'wechat_reply_rule', COUNT(*) FROM wechat_reply_rule")
w("UNION ALL SELECT 'wechat_message', COUNT(*) FROM wechat_message;")
w("")
w("SELECT reply_type AS 回复方式, COUNT(*) AS 条数")
w("FROM wechat_message WHERE reply_type IS NOT NULL GROUP BY reply_type;")
w("")
w("SELECT msg_type AS 消息类型, COUNT(*) AS 条数")
w("FROM wechat_message GROUP BY msg_type;")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L) + "\n")

print(f"已生成: {OUT}")
print(f"SQL 行数: {len(L)}")
print(f"规则数: {len(RULES)}")
print(f"菜单数: {len(MENUS)}")
print(f"素材数: {len(MATERIALS)}")
print(f"消息数: {len(rows)}")
