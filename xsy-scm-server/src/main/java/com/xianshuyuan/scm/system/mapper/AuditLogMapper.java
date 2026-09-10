package com.xianshuyuan.scm.system.mapper;

import com.xianshuyuan.scm.system.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {
    long countLoginLogs(@Param("query") LoginLogQuery query);

    List<LoginLogResponse> selectLoginLogs(@Param("query") LoginLogQuery query);

    long countOperationLogs(@Param("query") OperationLogQuery query);

    List<OperationLogResponse> selectOperationLogs(@Param("query") OperationLogQuery query);
}
