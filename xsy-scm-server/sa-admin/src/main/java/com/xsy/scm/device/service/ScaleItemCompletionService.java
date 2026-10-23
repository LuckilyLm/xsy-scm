package com.xsy.scm.device.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.device.constant.ScaleDeviceErrorCode;
import com.xsy.scm.device.domain.form.ScaleItemCompletionForm;
import com.xsy.scm.device.domain.vo.ScaleItemCompletionVO;
import com.xsy.scm.device.support.ScaleDeviceContext;
import com.xsy.scm.device.support.ScaleDevicePrincipal;
import com.xsy.scm.sorting.domain.form.SortingScaleCompleteForm;
import com.xsy.scm.sorting.domain.vo.SortingScaleCompletionVO;
import com.xsy.scm.sorting.service.SortingScaleEventService;
import com.xsy.scm.trace.service.TraceUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品集合完成的协议适配层。
 *
 * <p>
 * 累计总量由分拣域按筐重算，设备提交的合计不参与判定。设备与员工身份取自已校验的上下文，不接受请求体自报。
 */
@Service
@RequiredArgsConstructor
public class ScaleItemCompletionService {

    private final SortingScaleEventService sortingScaleEventService;

    private final TraceUnitService traceUnitService;

    @Transactional(rollbackFor = Exception.class)
    public ScaleItemCompletionVO complete(String taskItemId, ScaleItemCompletionForm form) {
        ScaleDevicePrincipal principal = ScaleDeviceContext.require();
        SortingScaleCompleteForm complete = new SortingScaleCompleteForm();
        complete.setOperationId(form.getOperationId());
        complete.setTaskId(parseId(form.getTaskId()));
        complete.setTaskItemId(parseId(taskItemId));
        complete.setJobVersion(form.getJobVersion());
        complete.setTaskItemVersion(toItemVersion(form.getTaskItemVersion()));
        complete.setManifestHash(form.getManifestHash());
        complete.setResult(form.getResult());
        complete.setReason(form.getReason());
        complete.setDeviceId(principal.deviceId());
        complete.setDeviceSessionId(principal.sessionId());
        complete.setEmployeeId(principal.employeeId());
        ScaleItemCompletionVO result = toVO(sortingScaleEventService.completeBaskets(complete));
        // 集合完成写入实拣量之后，才谈得上把该行的称重筐绑到客户：绑定校验依赖实拣量，顺序反了必然超量。
        if (!Boolean.TRUE.equals(result.getDuplicated())) {
            traceUnitService.bindWeighedUnits(parseId(taskItemId), result.getCompletionId(), ScmOperator.current());
        }
        return result;
    }

    private static ScaleItemCompletionVO toVO(SortingScaleCompletionVO source) {
        ScaleItemCompletionVO vo = new ScaleItemCompletionVO();
        vo.setCompletionId(source.getCompletionId());
        vo.setOperationId(source.getOperationId());
        vo.setTaskItemId(source.getTaskItemId() == null ? null : String.valueOf(source.getTaskItemId()));
        vo.setBasketCount(source.getBasketCount());
        vo.setTotalQuantity(source.getTotalQuantity());
        vo.setSortedQuantity(source.getSortedQuantity());
        vo.setBusinessUnit(source.getBusinessUnit());
        vo.setResult(source.getResult());
        vo.setState(source.getStatus());
        vo.setDuplicated(source.getDuplicated());
        return vo;
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_NOT_FOUND);
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_NOT_FOUND);
        }
    }

    /** 设备协议的明细版本是无符号长整型，库里是整型。超出范围说明设备与平台的数据格式对不上，直接拒绝，不截断成另一个版本号。 */
    private static Integer toItemVersion(Long value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_STATE_INVALID);
        }
        return value.intValue();
    }
}
