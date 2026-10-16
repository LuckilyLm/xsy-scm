package com.xsy.scm.sorting.dao;

import com.xsy.scm.sorting.domain.vo.SortingPartRecordVO;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SortingPartRecordDao {
    @Insert("""
            INSERT INTO xsy_sorting_part_record
                (task_id, task_item_id, event_type, quantity_delta, total_quantity, note, created_by)
            VALUES
                (#{taskId}, #{taskItemId}, #{eventType}, #{quantityDelta},
                 #{totalQuantity}, #{note}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SortingPartRecordVO record);

    @Select("""
            SELECT * FROM xsy_sorting_part_record
            WHERE task_item_id = #{itemId}
            ORDER BY id DESC LIMIT 1
            """)
    SortingPartRecordVO latest(@Param("itemId") Long itemId);

    @Select("""
            SELECT * FROM xsy_sorting_part_record
            WHERE task_id = #{taskId}
            ORDER BY id DESC LIMIT 200
            """)
    List<SortingPartRecordVO> recent(@Param("taskId") Long taskId);
}
