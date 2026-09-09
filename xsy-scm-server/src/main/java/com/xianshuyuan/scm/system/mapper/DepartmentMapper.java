package com.xianshuyuan.scm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.system.entity.DepartmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {
    DepartmentEntity lockActive(@Param("id") long id);
    List<DepartmentEntity> descendants(@Param("id") long id);
    long countActiveChildren(@Param("id") long id);
    long countAssignedUsers(@Param("id") long id);
    int updateDepartment(@Param("department") DepartmentEntity department);
    void insertAudit(@Param("id") long id, @Param("actorId") Long actorId,
            @Param("actor") String actor, @Param("operation") String operation,
            @Param("before") String before, @Param("after") String after);
}
