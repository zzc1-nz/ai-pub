# -*- coding: utf-8 -*-
"""
生成演示用素材图片。

对应 deploy/seed-demo-data/seed-demo.sql 中 wechat_material 表的 url 字段：
    /uploads/demo/banner-spring.jpg
    /uploads/demo/product-earphone.jpg
    ...

图片会被写入容器的 /app/uploads/demo/ 目录（APP_UPLOAD_DIR 指向 /app/uploads），
后端 WebMvcConfig 将 /uploads/** 映射到该目录，因此前端 <el-image :src="row.url">
即可正常显示。

风格：扁平化渐变卡片 + 中文标题，尺寸贴合素材用途（轮播图 16:9、商品主图 1:1 等）。
"""
import os
import math
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo-images")

# (文件名, 宽, 高, 主标题, 副标题, 渐变起色, 渐变止色, 角标文字)
SPECS = [
    # ---- 电商号 ----
    ("banner-spring.jpg",      1200, 600, "春季大促",     "全场 8 折起 · 满 199 减 30", (255, 107, 107), (255, 160, 122), "SPRING SALE"),
    ("product-earphone.jpg",    800, 800, "无线蓝牙耳机",  "主动降噪 · 续航 36 小时",     ( 79, 172, 254), ( 67, 206, 162), "HOT"),
    ("product-cup.jpg",         800, 800, "智能保温杯",    "温度显示 · 12 小时保温",     (255, 154, 108), (255, 206, 86),  "NEW"),
    ("news-spring.jpg",         900, 500, "2026 春季新品发布会", "32 款新品 · 三大类目",  ( 92, 107, 192), (255, 118, 117), "图文"),
    ("news-vip.jpg",            900, 500, "会员权益升级",  "100 积分 = 1 元，上不封顶",   ( 67, 206, 162), ( 24, 90, 157),  "图文"),
    # ---- 教育号 ----
    ("course-kids.jpg",         900, 600, "少儿编程",      "6-12 岁 · 图形化到 Python",  ( 56, 176, 0),   (  0, 150, 136), "COURSE"),
    ("teacher.jpg",             900, 600, "名师团队",      "平均教龄 8 年 · 双师课堂",    (102, 126, 234), (118,  75, 162), "TEAM"),
    ("news-enroll.jpg",         900, 500, "2026 春季招生简章", "早鸟价 8.5 折",         (255, 154,  68), (255,  87,  87), "图文"),
    # ---- 餐饮号 ----
    ("dish-hongshaorou.jpg",    900, 600, "招牌红烧肉",    "肥而不腻 · 每日现做",        (214,  48,  49), (250, 130,  49), "招牌菜"),
    ("store.jpg",               900, 600, "门店环境",      "可容纳 120 人 · 8 个包间",    ( 44,  62,  80), ( 76, 161, 175), "ENV"),
    ("news-newdish.jpg",        900, 500, "春季新菜上市",  "8 道时令菜品",               ( 34, 193, 195), ( 22, 160,  133), "图文"),
]


def find_font(size):
    """优先使用中文字体，避免标题显示成方块"""
    candidates = [
        r"C:\Windows\Fonts\msyhbd.ttc",   # 微软雅黑 Bold
        r"C:\Windows\Fonts\msyh.ttc",     # 微软雅黑
        r"C:\Windows\Fonts\simhei.ttf",   # 黑体
        r"C:\Windows\Fonts\simsun.ttc",   # 宋体
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]
    for p in candidates:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()


