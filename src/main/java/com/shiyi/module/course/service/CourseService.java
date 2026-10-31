package com.shiyi.module.course.service;

import com.shiyi.module.course.dto.CourseCreateDTO;
import com.shiyi.module.course.dto.CourseSessionCreateDTO;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.entity.CourseSession;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CourseService {
    void publish(CourseCreateDTO dto);
    void updateCourse(Long courseId, CourseCreateDTO dto);
    List<Course> listMy();
    List<Course> listByStatus(Integer status);
    void addSession(Long courseId, CourseSessionCreateDTO dto);
    List<CourseSession> listSessions(Long courseId);
    void closeSession(Long courseId, Long sessionId);
    void verify(Long courseId, boolean pass, String reason);
    String updateCover(Long courseId, MultipartFile file);
    void updateImages(Long courseId, List<MultipartFile> files);
}
