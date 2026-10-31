package com.shiyi.common;

/** 评价越权/非法状态时统一抛,防探测(和课程不存在同一招) */
public class ReviewInvalidException extends RuntimeException {
    public ReviewInvalidException() {
        super("评价无效");
    }
}
