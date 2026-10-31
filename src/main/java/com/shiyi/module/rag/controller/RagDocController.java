package com.shiyi.module.rag.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.common.BusinessException;
import com.shiyi.common.Result;
import com.shiyi.module.rag.dto.RagDocumentDTO;
import com.shiyi.module.rag.entity.RagDocument;
import com.shiyi.module.rag.mapper.RagDocumentMapper;
import com.shiyi.module.rag.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自定义知识文档管理。增删改后全量重建向量索引——
 * InMemoryEmbeddingStore 没有按来源删除的能力,重灌是最稳的做法(数据量小,秒级完成)
 */
@RestController
@RequestMapping("/api/v1/admin/rag/documents")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class RagDocController {

    private final RagDocumentMapper ragDocumentMapper;
    private final RagService ragService;

    @GetMapping
    public Result<List<RagDocument>> list() {
        return Result.ok(ragDocumentMapper.selectList(Wrappers.<RagDocument>lambdaQuery()
                .orderByDesc(RagDocument::getId)));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody RagDocumentDTO dto) {
        RagDocument doc = new RagDocument();
        doc.setTitle(dto.getTitle());
        doc.setContent(dto.getContent());
        doc.setEnabled(1);
        ragDocumentMapper.insert(doc);
        ragService.indexAll();
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RagDocumentDTO dto) {
        RagDocument doc = mustGet(id);
        doc.setTitle(dto.getTitle());
        doc.setContent(dto.getContent());
        ragDocumentMapper.updateById(doc);
        ragService.indexAll();
        return Result.ok();
    }

    /** 启用/停用:停用的文档在下次重建时不再进索引 */
    @PutMapping("/{id}/enabled")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        RagDocument doc = mustGet(id);
        doc.setEnabled(enabled ? 1 : 0);
        ragDocumentMapper.updateById(doc);
        ragService.indexAll();
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mustGet(id);
        ragDocumentMapper.deleteById(id);
        ragService.indexAll();
        return Result.ok();
    }

    private RagDocument mustGet(Long id) {
        RagDocument doc = ragDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("知识文档不存在");
        }
        return doc;
    }
}
