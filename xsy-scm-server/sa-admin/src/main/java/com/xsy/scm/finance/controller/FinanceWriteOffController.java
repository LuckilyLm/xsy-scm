package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAddResultVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.support.FinanceExcel;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceWriteOffQueryService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 财务核销登记、反向和分页查询入口。 */
@RestController
@RequestMapping("/scm/finance/write-off")
@Tag(name = "SCM 财务核销")
@RequiredArgsConstructor
public class FinanceWriteOffController {

    private static final List<String> EXPORT_TITLES = List.of("核销单号", "资金类型", "资金单号", "资金方", "目标类型", "目标单号", "目标方",
            "金额", "方向", "原因", "核销时点", "操作人");

    private final FinanceWriteOffService financeWriteOffService;
    private final FinanceWriteOffQueryService financeWriteOffQueryService;

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.WRITE_OFF_ADD)
    @OperateLog
    public ResponseDTO<FinanceWriteOffAddResultVO> add(@Valid @RequestBody FinanceWriteOffAddForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeWriteOffService.add(form, idempotencyKey));
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
    @SaCheckPermission(value = {FinancePermission.WRITE_OFF_QUERY,
            FinancePermission.EXPORT}, mode = cn.dev33.satoken.annotation.SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody FinanceWriteOffQueryForm form, HttpServletResponse response)
            throws IOException {
        List<FinanceWriteOffVO> rows = financeWriteOffQueryService.exportRows(form);
        List<List<Object>> data = rows.stream()
                .map(row -> FinanceExcel.row(EXPORT_TITLES, row.getWriteOffNo(), row.getSourceType(), row.getSourceNo(),
                        row.getSourceName(), row.getTargetType(), row.getTargetNo(), row.getTargetName(),
                        row.getAmount(), row.getEntryType(), row.getReason(), row.getWrittenOffAt(), row.getOperator()))
                .toList();
        FinanceExcel.write(response, "核销明细.xlsx", "核销明细", EXPORT_TITLES, data);
    }
}
