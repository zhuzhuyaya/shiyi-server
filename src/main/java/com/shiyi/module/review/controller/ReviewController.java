package com.shiyi.module.review.controller;

import com.shiyi.common.Result;
import com.shiyi.module.review.dto.ReviewDTO;
import com.shiyi.module.review.service.ReviewService;
import com.shiyi.module.review.vo.ReviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 用户评价:POST /api/v1/reviews/{orderId} */
    @PostMapping("/{orderId}")
    public Result<Void> submit(@PathVariable Long orderId,
                               @Valid @RequestBody ReviewDTO dto) {
        reviewService.submit(orderId, dto);
        return Result.ok();
    }

    /** C 端评价列表:GET /api/v1/reviews/course/1 (白名单已覆盖 /api/v1/**) */
    @GetMapping("/course/{courseId}")
    public Result<List<ReviewVO>> listByCourse(@PathVariable Long courseId) {
        return Result.ok(reviewService.listByCourse(courseId));
    }
}
