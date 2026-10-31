package com.shiyi.module.user.controller;

import com.shiyi.common.Result;
import com.shiyi.module.user.entity.Message;
import com.shiyi.module.user.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 我的站内信:GET /api/v1/messages */
    @GetMapping
    public Result<List<Message>> my() {
        return Result.ok(messageService.listMine());
    }

    /** 标记已读:POST /api/v1/messages/1/read */
    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }
}
