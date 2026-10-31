package com.shiyi.module.search.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiyi.module.course.dto.CourseQuery;
import com.shiyi.module.course.service.CoursePortalService;
import com.shiyi.module.course.vo.CourseListVO;
import com.shiyi.module.search.document.CourseDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSearchService {

    private final ElasticsearchClient client;
    private final CoursePortalService coursePortalService;

    public Page<CourseListVO> search(CourseQuery query) {
        // 空关键词 = 无意图浏览,直接走 DB 列表(销量排序)——路由到对的引擎
        if (StrUtil.isBlank(query.getKeyword())) {
            return coursePortalService.listForPortal(query);
        }
        try {
            return searchByEs(query);
        } catch (Exception e) {
            // 降级:搜索是增强能力,ES 故障不能拖垮浏览主流程
            log.error("ES 检索失败,降级 DB LIKE", e);
            return coursePortalService.listForPortal(query);
        }
    }

    private Page<CourseListVO> searchByEs(CourseQuery query) throws Exception {
        SearchResponse<CourseDocument> resp = client.search(s -> s
                        .index(EsCourseService.INDEX_COURSE)
                        .from((int) ((query.getPage() - 1) * query.getSize()))
                        .size((int) query.getSize())
                        .query(q -> q.bool(b -> {
                            // 标题权重 3、标签 2、简介 1——用户搜的词出现在哪,权重说了算
                            b.must(m -> m.multiMatch(mm -> mm
                                    .query(query.getKeyword())
                                    .fields("title^3", "tags^2", "description")));
                            b.filter(f -> f.term(t -> t.field("status").value(1)));
                            if (query.getHeritageItemId() != null) {
                                b.filter(f -> f.term(t -> t.field("heritageItemId")
                                        .value(query.getHeritageItemId())));
                            }
                            if (StrUtil.isNotBlank(query.getCategory())) {
                                b.filter(f -> f.term(t -> t.field("category")
                                        .value(query.getCategory())));
                            }
                            return b;
                        }))
                        .highlight(h -> h.fields("title",
                                f -> f.preTags("<em>").postTags("</em>")))
                , CourseDocument.class);

        long total = resp.hits().total() == null ? 0 : resp.hits().total().value();
        List<CourseListVO> records = resp.hits().hits().stream()
                .map(hit -> {
                    CourseListVO vo = BeanUtil.toBean(hit.source(), CourseListVO.class);
                    // 命中高亮:前端用 v-html 渲染 <em>
                    if (hit.highlight() != null && hit.highlight().containsKey("title")) {
                        vo.setTitle(hit.highlight().get("title").get(0));
                    }
                    return vo;
                }).toList();

        Page<CourseListVO> page = new Page<>(query.getPage(), query.getSize(), total);
        page.setRecords(records);
        return page;
    }
}
