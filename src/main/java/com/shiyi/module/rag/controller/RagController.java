package com.shiyi.module.rag.controller;

import com.shiyi.common.Result;
import com.shiyi.module.rag.service.RagService;
import com.shiyi.module.rag.vo.RagAnswerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /** 问答:POST /api/v1/rag/ask?question=扎染是什么,返回答案 + 命中来源。匿名可用(白名单) */
    @PostMapping("/ask")
    public Result<RagAnswerVO> ask(@RequestParam String question) {
        return Result.ok(ragService.ask(question));
    }
}
