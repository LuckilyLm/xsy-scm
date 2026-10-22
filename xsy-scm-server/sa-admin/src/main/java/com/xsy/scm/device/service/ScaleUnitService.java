package com.xsy.scm.device.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.domain.form.TraceScaleUnitIssueForm;
import com.xsy.scm.trace.domain.vo.TraceScaleUnitVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.service.TraceUnitService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 秤端的包装码入口。
 *
 * <p>
 * 只做入参转换：发码、选批次、补打的规则都在追溯域，这里不复制一遍校验，也不接受设备自报的 商品、仓库或操作人。
 */
@Service
@RequiredArgsConstructor
public class ScaleUnitService {

    private final TraceUnitService traceUnitService;

    /** 可选批次：本仓、该商品、余额为正，按先进先出排序。 */
    public List<TraceUnitVO.LotOption> candidateLots(String taskItemId) {
        return traceUnitService.candidateLots(parseItemId(taskItemId));
    }

    /** 一筐发一枚码；重试命中已有记录时原样返回同一枚。 */
    public TraceScaleUnitVO issue(TraceScaleUnitIssueForm form, String operator) {
        return traceUnitService.issueForWeighing(form, operator);
    }

    /** 补打标签：按码取回链接与重量，不重新发码。 */
    public TraceScaleUnitVO label(String unitCode) {
        return traceUnitService.label(unitCode);
    }

    /** 设备协议的明细编号是字符串，非法值按「明细不存在」回答，不抛 500。 */
    private static Long parseItemId(String taskItemId) {
        try {
            return Long.parseLong(taskItemId == null ? "" : taskItemId.trim());
        } catch (NumberFormatException exception) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_SORTING_ITEM_NOT_FOUND);
        }
    }
}
