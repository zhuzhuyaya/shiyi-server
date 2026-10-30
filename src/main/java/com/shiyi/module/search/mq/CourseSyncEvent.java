package com.shiyi.module.search.mq;

/** 课程变更事件:只带 ID,消费端回查最新状态 */
public record CourseSyncEvent(Long courseId) {
}
