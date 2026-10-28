package com.xsy.scm.finance.controller;

import java.io.IOException;
import java.util.List;

import com.xsy.scm.finance.domain.form.FinancePostingAddForm;
import com.xsy.scm.finance.domain.form.FinancePostingQueryForm;
import com.xsy.scm.finance.domain.form.FinancePostingUpdateForm;
import com.xsy.scm.finance.domain.form.FinancePostingVoidForm;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinancePostingQueryService;
import com.xsy.scm.finance.service.FinancePostingService;
import com.xsy.scm.finance.support.FinanceExcel;
import com.xsy.scm.finance.support.FinanceExportLabels;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 过账特殊单据：隐藏式的财务虚拟单据，登记、修改、作废、查询与导出。
 *
 * <p>
 * 它不生成应收应付、不参与核销 —— 所有端点只动过账表与财务操作日志， 因此不存在「过账后客户欠款变了」的路径。
 */
@RestController
@RequestMapping("/scm/finance/posting")
@Tag(name = "SCM 财务过账特殊单据")
@RequiredArgsConstructor
public class FinancePostingController {

    private static final List<String> EXPORT_TITLES = List.of("过账单号", "客户名称", "结算客户", "过账日期", "过账金额", "税点", "返款状态",
        "状态", "备注", "作废原因", "登记人", "登记时间");

    private final FinancePostingService financePostingService;
    private final FinancePostingQueryService financePostingQueryService;

    @PostMapping("/query")
    @SaCheckPermission(FinancePermission.POSTING_QUERY)
    public ResponseDTO<PageResult<FinancePostingVO>> query(@Valid @RequestBody FinancePostingQueryForm form) {
        return ResponseDTO.ok(financePostingQueryService.query(form));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(FinancePermission.POSTING_QUERY)
    public ResponseDTO<FinancePostingVO> detail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(financePostingQueryService.detail(id));
    }

    @PostMapping("/export")
    @SaCheckPermission(value = {FinancePermission.POSTING_QUERY, FinancePermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody FinancePostingQueryForm form, HttpServletResponse response)
        throws IOException {
        List<FinancePostingVO> rows = financePostingQueryService.exportRows(form);
        List<List<Object>> data = rows.stream()
            .map(row -> FinanceExcel.row(EXPORT_TITLES, row.getPostingNo(), row.getCustomerName(),
                row.getSettlementCustomerName(), row.getDocumentDate(), row.getAmount(), row.getTaxRate(),
                FinanceExportLabels.postingRefundStatus(row.getRefundStatus()),
                FinanceExportLabels.postingStatus(row.getStatus()), row.getRemark(), row.getVoidReason(),
                row.getCreatedBy(), row.getCreatedAt()))
            .toList();
        FinanceExcel.write(response, "过账明细.xlsx", "过账明细", EXPORT_TITLES, data);
    }

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.POSTING_ADD)
    @OperateLog
    public ResponseDTO<FinancePostingVO> add(@Valid @RequestBody FinancePostingAddForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financePostingService.add(form, idempotencyKey));
    }

    @PostMapping("/update")
    @SaCheckPermission(FinancePermission.POSTING_UPDATE)
    @OperateLog
    public ResponseDTO<FinancePostingVO> update(@Valid @RequestBody FinancePostingUpdateForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financePostingService.update(form, idempotencyKey));
    }

    @PostMapping("/void")
    @SaCheckPermission(FinancePermission.POSTING_VOID)
    @OperateLog
    public ResponseDTO<FinancePostingVO> voidPosting(@Valid @RequestBody FinancePostingVoidForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financePostingService.voidPosting(form, idempotencyKey));
    }
}
