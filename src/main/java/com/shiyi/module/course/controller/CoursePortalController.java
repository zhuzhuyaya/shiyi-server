package com.shiyi.module.course.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiyi.common.Result;
import com.shiyi.module.course.dto.CourseQuery;
import com.shiyi.module.course.service.CoursePortalService;
import com.shiyi.module.course.vo.CourseDetailVO;
import com.shiyi.module.course.vo.CourseListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CoursePortalController {

    private final CoursePortalService coursePortalService;

    /** C 端列表:GET /api/v1/courses?keyword=扎染&heritageItemId=1&page=1 */
    @GetMapping
    public Result<Page<CourseListVO>> list(CourseQuery query) {
        return Result.ok(coursePortalService.listForPortal(query));
    }

    /** C 端详情 */
    @GetMapping("/{id}")
    public Result<CourseDetailVO> detail(@PathVariable Long id) {
        return Result.ok(coursePortalService.getDetail(id));
    }

    /** 热门榜 */
    @GetMapping("/hot")
    public Result<List<CourseListVO>> hot() {
        return Result.ok(coursePortalService.hotList());
    }
}
