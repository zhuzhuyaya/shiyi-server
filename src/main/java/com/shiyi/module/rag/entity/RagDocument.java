package com.shiyi.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 自定义知识文档:管理员在后台维护,只有启用的才进 RAG 索引 */
@Data
@TableName("rag_document")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String content;
    private Integer enabled;   // 1 启用 0 停用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
