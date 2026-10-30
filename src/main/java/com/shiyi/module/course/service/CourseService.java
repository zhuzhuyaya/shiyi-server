package com.shiyi.module.course.service;

import com.shiyi.module.course.dto.CourseCreateDTO;
import com.shiyi.module.course.dto.CourseSessionCreateDTO;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.entity.CourseSession;

import java.util.List;

public interface CourseService {
    void publish(CourseCreateDTO dto);
    List<Course> listMy();
    List<Course> listByStatus(Integer status);
    void addSession(Long courseId, CourseSessionCreateDTO dto);
    List<CourseSession> listSessions(Long courseId);
    void verify(Long courseId, boolean pass);
    String updateCover(Long courseId, org.springframework.web.multipart.MultipartFile file);
}
