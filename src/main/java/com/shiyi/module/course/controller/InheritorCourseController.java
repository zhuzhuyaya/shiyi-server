package com.shiyi.module.course.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shiyi.common.Result;
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

    @PostMapping
    public Result<Void> publish(@Valid @RequestBody CourseCreateDTO dto) {
        courseService.publish(dto);
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
}
