package com.shiyi.module.rag.entity;

import lombok.Data;

/** 检索单元:一个文档片段。RAG 的质量很大程度取决于 chunk 怎么切 */
@Data
public class Chunk {
    private Long id;              // 来源 id(heritage_item.id 或 course.id)
    private String sourceType;    // "heritage_item" / "course"
    private String sourceName;    // 来源名,便于展示"依据了哪篇"
    private String content;       // 片段正文
    private String embedding;     // 向量 JSON,768 维,存 ES
}
