package com.shiyi.module.review.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shiyi.common.Result;
import com.shiyi.module.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inheritor/reviews")
@RequiredArgsConstructor
@SaCheckRole("INHERITOR")
public class InheritorReviewController {

    private final ReviewService reviewService;

    /** 传承人回复:PATCH /api/v1/inheritor/reviews/1  body: {"reply":"谢谢支持"} */
    @PatchMapping("/{reviewId}")
    public Result<Void> reply(@PathVariable Long reviewId,
                              @org.springframework.web.bind.annotation.RequestBody String reply) {
        reviewService.reply(reviewId, reply);
        return Result.ok();
    }
}
