package com.xsy.scm.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.report.constant.ScmOrderExceptionTypeEnum;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmOrderExceptionQueryForm;
import com.xsy.scm.report.domain.vo.ScmOrderExceptionRowVO;
import com.xsy.scm.report.domain.vo.ScmOrderExceptionSummaryVO;
import com.xsy.scm.report.service.ScmOrderExceptionService;
import com.xsy.scm.report.support.ScmReportExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/report/order-exceptions")
@RequiredArgsConstructor
public class ScmOrderExceptionController {
    private static final List<String> TITLES = List.of("异常类别", "发生时间", "来源单号", "来源行编号", "订单号",
            "客户", "仓库", "商品", "单位", "计划量", "实际量", "差异量", "源状态", "原因");
    private final ScmOrderExceptionService orderExceptionService;

    @PostMapping("/query")
    @SaCheckPermission(ScmReportPermission.ORDER_EXCEPTION_QUERY)
    public ResponseDTO<PageResult<ScmOrderExceptionRowVO>> query(@Valid @RequestBody ScmOrderExceptionQueryForm form) {
        return ResponseDTO.ok(orderExceptionService.query(form));
    }

    @PostMapping("/summary")
    @SaCheckPermission(ScmReportPermission.ORDER_EXCEPTION_QUERY)
    public ResponseDTO<List<ScmOrderExceptionSummaryVO>> summary(@Valid @RequestBody ScmOrderExceptionQueryForm form) {
        return ResponseDTO.ok(orderExceptionService.summary(form));
    }

    @PostMapping("/export")
    @SaCheckPermission(value = {ScmReportPermission.ORDER_EXCEPTION_QUERY, ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody ScmOrderExceptionQueryForm form, HttpServletResponse response)
            throws IOException {
        var rows = orderExceptionService.export(form).stream().map(row -> ScmReportExcel.row(TITLES,
                ScmOrderExceptionTypeEnum.valueOf(row.getExceptionType()).getLabel(), row.getOccurredAt(),
                row.getSourceNo(), row.getSourceRowId(), row.getOrderNo(), row.getCustomerName(), row.getWarehouseName(),
                row.getProductName(), row.getUnit(), row.getPlannedQuantity(), row.getActualQuantity(),
                row.getDifferenceQuantity(), row.getSourceStatus(), row.getReason())).toList();
        ScmReportExcel.write(response, "异常订单分析.xlsx", "异常事实", TITLES, rows);
    }
}
