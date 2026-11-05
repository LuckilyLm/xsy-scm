package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAutoForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAddResultVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAutoPlanVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.common.excel.ScmExcel;
import com.xsy.scm.finance.support.FinanceExportLabels;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceWriteOffQueryService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务核销登记、反向和分页查询入口。
 */
@RestController
@RequestMapping("/scm/finance/write-off")
@Tag(name = "SCM 财务账款抵扣")
@RequiredArgsConstructor
public class FinanceWriteOffController {

    private static final List<String> EXPORT_TITLES = List.of("抵扣单号", "收付款类型", "收付款单号", "收付款对象", "账单类型", "待结算单号",
        "结算对象", "金额", "记录类型", "原因", "抵扣时间", "操作人");

    private final FinanceWriteOffService financeWriteOffService;
    private final FinanceWriteOffQueryService financeWriteOffQueryService;

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.WRITE_OFF_ADD)
    @OperateLog
    public ResponseDTO<FinanceWriteOffAddResultVO> add(@Valid @RequestBody FinanceWriteOffAddForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeWriteOffService.add(form, idempotencyKey));
    }

    /**
     * 自动 FIFO 核销预览：只读，返回将按账单日期顺序核销的单据与金额。
     */
    @PostMapping("/auto/preview")
    @SaCheckPermission(FinancePermission.WRITE_OFF_ADD)
    public ResponseDTO<List<FinanceWriteOffAutoPlanVO>> autoPreview(@Valid @RequestBody FinanceWriteOffAutoForm form) {
        return ResponseDTO.ok(financeWriteOffService.autoPreview(form));
    }

    @PostMapping("/auto")
    @SaCheckPermission(FinancePermission.WRITE_OFF_ADD)
    @OperateLog
    public ResponseDTO<FinanceWriteOffAddResultVO> auto(@Valid @RequestBody FinanceWriteOffAutoForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeWriteOffService.autoAdd(form, idempotencyKey));
    }

    @PostMapping("/reverse")
    @SaCheckPermission(FinancePermission.WRITE_OFF_REVERSE)
    @OperateLog
    public ResponseDTO<FinanceWriteOffVO> reverse(@Valid @RequestBody FinanceWriteOffReverseForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeWriteOffService.reverse(form, idempotencyKey));
    }

    @PostMapping("/query")
    @SaCheckPermission(FinancePermission.WRITE_OFF_QUERY)
    public ResponseDTO<PageResult<FinanceWriteOffVO>> query(@Valid @RequestBody FinanceWriteOffQueryForm form) {
        return ResponseDTO.ok(financeWriteOffQueryService.query(form));
    }

    @PostMapping("/export")
    @SaCheckPermission(value = {FinancePermission.WRITE_OFF_QUERY, FinancePermission.EXPORT},
        mode = cn.dev33.satoken.annotation.SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody FinanceWriteOffQueryForm form, HttpServletResponse response)
        throws IOException {
        List<FinanceWriteOffVO> rows = financeWriteOffQueryService.exportRows(form);
        List<List<Object>> data = rows.stream()
            .map(row -> ScmExcel.row(EXPORT_TITLES, row.getWriteOffNo(),
                FinanceExportLabels.businessType(row.getSourceType()), row.getSourceNo(), row.getSourceName(),
                FinanceExportLabels.businessType(row.getTargetType()), row.getTargetNo(), row.getTargetName(),
                row.getAmount(), FinanceExportLabels.entryType(row.getEntryType()), row.getReason(),
                row.getWrittenOffAt(), row.getOperator()))
            .toList();
        ScmExcel.write(response, "账款抵扣明细.xlsx", "账款抵扣明细", EXPORT_TITLES, data);
    }
}
