package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public Result<String> chat(@RequestParam Long accountId, 
                               @RequestParam String openid, 
                               @RequestParam String message) {
        String response = chatService.chat(accountId, openid, message);
        return Result.success(response);
    }

    @DeleteMapping
    public Result<Void> clear(@RequestParam Long accountId, @RequestParam String openid) {
        chatService.clearConversation(accountId, openid);
        return Result.success();
    }
}