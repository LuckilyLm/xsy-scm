package com.xianshuyuan.scm.system.service;

import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {
    private final AuditLogMapper logs;

    public PageData<LoginLogResponse> loginLogs(LoginLogQuery query) {
        long total = logs.countLoginLogs(query);
        return new PageData<>(total == 0 ? java.util.List.of() : logs.selectLoginLogs(query),
                query.getPage(), query.getPageSize(), total);
    }

    public PageData<OperationLogResponse> operationLogs(OperationLogQuery query) {
        long total = logs.countOperationLogs(query);
        return new PageData<>(total == 0 ? java.util.List.of() : logs.selectOperationLogs(query),
                query.getPage(), query.getPageSize(), total);
    }
}
