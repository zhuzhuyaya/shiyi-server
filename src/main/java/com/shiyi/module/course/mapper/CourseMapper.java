package com.shiyi.module.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiyi.module.course.entity.Course;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CourseMapper extends BaseMapper<Course> {

    /** 核销 +1 销量:原子自增,杜绝"读出来 +1 再写回"的丢更新 */
    @Update("UPDATE course SET sales = sales + 1 WHERE id = #{id}")
    int increaseSales(@Param("id") Long id);
}
