package com.xsy.scm.trace.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.trace.domain.form.TraceCorrectionQueryForm;
import com.xsy.scm.trace.domain.vo.TraceCorrectionVO;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 更正申请与解绑事实。解绑只追加；申请走「待审批 → 批准/驳回」的单据状态机。SQL 见 mapper/scm/trace/TraceCorrectionDao.xml。
 */
@Mapper
public interface TraceCorrectionDao {

    int insertCorrection(TraceCorrectionVO correction);

    TraceCorrectionVO findById(@Param("id") Long id);

    /**
     * 审批前锁单据行：同一张申请不能被两个审批人同时处理。
     */
    TraceCorrectionVO lockById(@Param("id") Long id);

    int countPending(@Param("unitId") Long unitId);

    /**
     * 绑定的订单行已有正式出库记录时禁止解绑，不能把已交付实物重新派给别的客户。
     */
    int countConfirmedOutboundByBinding(@Param("bindingId") Long bindingId);

    int decide(@Param("id") Long id, @Param("version") Integer version, @Param("status") String status,
        @Param("operator") String operator, @Param("decidedAt") OffsetDateTime decidedAt,
        @Param("reason") String reason);

    int insertUnbind(@Param("unitId") Long unitId, @Param("bindingId") Long bindingId,
        @Param("correctionId") Long correctionId, @Param("reason") String reason,
        @Param("unboundAt") OffsetDateTime unboundAt, @Param("operator") String operator);

    int appendUnbindEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId,
        @Param("correctionId") Long correctionId, @Param("occurredAt") OffsetDateTime occurredAt,
        @Param("operator") String operator, @Param("reason") String reason);

    List<TraceCorrectionVO> queryPage(Page<?> page, @Param("query") TraceCorrectionQueryForm query,
        @Param("scope") ScmValueScope scope);
}
