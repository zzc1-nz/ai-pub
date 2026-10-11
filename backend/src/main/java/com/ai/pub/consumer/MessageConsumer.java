package com.ai.pub.consumer;

import com.ai.pub.config.RabbitMQConfig;
import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.service.*;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    private final WechatAccountService wechatAccountService;
    private final WechatMessageService wechatMessageService;
    private final WechatReplyService wechatReplyService;
    private final ChatService chatService;
    private final WechatApiService wechatApiService;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageConsumer(WechatAccountService wechatAccountService,
                          WechatMessageService wechatMessageService,
                          WechatReplyService wechatReplyService,
                          ChatService chatService,
                          WechatApiService wechatApiService,
                          SimpMessagingTemplate messagingTemplate) {
        this.wechatAccountService = wechatAccountService;
        this.wechatMessageService = wechatMessageService;
        this.wechatReplyService = wechatReplyService;
        this.chatService = chatService;
        this.wechatApiService = wechatApiService;
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.MESSAGE_QUEUE)
    public void handleMessage(WechatMessage message, Channel channel, Message amqpMessage) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        log.info("从队列接收消息: messageId={}, content={}", message.getId(), message.getContent());

        try {
            // 检查消息是否已被处理过
            WechatMessage latest = wechatMessageService.getById(message.getId());
            if (latest.getReplyContent() != null && !latest.getReplyContent().isEmpty()) {
                log.info("消息已被处理，跳过: messageId={}", message.getId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            WechatAccount account = wechatAccountService.getById(message.getAccountId());
            if (account == null) {
                log.error("公众号不存在: accountId={}", message.getAccountId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            String replyContent = processMessage(account, message);
            latest.setReplyContent(replyContent);
            wechatMessageService.update(message.getId(), latest);

            // WebSocket 推送回复更新到前端
            messagingTemplate.convertAndSend("/topic/messages", latest);

            // 通过微信客服消息 API 异步发送回复
            boolean sent = wechatApiService.sendCustomerMessage(account.getId(), message.getOpenid(), replyContent);
            if (!sent) {
                log.warn("客服消息发送失败，尝试降级为被动回复: messageId={}", message.getId());
            }

            log.info("消息处理完成: messageId={}, reply={}", message.getId(), replyContent);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理队列消息失败: messageId={}", message.getId(), e);
            // 失败时拒绝消息，不重新入队（进入死信队列）
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private String processMessage(WechatAccount account, WechatMessage message) {
        String userMessage = message.getContent();
        String openid = message.getOpenid();
        String replyContent = null;
        String replyType = null;

        try {
            log.info("开始处理消息: userMessage={}, openid={}, aiEnabled={}", userMessage, openid, account.getAiEnabled());

            replyContent = wechatReplyService.findReply(account.getId(), userMessage);

            if (replyContent != null && !replyContent.isEmpty()) {
                replyType = "KEYWORD";
                log.info("匹配到关键词回复: {}", replyContent);
            } else if (Boolean.TRUE.equals(account.getAiEnabled())) {
                log.info("准备调用AI生成回复...");
                try {
                    replyContent = chatService.chat(account.getId(), openid, userMessage);
                    replyType = "AI";
                    log.info("AI生成回复: {}", replyContent);
                } catch (Exception e) {
                    log.error("AI调用失败，使用默认回复", e);
                    replyContent = null;
                }
            }

            if (replyContent == null || replyContent.isEmpty()) {
                replyContent = account.getDefaultReply();
                if (replyContent == null || replyContent.isEmpty()) {
                    replyContent = "您好，感谢您的关注！";
                }
                replyType = "DEFAULT";
                log.info("使用默认回复: {}", replyContent);
            }

            message.setReplyType(replyType);
            return replyContent;

        } catch (Exception e) {
            log.error("消息处理失败", e);
            message.setReplyType("DEFAULT");
            return account.getDefaultReply() != null ? account.getDefaultReply() : "您好，感谢您的关注！";
        }
    }
}
