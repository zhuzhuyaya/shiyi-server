package com.shiyi.module.review.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.common.BusinessException;
import com.shiyi.common.ReviewInvalidException;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.mapper.CourseMapper;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.service.InheritorService;
import com.shiyi.module.order.entity.Order;
import com.shiyi.module.order.mapper.OrderMapper;
import com.shiyi.module.review.dto.ReviewDTO;
import com.shiyi.module.review.entity.CourseReview;
import com.shiyi.module.review.mapper.CourseReviewMapper;
import com.shiyi.module.review.vo.ReviewVO;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final CourseReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final CourseMapper courseMapper;
    private final InheritorService inheritorService;
    private final UserMapper userMapper;

    /** 本人 + 已核销才可评;uk_order 兜底一单一评 */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long orderId, ReviewDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ReviewInvalidException();
        }
        if (order.getStatus() != Order.STATUS_CHECKED_IN) {
            throw new ReviewInvalidException();
        }

        CourseReview review = new CourseReview();
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setCourseId(order.getCourseId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setImages(dto.getImages());
        reviewMapper.insert(review);
    }

    /** C 端评价列表:按课程查,组装昵称(N+1 解法) */
    public List<ReviewVO> listByCourse(Long courseId) {
        List<CourseReview> reviews = reviewMapper.selectList(Wrappers.<CourseReview>lambdaQuery()
                .eq(CourseReview::getCourseId, courseId)
                .eq(CourseReview::getDeleted, 0)
                .orderByDesc(CourseReview::getCreateTime));

        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nicknames = userMapper.selectBatchIds(
                        reviews.stream().map(CourseReview::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return reviews.stream().map(r -> {
            ReviewVO vo = new ReviewVO();
            vo.setId(r.getId());
            vo.setNickname(nicknames.get(r.getUserId()));
            vo.setRating(r.getRating());
            vo.setContent(r.getContent());
            vo.setImages(r.getImages());
            vo.setReply(r.getReply());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).toList();
    }

    /** 传承人回复:属主传导 评价→课程→传承人 */
    public void reply(Long reviewId, String reply) {
        Inheritor inheritor = inheritorService.currentInheritor();
        CourseReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new ReviewInvalidException();
        }
        Course course = courseMapper.selectById(review.getCourseId());
        if (course == null || !course.getInheritorId().equals(inheritor.getId())) {
            throw new ReviewInvalidException();
        }
        review.setReply(reply);
        reviewMapper.updateById(review);
    }
}

