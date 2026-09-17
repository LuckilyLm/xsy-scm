package com.xsy.scm.admin.module.business.trace.service;

import com.xsy.scm.admin.module.business.trace.dao.TraceWarnDao;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceWarnVO;
import com.xsy.scm.base.common.domain.ResponseDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 溯源预警 Service（只读）
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class TraceWarnService {

    /**
     * 默认预警提前天数
     */
    private static final int DEFAULT_WARN_DAYS = 30;

    @Resource
    private TraceWarnDao traceWarnDao;

    /**
     * 厂商资质到期预警：返回在 warnDays 内到期或已过期的厂商
     */
    public ResponseDTO<List<TraceWarnVO>> manufacturerWarn(Integer warnDays) {
        int days = (warnDays == null || warnDays <= 0) ? DEFAULT_WARN_DAYS : warnDays;
        LocalDate deadline = LocalDate.now().plusDays(days);
        return ResponseDTO.ok(traceWarnDao.listExpiringManufacturers(deadline));
    }
}