def lerp(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def make_gradient(w, h, c1, c2, diagonal=True):
    """生成渐变背景（逐像素，尺寸不大可接受）"""
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if diagonal:
                t = (x / max(w - 1, 1) + y / max(h - 1, 1)) / 2
            else:
                t = y / max(h - 1, 1)
            px[x, y] = lerp(c1, c2, t)
    return img


def draw_centered(draw, text, font, cx, y, fill):
    bbox = draw.textbbox((0, 0), text, font=font)
    w = bbox[2] - bbox[0]
    draw.text((cx - w / 2, y), text, font=font, fill=fill)
    return bbox[3] - bbox[1]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    made = []

    for fname, w, h, title, sub, c1, c2, badge in SPECS:
        img = make_gradient(w, h, c1, c2)
        d = ImageDraw.Draw(img, "RGBA")

        # 半透明装饰圆，增加层次感
        r = int(min(w, h) * 0.42)
        d.ellipse([w - r, -r // 2, w + r // 2, r], fill=(255, 255, 255, 26))
        d.ellipse([-r // 3, h - r, r, h + r // 2], fill=(255, 255, 255, 18))

        # 顶部角标
        bf = find_font(max(int(min(w, h) * 0.055), 16))
        bbox = d.textbbox((0, 0), badge, font=bf)
        bw, bh = bbox[2] - bbox[0], bbox[3] - bbox[1]
        pad_x, pad_y = 22, 12
        d.rounded_rectangle(
            [40, 40, 40 + bw + pad_x * 2, 40 + bh + pad_y * 2],
            radius=8, fill=(255, 255, 255, 60))
        d.text((40 + pad_x, 40 + pad_y - bbox[1]), badge, font=bf, fill=(255, 255, 255, 240))

        # 主标题
        tf = find_font(int(min(w, h) * 0.135))
        cx = w / 2
        tb = d.textbbox((0, 0), title, font=tf)
        ty = h / 2 - (tb[3] - tb[1]) * 0.95
        d.text((cx - (tb[2] - tb[0]) / 2 + 2, ty + 2), title, font=tf, fill=(0, 0, 0, 60))  # 阴影
        d.text((cx - (tb[2] - tb[0]) / 2, ty), title, font=tf, fill=(255, 255, 255))

        # 副标题
        sf = find_font(int(min(w, h) * 0.058))
        sb = d.textbbox((0, 0), sub, font=sf)
        sy = ty + (tb[3] - tb[1]) + int(min(w, h) * 0.055)
        d.text((cx - (sb[2] - sb[0]) / 2, sy), sub, font=sf, fill=(255, 255, 255, 225))

        # 底部分隔线
        d.rectangle([cx - w * 0.08, h - 62, cx + w * 0.08, h - 59], fill=(255, 255, 255, 150))

        path = os.path.join(OUT_DIR, fname)
        img.save(path, quality=88, optimize=True)
        made.append((fname, os.path.getsize(path)))

    # 视频/语音的占位封面（前端不直接展示，但保持数据完整）
    extra = [
        ("video-tutorial.jpg",  900, 500, "商品使用教程", "3 分钟学会",     ( 44,  62,  80), ( 15,  76, 117), "VIDEO"),
        ("video-trial.jpg",     900, 500, "免费试听课",   "少儿编程 15 分钟", ( 56, 176,   0), (  0, 121, 107), "VIDEO"),
        ("voice-welcome.jpg",   600, 600, "客服欢迎语音", "点击播放",       (102, 126, 234), (118,  75, 162), "AUDIO"),
        ("voice-booking.jpg",   600, 600, "订座确认语音", "点击播放",       (214,  48,  49), (250, 130,  49), "AUDIO"),
    ]
    for fname, w, h, title, sub, c1, c2, badge in extra:
        img = make_gradient(w, h, c1, c2)
        d = ImageDraw.Draw(img, "RGBA")
        r = int(min(w, h) * 0.42)
        d.ellipse([w - r, -r // 2, w + r // 2, r], fill=(255, 255, 255, 26))

        # 播放按钮
        cx, cy = w / 2, h / 2 - min(w, h) * 0.08
        pr = int(min(w, h) * 0.11)
        d.ellipse([cx - pr, cy - pr, cx + pr, cy + pr], fill=(255, 255, 255, 70))
        d.polygon([(cx - pr * 0.32, cy - pr * 0.5), (cx - pr * 0.32, cy + pr * 0.5),
                   (cx + pr * 0.52, cy)], fill=(255, 255, 255, 235))

        tf = find_font(int(min(w, h) * 0.088))
        tb = d.textbbox((0, 0), title, font=tf)
        d.text((cx - (tb[2] - tb[0]) / 2, cy + pr * 1.6), title, font=tf, fill=(255, 255, 255))
        sf = find_font(int(min(w, h) * 0.05))
        sb = d.textbbox((0, 0), sub, font=sf)
        d.text((cx - (sb[2] - sb[0]) / 2, cy + pr * 1.6 + (tb[3] - tb[1]) + 14),
               sub, font=sf, fill=(255, 255, 255, 210))

        bf = find_font(max(int(min(w, h) * 0.05), 14))
        bb = d.textbbox((0, 0), badge, font=bf)
        d.rounded_rectangle([30, 30, 30 + (bb[2] - bb[0]) + 40, 30 + (bb[3] - bb[1]) + 24],
                            radius=8, fill=(255, 255, 255, 55))
        d.text((50, 42 - bb[1]), badge, font=bf, fill=(255, 255, 255, 240))

        path = os.path.join(OUT_DIR, fname)
        img.save(path, quality=88, optimize=True)
        made.append((fname, os.path.getsize(path)))

    print(f"输出目录: {OUT_DIR}")
    print(f"共生成 {len(made)} 张：")
    for n, s in made:
        print(f"  {n:<28} {s/1024:6.1f} KB")
    total = sum(s for _, s in made)
    print(f"合计 {total/1024:.1f} KB")


if __name__ == "__main__":
    main()
