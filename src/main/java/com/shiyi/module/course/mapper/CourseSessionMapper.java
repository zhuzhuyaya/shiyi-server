package com.shiyi.module.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiyi.module.course.entity.CourseSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CourseSessionMapper extends BaseMapper<CourseSession> {

    /** DB 兜底扣库存:影响行数 0 = 真没库存(同一行的 UPDATE 在 InnoDB 里串行,行锁保证不超卖) */
    @Update("UPDATE course_session SET remaining_stock = remaining_stock - 1 " +
            "WHERE id = #{id} AND remaining_stock > 0")
    int deductStock(@Param("id") Long id);

    /** 回补库存:关单/退款时用(第 8 课) */
    @Update("UPDATE course_session SET remaining_stock = remaining_stock + 1 WHERE id = #{id}")
    int restoreStock(@Param("id") Long id);
}
