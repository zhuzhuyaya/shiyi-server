package com.shiyi.module.rag.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.mapper.CourseMapper;
import com.shiyi.module.heritage.entity.HeritageItem;
import com.shiyi.module.heritage.mapper.HeritageItemMapper;
import com.shiyi.module.rag.entity.RagDocument;
import com.shiyi.module.rag.mapper.RagDocumentMapper;
import com.shiyi.module.rag.vo.RagAnswerVO;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/** RAG 问答:检索 → 增强 → 生成。检索质量决定答案上限，生成质量决定表达下限 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final OllamaEmbeddingModel embeddingModel;
    private final OllamaChatModel chatModel;
    private final HeritageItemMapper heritageItemMapper;
    private final CourseMapper courseMapper;
    private final RagDocumentMapper ragDocumentMapper;

    /** 演示用:内存向量库(数据量小,零额外依赖)。生产换 ElasticsearchEmbeddingStore */
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    /** 索引化:非遗知识库 → chunk(带来源 metadata) → 向量化 → 存 store */
    public void indexAll() {
        // 先把非遗项目一次性查出来做 Map,课程 chunk 要带上项目名
        java.util.Map<Long, String> heritageNames = heritageItemMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        HeritageItem::getId,
                        HeritageItem::getName));

        int count = 0;
        // 索引非遗项目:metadata 加在 Document 上,切分器会复制给每个子 segment
        // 名字 Map 保留全部(课程 chunk 要能挂上项目名),知识库只收已收录的项目,待收录的不进问答
        for (HeritageItem item : heritageItemMapper.selectList(Wrappers.<HeritageItem>lambdaQuery()
                .eq(HeritageItem::getStatus, HeritageItem.STATUS_ACTIVE))) {
            Document doc = Document.from(toText(item));
            doc.metadata().put("sourceName", "非遗项目:" + item.getName());
            doc.metadata().put("type", "heritage");
            for (TextSegment seg : split(doc)) {
                Response<Embedding> resp = embeddingModel.embed(seg.text());
                embeddingStore.add(resp.content(), seg);
                count++;
            }
        }
        // 索引课程
        for (Course course : courseMapper.selectList(null)) {
            Document doc = Document.from(toText(course, heritageNames));
            doc.metadata().put("sourceName", "课程:" + course.getTitle());
            doc.metadata().put("type", "course");
            for (TextSegment seg : split(doc)) {
                Response<Embedding> resp = embeddingModel.embed(seg.text());
                embeddingStore.add(resp.content(), seg);
                count++;
            }
        }
        // 自定义知识文档:管理员在后台维护,只有启用的进索引
        for (RagDocument d : ragDocumentMapper.selectList(Wrappers.<RagDocument>lambdaQuery()
                .eq(RagDocument::getEnabled, 1))) {
            Document doc = Document.from("知识文档【%s】\n%s".formatted(d.getTitle(), d.getContent()));
            doc.metadata().put("sourceName", "知识文档:" + d.getTitle());
            doc.metadata().put("type", "doc");
            for (TextSegment seg : split(doc)) {
                Response<Embedding> resp = embeddingModel.embed(seg.text());
                embeddingStore.add(resp.content(), seg);
                count++;
            }
        }
        log.info("RAG 索引完成,共 {} 个 chunk", count);
    }

    /** 问答:检索 → 增强 → 生成,附带命中来源(可溯源,防模型编造) */
    public RagAnswerVO ask(String question) {
        // 1.0.x 的 EmbeddingStoreContentRetriever 必须传入 embeddingModel
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(4)
                .build();

        // 1.0.x 返回的是 List<Content>,不是 List<TextSegment>
        List<Content> contents = retriever.retrieve(new Query(question));
        if (contents.isEmpty()) {
            return new RagAnswerVO("我在非遗知识库里没找到相关内容，请换个问法。", List.of());
        }

        // 转成 TextSegment 再拼上下文
        List<TextSegment> segments = contents.stream()
                .map(Content::textSegment)
                .toList();

        String context = StrUtil.join("\n\n", segments.stream().map(TextSegment::text).toList());
        String prompt = """
                你是一位非遗文化助手。请**严格基于以下资料**回答用户问题，不要编造。
                如果资料里没有，请明确说"资料中没有记载"。

                资料:
                %s

                用户问题：%s
                """.formatted(context, question);

        // 1.0.x 用 chat()，不再用 generate()
        ChatResponse response = chatModel.chat(UserMessage.from(prompt));

        // 来源去重:同一 sourceName 的多个 chunk 只留第一段做摘要
        LinkedHashMap<String, String> sourceMap = new LinkedHashMap<>();
        for (TextSegment seg : segments) {
            String src = seg.metadata().getString("sourceName");
            if (StrUtil.isBlank(src)) {
                src = "未知来源";
            }
            String text = seg.text();
            sourceMap.putIfAbsent(src, text.length() > 60 ? text.substring(0, 60) + "..." : text);
        }
        List<RagAnswerVO.Source> sources = sourceMap.entrySet().stream()
                .map(e -> new RagAnswerVO.Source(e.getKey(), e.getValue()))
                .toList();

        return new RagAnswerVO(response.aiMessage().text(), sources);
    }

    /** 递归切分:先按段落，再按句子，最后按字符。chunk 400 字，重叠 40 字 */
    private List<TextSegment> split(Document doc) {
        if (doc == null || StrUtil.isBlank(doc.text())) {
            return List.of();
        }
        DocumentSplitter splitter = DocumentSplitters.recursive(400, 40);
        return splitter.split(doc);
    }

    private String toText(HeritageItem item) {
        return "非遗项目【%s】\n门类：%s\n级别：%s\n申报地区：%s\n简介：%s"
                .formatted(item.getName(),
                        StrUtil.nullToEmpty(item.getCategory()),
                        StrUtil.nullToEmpty(item.getLevel()),
                        StrUtil.nullToEmpty(item.getRegion()),
                        StrUtil.nullToEmpty(item.getDescription()));
    }

    private String toText(Course course, java.util.Map<Long, String> heritageNames) {
        String name = heritageNames.get(course.getHeritageItemId());
        return "课程【%s】\n关联非遗：%s\n标签：%s\n地址：%s\n简介：%s"
                .formatted(course.getTitle(),
                        StrUtil.nullToEmpty(name),
                        StrUtil.nullToEmpty(course.getTags()),
                        StrUtil.nullToEmpty(course.getAddress()),
                        StrUtil.nullToEmpty(course.getDescription()));
    }
}
