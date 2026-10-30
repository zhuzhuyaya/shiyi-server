package com.shiyi.module.search.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiyi.common.Result;
import com.shiyi.module.course.dto.CourseQuery;
import com.shiyi.module.course.vo.CourseListVO;
import com.shiyi.module.search.service.CourseSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class SearchController {

    private final CourseSearchService courseSearchService;

    /** C 端搜索:GET /api/v1/courses/search?keyword=扎染 */
    @GetMapping("/search")
    public Result<Page<CourseListVO>> search(CourseQuery query) {
        return Result.ok(courseSearchService.search(query));
    }
}