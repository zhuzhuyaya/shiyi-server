package com.shiyi.module.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RagAnswerVO {

    private String answer;
    private List<Source> sources;

    @Data
    @AllArgsConstructor
    public static class Source {
        private String sourceName;   // 来源标识,来自 TextSegment.metadata()
        private String snippet;      // 命中的片段(可截断)
    }
}
