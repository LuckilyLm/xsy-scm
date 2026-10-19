package com.xsy.scm.trace.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.dao.TraceCorrectionDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm;
import com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm;
import com.xsy.scm.trace.domain.form.TraceCorrectionQueryForm;
import com.xsy.scm.trace.domain.vo.TraceCorrectionVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 包装码更正：带审批的解绑。
 *
 * <p>
 * 绑定只追加，撤销也追加一条解绑事实，原绑定行不动；解绑后该码回到可绑定状态，可以重新绑到正确的分拣行。 申请与审批必须不是同一个人 —— 这是「不得无痕解绑」的落点：谁提出、谁批准、为什么，全部留痕。
 */
@Service
@RequiredArgsConstructor
public class TraceCorrectionService {

    private final TraceCorrectionDao traceCorrectionDao;

    private final TraceUnitDao traceUnitDao;

    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    private final ScmDataScopeService dataScopeService;

    /** 申请更正：撤销当前这条绑定。 */
    @Transactional(rollbackFor = Exception.class)
    public TraceCorrectionVO apply(TraceCorrectionApplyForm form, String operator) {
        TraceUnitVO.Unit unit = traceUnitDao.lockByCode(form.getUnitCode().trim());
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_NOT_FOUND);
        }
        if (!"ACTIVE".equals(unit.getStatus())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_UNIT_VOID);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(unit.getLotId()));
        Long bindingId = traceUnitDao.findActiveBindingId(unit.getId());
        if (bindingId == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_CORRECTION_NO_BINDING);
        }
        if (traceCorrectionDao.countPending(unit.getId()) > 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_CORRECTION_PENDING_EXISTS);
        }

        TraceCorrectionVO correction = new TraceCorrectionVO();
        correction.setUnitId(unit.getId());
        correction.setUnitCode(unit.getUnitCode());
        correction.setLotId(unit.getLotId());
        correction.setBindingId(bindingId);
        correction.setReason(form.getReason().trim());
        correction.setRequestedBy(operator);
        correction.setRequestedAt(OffsetDateTime.now());
        traceCorrectionDao.insertCorrection(correction);
        return traceCorrectionDao.findById(correction.getId());
    }

    /**
     * 批准：追加解绑事实 + UNBIND 事件，绑定不再生效。
     *
     * <p>
     * 自审禁止按操作者标识比较（{@code <userType>:<userId>}），与报损报溢同一口径。
     */
    @Transactional(rollbackFor = Exception.class)
    public TraceCorrectionVO approve(TraceCorrectionDecisionForm form, String operator) {
        TraceCorrectionVO correction = requirePending(form, operator);
        OffsetDateTime now = OffsetDateTime.now();
        traceCorrectionDao.insertUnbind(correction.getUnitId(), correction.getBindingId(), correction.getId(),
                correction.getReason(), now, operator);
        traceCorrectionDao.appendUnbindEvent(correction.getUnitId(), correction.getLotId(), correction.getId(), now,
                operator, correction.getReason());
        decide(form, operator, "APPROVED", now);
        return traceCorrectionDao.findById(correction.getId());
    }

    /** 驳回：只留审批意见，绑定继续有效。 */
    @Transactional(rollbackFor = Exception.class)
    public TraceCorrectionVO reject(TraceCorrectionDecisionForm form, String operator) {
        TraceCorrectionVO correction = requirePending(form, operator);
        decide(form, operator, "REJECTED", OffsetDateTime.now());
        return traceCorrectionDao.findById(correction.getId());
    }

    @Transactional(readOnly = true)
    public PageResult<TraceCorrectionVO> query(TraceCorrectionQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        List<TraceCorrectionVO> list = traceCorrectionDao.queryPage(page, form, scope.getWarehouseScope());
        return SmartPageUtil.convert2PageResult(page, list);
    }

    private TraceCorrectionVO requirePending(TraceCorrectionDecisionForm form, String operator) {
        TraceCorrectionVO correction = traceCorrectionDao.lockById(form.getId());
        if (correction == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_CORRECTION_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(correction.getLotId()));
        if (!"PENDING".equals(correction.getStatus())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_CORRECTION_STATE_INVALID);
        }
        if (operator != null && operator.equals(correction.getRequestedBy())) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_CORRECTION_SELF_APPROVAL);
        }
        return correction;
    }

    private void decide(TraceCorrectionDecisionForm form, String operator, String status, OffsetDateTime now) {
        if (traceCorrectionDao.decide(form.getId(), form.getVersion(), status, operator, now,
                form.getReason() == null ? null : form.getReason().trim()) != 1) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_CORRECTION_STATE_INVALID);
        }
    }
}
