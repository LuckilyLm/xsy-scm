package com.xsy.scm.report.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmFinanceProfitQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceProfitRowVO;
import com.xsy.scm.report.domain.vo.ScmFinanceProfitSummaryVO;
import com.xsy.scm.report.service.ScmFinanceProfitService;
import com.xsy.scm.report.support.ScmReportExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/report/finance/profit")
@RequiredArgsConstructor
public class ScmFinanceProfitController {
    private static final List<String> TITLES = List.of("分析维度", "维度名称", "编码", "日期", "销售收入", "商品销售成本", "促销赠品成本", "销售毛利",
            "毛利率（%）", "缺失成本流水数");

    private final ScmFinanceProfitService financeProfitService;

    @PostMapping("/query")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_PROFIT_QUERY,
            ScmReportPermission.COST_QUERY}, mode = SaMode.AND)
    public ResponseDTO<PageResult<ScmFinanceProfitRowVO>> query(@Valid @RequestBody ScmFinanceProfitQueryForm form) {
        return ResponseDTO.ok(financeProfitService.query(form));
    }

    @PostMapping("/summary")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_PROFIT_QUERY,
            ScmReportPermission.COST_QUERY}, mode = SaMode.AND)
    public ResponseDTO<ScmFinanceProfitSummaryVO> summary(@Valid @RequestBody ScmFinanceProfitQueryForm form) {
        return ResponseDTO.ok(financeProfitService.summary(form));
    }

    @PostMapping("/export")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_PROFIT_QUERY, ScmReportPermission.COST_QUERY,
            ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody ScmFinanceProfitQueryForm form, HttpServletResponse response)
            throws IOException {
        String dimension = form.getDimension().getDescription();
        List<List<Object>> rows = financeProfitService.export(form).stream()
                .map(row -> ScmReportExcel.row(TITLES, dimension, row.getDimensionName(), row.getDimensionCode(),
                        row.getBizDate(), fixedScale(row.getRevenueAmount()), fixedScale(row.getSalesCostAmount()),
                        fixedScale(row.getGiftCostAmount()), fixedScale(row.getGrossProfit()),
                        fixedScale(row.getGrossMarginRate()), row.getCostMissingCount()))
                .toList();
        ScmReportExcel.write(response, "销售毛利分析.xlsx", "毛利分析", TITLES, rows);
    }

    private static BigDecimal fixedScale(BigDecimal value) {
        return value == null ? null : value.setScale(ScmFixedScale4Serializer.SCALE, RoundingMode.HALF_UP);
    }
}
