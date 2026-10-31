package com.shiyi.module.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.StringReader;

/** RAG 向量索引:独立索引 shiyi_knowledge,和课程检索索引分开演进 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagConfig {

    public static final String INDEX_KNOWLEDGE = "shiyi_knowledge";

    private static final String MAPPING_JSON = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
              },
              "mappings": {
                "properties": {
                  "sourceType": { "type": "keyword" },
                  "sourceName": { "type": "keyword" },
                  "content":    { "type": "text", "analyzer": "ik_max_word" },
                  "embedding": {
                    "type": "dense_vector",
                    "dims": 1024,
                    "index": true,
                    "similarity": "cosine"
                  }
                }
              }
            }
            """;

    private final ElasticsearchClient client;

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = client.indices().exists(e -> e.index(INDEX_KNOWLEDGE)).value();
            if (!exists) {
                client.indices().create(CreateIndexRequest.of(
                        b -> b.index(INDEX_KNOWLEDGE).withJson(new StringReader(MAPPING_JSON))));
                log.info("RAG 向量索引 [{}] 创建完成", INDEX_KNOWLEDGE);
            }
        } catch (Exception e) {
            log.warn("RAG 索引初始化失败(ES 未启动?),问答将不可用:{}", e.getMessage());
        }
    }
}