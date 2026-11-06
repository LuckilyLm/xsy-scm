package com.xsy.scm.report.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.validation.ScmServiceValidationSuspension;
import com.xsy.scm.report.constant.ReportErrorCode;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.PurchaseDailyQueryForm;
import com.xsy.scm.report.domain.vo.PurchaseDailyReportVO;
import com.xsy.scm.report.service.PurchaseDailyQueryService;
import com.xsy.scm.common.excel.ScmExcel;
import com.xsy.scm.report.support.ScmReportExportGuard;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/report/purchase/daily")
@RequiredArgsConstructor
public class PurchaseDailyReportController {
    private final PurchaseDailyQueryService purchaseDailyQueryService;

    @PostMapping("/query")
    @SaCheckPermission(ScmReportPermission.PURCHASE_QUERY)
    public ResponseDTO<PurchaseDailyReportVO> query(@Valid @RequestBody PurchaseDailyQueryForm form) {
        return ResponseDTO.ok(purchaseDailyQueryService.query(form));
    }

    @PostMapping("/export")
    @SaCheckPermission(value = {ScmReportPermission.PURCHASE_QUERY, ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody PurchaseDailyQueryForm form, HttpServletResponse response)
        throws IOException {
        List<PurchaseDailyReportVO.ProductRow> products = ScmReportExportGuard.exportRows(limit -> {
            form.setPageNum(1L);
            form.setPageSize(limit);
            // 导出探针分页必然超过日常上限，仅这一处内部调用跳过入口校验。
            PurchaseDailyReportVO report = ScmServiceValidationSuspension
                .run(() -> purchaseDailyQueryService.query(form));
            if (report.getGeneratedAt() == null) {
                throw new ScmBusinessException(ReportErrorCode.REPORT_DAILY_UNAVAILABLE);
            }
            return report.getProducts().getList();
        });
        List<String> titles = List.of("统计日期", "商品编码", "商品名称", "商品规格编码", "商品规格", "采购单位", "采购单数", "采购数量", "采购金额");
        List<List<Object>> rows = products.stream()
            .map(row -> ScmExcel.row(titles, form.getReportDate(), row.getSpuCode(), row.getProductName(),
                row.getSkuCode(), row.getSkuName(), row.getPurchaseUnit(), row.getOrderCount(),
                row.getPlannedQuantity(), row.getOrderAmount()))
            .toList();
        ScmExcel.write(response, "采购商品清单-" + form.getReportDate() + ".xlsx", "采购商品清单", titles, rows);
    }
}
