package com.shiyi.module.course.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shiyi.common.Result;
import com.shiyi.infra.limiter.RateLimit;
import com.shiyi.module.course.dto.CourseCreateDTO;
import com.shiyi.module.course.dto.CourseSessionCreateDTO;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.entity.CourseSession;
import com.shiyi.module.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inheritor/courses")
@RequiredArgsConstructor
@SaCheckRole("INHERITOR")   // 类级：整个控制器都要求传承人角色
public class InheritorCourseController {

    private final CourseService courseService;

    @RateLimit(key = "course:publish", window = 60, max = 10)   // 发课是写操作,每人 60s 最多 10 次
    @PostMapping
    public Result<Void> publish(@Valid @RequestBody CourseCreateDTO dto) {
        courseService.publish(dto);
        return Result.ok();
    }

    /** 改课:仅待审核/未通过可编辑;被驳回的保存后自动重新进入待审核 */
    @RateLimit(key = "course:update", window = 60, max = 10)
    @PutMapping("/{courseId}")
    public Result<Void> update(@PathVariable Long courseId,
                               @Valid @RequestBody CourseCreateDTO dto) {
        courseService.updateCourse(courseId, dto);
        return Result.ok();
    }

    @GetMapping
    public Result<List<Course>> my() {
        return Result.ok(courseService.listMy());
    }

    @PostMapping("/{courseId}/sessions")
    public Result<Void> addSession(@PathVariable Long courseId,
                                   @Valid @RequestBody CourseSessionCreateDTO dto) {
        courseService.addSession(courseId, dto);
        return Result.ok();
    }

    @GetMapping("/{courseId}/sessions")
    public Result<List<CourseSession>> sessions(@PathVariable Long courseId) {
        return Result.ok(courseService.listSessions(courseId));
    }

    @PostMapping("/{courseId}/cover")
    public Result<String> cover(@PathVariable Long courseId,
                                @RequestParam("file") MultipartFile file) {
        return Result.ok(courseService.updateCover(courseId, file));
    }

    /** 图集:POST /api/v1/inheritor/courses/1/images, form-data 多个 file 字段 */
    @PostMapping("/{courseId}/images")
    public Result<Void> images(@PathVariable Long courseId,
                               @RequestParam("files") List<MultipartFile> files) {
        courseService.updateImages(courseId, files);
        return Result.ok();
    }

    /** 停售场次:POST /api/v1/inheritor/courses/1/sessions/2/close */
    @PostMapping("/{courseId}/sessions/{sessionId}/close")
    public Result<Void> closeSession(@PathVariable Long courseId,
                                     @PathVariable Long sessionId) {
        courseService.closeSession(courseId, sessionId);
        return Result.ok();
    }
}
