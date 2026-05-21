package com.ai.pub.controller;

import com.ai.pub.repository.WechatMessageRepository;
import com.ai.pub.common.WechatUtil;
import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.service.WechatAccountService;
import com.ai.pub.service.WechatMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wx/callback/{accountId}")
public class WechatCallbackController {

    private static final Logger log = LoggerFactory.getLogger(WechatCallbackController.class);

    private final WechatAccountService wechatAccountService;
    private final WechatMessageService wechatMessageService;
    private final RabbitTemplate rabbitTemplate;
    private final WechatUtil wechatUtil;
    private final WechatMessageRepository wechatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String MESSAGE_QUEUE = "wechat.message.queue";

    public WechatCallbackController(
            WechatAccountService wechatAccountService,
            WechatMessageService wechatMessageService,
            RabbitTemplate rabbitTemplate,
            WechatUtil wechatUtil,
            WechatMessageRepository wechatMessageRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.wechatAccountService = wechatAccountService;
        this.wechatMessageService = wechatMessageService;
        this.rabbitTemplate = rabbitTemplate;
        this.wechatUtil = wechatUtil;
        this.wechatMessageRepository = wechatMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public String verify(
            @PathVariable Long accountId,
            @RequestParam(name = "signature") String signature,
            @RequestParam(name = "timestamp") String timestamp,
            @RequestParam(name = "nonce") String nonce,
            @RequestParam(name = "echostr") String echostr) {
        log.info("微信验证请求: accountId={}, signature={}", accountId, signature);

        WechatAccount account = wechatAccountService.getById(accountId);
        if (account == null) {
            log.error("公众号不存在: accountId={}", accountId);
            return "";
        }

        boolean isValid = wechatUtil.checkSignature(account.getToken(), signature, timestamp, nonce);
        if (isValid) {
            log.info("微信验证成功");
            return echostr;
        } else {
            log.error("微信验证失败");
            return "";
        }
    }

    @PostMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public String handleMessage(
            @PathVariable Long accountId,
            @RequestParam(name = "signature") String signature,
            @RequestParam(name = "timestamp") String timestamp,
            @RequestParam(name = "nonce") String nonce,
            HttpServletRequest request) {
        log.info("收到微信消息: accountId={}", accountId);

        try {
            WechatAccount account = wechatAccountService.getById(accountId);
            if (account == null) {
                log.error("公众号不存在: accountId={}", accountId);
                return "";
            }

            boolean isValid = wechatUtil.checkSignature(account.getToken(), signature, timestamp, nonce);
            if (!isValid) {
                log.error("消息签名验证失败");
                return "";
            }

            byte[] bytes = request.getInputStream().readAllBytes();
            String xmlData = new String(bytes, "UTF-8");

            if (xmlData.contains("??") || isMessyCode(xmlData)) {
                xmlData = new String(bytes, "GBK");
                log.info("检测到UTF-8乱码，尝试GBK编码解析");
            }
            log.info("收到的XML: {}", xmlData);

            WechatMessage message = wechatUtil.parseMessage(accountId, xmlData);

            if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
                log.warn("无效消息，跳过处理");
                return "";
            }

            // 去重：同一 msgId 只入队一次，微信重试直接跳过
            if (message.getMsgId() != null && wechatMessageRepository.findByMsgId(message.getMsgId()).isPresent()) {
                log.info("消息已存在，跳过重复处理: msgId={}", message.getMsgId());
                return "success";
            }

            message.setAccountId(accountId);
            wechatMessageService.create(message);

            // WebSocket 实时推送新消息到前端
            messagingTemplate.convertAndSend("/topic/messages", message);

            // 异步处理：发送到 RabbitMQ，由消费者处理后通过客服消息 API 回复
            sendToQueue(message);

            return "success";

        } catch (Exception e) {
            log.error("处理微信消息失败", e);
            return "";
        }
    }

    private void sendToQueue(WechatMessage message) {
        try {
            rabbitTemplate.convertAndSend(MESSAGE_QUEUE, message);
            log.info("消息已发送到队列: messageId={}", message.getId());
        } catch (Exception e) {
            log.error("发送消息到队列失败", e);
        }
    }

    private boolean isMessyCode(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((int) c == 0xFFFD || (int) c == 0x003F) {
                return true;
            }
        }
        return false;
    }
}
