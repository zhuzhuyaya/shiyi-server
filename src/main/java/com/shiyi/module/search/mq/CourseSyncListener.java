package com.shiyi.module.search.mq;

import com.shiyi.module.search.service.EsCourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseSyncListener {

    private final EsCourseService esCourseService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_COURSE_SYNC)
    public void onCourseChange(CourseSyncEvent event) {
        log.info("收到课程变更事件 courseId={}", event.courseId());
        esCourseService.syncCourse(event.courseId());
    }
}