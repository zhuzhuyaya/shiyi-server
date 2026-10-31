package com.shiyi.module.search.service;

import cn.hutool.core.bean.BeanUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.common.BusinessException;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.entity.CourseSession;
import com.shiyi.module.course.mapper.CourseMapper;
import com.shiyi.module.course.mapper.CourseSessionMapper;
import com.shiyi.module.heritage.entity.HeritageItem;
import com.shiyi.module.heritage.mapper.HeritageItemMapper;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.mapper.InheritorMapper;
import com.shiyi.module.search.document.CourseDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsCourseService {

    public static final String INDEX_COURSE = "course";

    /** 三引号文本块(JDK 15+):mapping 就是 JSON,别用字符串拼接 */
    private static final String MAPPING_JSON = """
            {
              "mappings": {
                "properties": {
                  "title":          { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                  "tags":           { "type": "text", "analyzer": "ik_max_word" },
                  "description":    { "type": "text", "analyzer": "ik_max_word" },
                  "cover":          { "type": "keyword", "index": false, "doc_values": false },
                  "address":        { "type": "keyword", "index": false, "doc_values": false },
                  "inheritorName":  { "type": "keyword" },
                  "heritageName":   { "type": "keyword" },
                  "heritageItemId": { "type": "long" },
                  "minPrice":       { "type": "double" },
                  "sales":          { "type": "integer" },
                  "status":         { "type": "integer" },
                  "heritageName":   { "type": "keyword" },
                  "category":       { "type": "keyword" },   // 门类筛选
                  "heritageItemId": { "type": "long" }
                }
              }
            }
            """;

    private final ElasticsearchClient client;
    private final CourseMapper courseMapper;
    private final CourseSessionMapper courseSessionMapper;
    private final InheritorMapper inheritorMapper;
    private final HeritageItemMapper heritageItemMapper;

    @PostConstruct
    public void ensureIndexOnStart() {
        try {
            ensureIndex();
        } catch (Exception e) {
            // 中间件缺席不该让应用起不来:搜索降级 DB,主流程照跑
            log.warn("ES 索引初始化失败(ES 未启动?),搜索将降级 DB 查询:{}", e.getMessage());
        }
    }

    public void ensureIndex() throws Exception {
        boolean exists = client.indices().exists(e -> e.index(INDEX_COURSE)).value();
        if (!exists) {
            client.indices().create(CreateIndexRequest.of(
                    b -> b.index(INDEX_COURSE).withJson(new StringReader(MAPPING_JSON))));
            log.info("ES 索引 [{}] 创建完成", INDEX_COURSE);
        }
    }

    /** 单课同步:上架 → upsert 文档;下架/删除 → 移除文档。幂等:_id = courseId */
    public void syncCourse(Long courseId) {
        try {
            ensureIndex();
            Course course = courseMapper.selectById(courseId);
            if (course == null || course.getStatus() != Course.STATUS_ONLINE) {
                removeFromIndex(courseId);
                return;
            }
            client.index(i -> i.index(INDEX_COURSE)
                    .id(String.valueOf(courseId))
                    .document(buildDoc(course)));
        } catch (ElasticsearchException e) {
            log.error("ES 同步失败 courseId={}:{}", courseId, e.response().error().reason());
        } catch (Exception e) {
            log.error("ES 同步失败 courseId={}", courseId, e);
        }
    }

    /** 全量重建:ES 数据丢失 / 索引结构变更后一键恢复——"派生视图永远可重建" */
    public long fullResync() {
        try {
            ensureIndex();
            List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                    .eq(Course::getStatus, Course.STATUS_ONLINE)
                    .eq(Course::getDeleted, 0));

            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (Course course : courses) {
                bulk.operations(op -> op.index(idx -> idx
                        .index(INDEX_COURSE)
                        .id(String.valueOf(course.getId()))
                        .document(buildDoc(course))));
            }
            boolean hasErrors = client.bulk(bulk.build()).errors();
            if (hasErrors) {
                throw new BusinessException("全量重建存在失败项,详见 ES 日志");
            }
            client.indices().refresh(r -> r.index(INDEX_COURSE));
            return courses.size();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES 全量重建失败", e);
            throw new BusinessException("全量重建失败,请检查 ES 状态");
        }
    }

    private void removeFromIndex(Long courseId) throws Exception {
        try {
            client.delete(d -> d.index(INDEX_COURSE).id(String.valueOf(courseId)));
        } catch (ElasticsearchException e) {
            // 文档本就不在索引里(从未同步过),404 忽略
        }
    }

    private CourseDocument buildDoc(Course course) {
        CourseDocument doc = BeanUtil.toBean(course, CourseDocument.class);
        Inheritor inheritor = inheritorMapper.selectById(course.getInheritorId());
        if (inheritor != null) {
            doc.setInheritorName(inheritor.getName());
        }
        HeritageItem item = heritageItemMapper.selectById(course.getHeritageItemId());
        if (item != null) {
            doc.setHeritageName(item.getName());
            doc.setCategory(item.getCategory());
        }
        CourseSession cheapest = courseSessionMapper.selectOne(Wrappers.<CourseSession>lambdaQuery()
                .eq(CourseSession::getCourseId, course.getId())
                .orderByAsc(CourseSession::getPrice)
                .last("LIMIT 1"));
        doc.setMinPrice(cheapest == null ? null : cheapest.getPrice());
        return doc;
    }
}

