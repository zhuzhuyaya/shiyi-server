package com.shiyi.module.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shiyi.common.Result;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.service.CourseService;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.service.InheritorService;
import com.shiyi.module.rag.service.RagService;
import com.shiyi.module.search.service.EsCourseService;
import com.shiyi.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class AdminController {

    private final InheritorService inheritorService;
    private final CourseService courseService;
    private final EsCourseService esCourseService;
    private final SeckillService seckillService;
    private final RagService ragService;

    @GetMapping("/inheritors")
    public Result<List<Inheritor>> inheritors(@RequestParam(defaultValue = "0") Integer status) {
        return Result.ok(inheritorService.listByStatus(status));
    }

    @PostMapping("/inheritors/{id}/verify")
    public Result<Void> verifyInheritor(@PathVariable Long id,
                                        @RequestParam boolean pass,
                                        @RequestParam(required = false) String reason) {
        inheritorService.verify(id, pass, reason);
        return Result.ok();
    }

    @GetMapping("/courses")
    public Result<List<Course>> courses(@RequestParam(defaultValue = "0") Integer status) {
        return Result.ok(courseService.listByStatus(status));
    }

    @PostMapping("/courses/{id}/verify")
    public Result<Void> verifyCourse(@PathVariable Long id,
                                     @RequestParam boolean pass,
                                     @RequestParam(required = false) String reason) {
        courseService.verify(id, pass, reason);
        return Result.ok();
    }

    /** 全量重建 ES 索引:POST /api/v1/admin/es/resync(第 6 课) */
    @PostMapping("/es/resync")
    public Result<Long> resyncEs() {
        return Result.ok(esCourseService.fullResync());
    }

    /** 全量库存预热:POST /api/v1/admin/seckill/preheat(第 7 课,Redis 数据丢失后的修复入口) */
    @PostMapping("/seckill/preheat")
    public Result<Long> preheat() {
        return Result.ok(seckillService.preheatAll());
    }

    @PostMapping("/rag/reindex")
    public Result<String> ragReindex() {
        ragService.indexAll();
        return Result.ok("RAG 索引重建完成");
    }
}
