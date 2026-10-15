# -*- coding: utf-8 -*-
"""
模拟微信服务器推送消息 —— 面试演示脚本

用途：不依赖真实微信公众号，直接构造符合微信规范的 XML 回调请求，
      走通「签名校验 → XML 解析 → 去重 → 入库 → RabbitMQ → 消费者 → 关键词匹配 → 回写」
      的完整链路，用于现场演示系统的核心能力。

原理：
  微信服务器推送消息时，会用「Token + timestamp + nonce」三个值
  按字典序排序后拼接取 SHA1，作为 signature 参数。本脚本按同样规则
  计算出正确签名，因此能通过后端的 WechatUtil.checkSignature 校验。

用法：
    python simulate_wechat.py                          # 用默认消息演示
    python simulate_wechat.py "我要退款"                 # 指定消息内容
    python simulate_wechat.py "我要退款" 1               # 指定消息内容 + 公众号ID
"""
import hashlib
import random
import sys
import time
import urllib.parse
import urllib.request

BASE_URL = "http://localhost:8080"

# 各公众号的 Token（与 wechat_account.token 一致）
ACCOUNT_TOKENS = {
    1: "ecommerce_token_2026",
    2: "education_token_2026",
    3: "catering_token_2026",
}

ACCOUNT_NAMES = {
    1: "优选好物商城（电商）",
    2: "启明教育在线（教育）",
    3: "味多多餐饮服务（餐饮）",
}


def make_signature(token: str, timestamp: str, nonce: str) -> str:
    """微信签名算法：token/timestamp/nonce 字典序排序 → 拼接 → SHA1"""
    joined = "".join(sorted([token, timestamp, nonce]))
    return hashlib.sha1(joined.encode("utf-8")).hexdigest()


def send_text(account_id: int, content: str, openid: str = None) -> str:
    token = ACCOUNT_TOKENS[account_id]
    timestamp = str(int(time.time()))
    nonce = str(random.randint(1000000000, 9999999999))
    signature = make_signature(token, timestamp, nonce)

    if openid is None:
        openid = "oDemoInterview%03d" % random.randint(1, 999)

    msg_id = str(random.randint(10**15, 10**16 - 1))
    xml = (
        "<xml>"
        f"<ToUserName><![CDATA[gh_demo_{account_id:03d}]]></ToUserName>"
        f"<FromUserName><![CDATA[{openid}]]></FromUserName>"
        f"<CreateTime>{timestamp}</CreateTime>"
        "<MsgType><![CDATA[text]]></MsgType>"
        f"<Content><![CDATA[{content}]]></Content>"
        f"<MsgId>{msg_id}</MsgId>"
        "</xml>"
    )

    url = (
        f"{BASE_URL}/wx/callback/{account_id}"
        f"?signature={signature}&timestamp={timestamp}&nonce={nonce}"
    )

    req = urllib.request.Request(
        url,
        data=xml.encode("utf-8"),
        headers={"Content-Type": "application/xml"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8")


def main():
    content = sys.argv[1] if len(sys.argv) > 1 else "你们这个太差了，我要投诉"
    account_id = int(sys.argv[2]) if len(sys.argv) > 2 else 1

    print("=" * 62)
    print(" 模拟微信服务器推送消息")
    print("=" * 62)
    print(f" 公众号   : [{account_id}] {ACCOUNT_NAMES.get(account_id, '未知')}")
    print(f" 消息内容 : {content}")
    print()

    t0 = time.time()
    result = send_text(account_id, content)
    elapsed = (time.time() - t0) * 1000

    print(f" 回调响应 : {result!r}")
    print(f" 响应耗时 : {elapsed:.0f} ms")
    print()
    print(" 说明：微信要求 5 秒内响应，这里立即返回 success，")
    print("       实际回复由 RabbitMQ 消费者异步处理，不阻塞回调。")
    print()
    print(" 查看处理结果：")
    print("   docker logs ai-pub-backend --tail 20")
    print("   或打开后台「消息记录」页面，最新一条即为本次推送。")


if __name__ == "__main__":
    main()
