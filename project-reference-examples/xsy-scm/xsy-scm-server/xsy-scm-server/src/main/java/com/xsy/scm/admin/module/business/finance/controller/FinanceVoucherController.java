package com.xsy.scm.admin.module.business.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherPushConfirmForm;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherPushForm;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.FinanceVoucherDetailVO;
import com.xsy.scm.admin.module.business.finance.domain.vo.FinanceVoucherVO;
import com.xsy.scm.admin.module.business.finance.service.FinanceVoucherService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会计凭证 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "财务管理-会计凭证")
public class FinanceVoucherController {

    @Resource
    private FinanceVoucherService financeVoucherService;

    @Operation(summary = "分页查询会计凭证 @author xsy-scm")
    @PostMapping("/finance/voucher/query")
    @SaCheckPermission("financeVoucher:query")
    public ResponseDTO<PageResult<FinanceVoucherVO>> query(@RequestBody @Valid VoucherQueryForm queryForm) {
        return financeVoucherService.query(queryForm);
    }

    @Operation(summary = "查询会计凭证详情 @author xsy-scm")
    @GetMapping("/finance/voucher/get/{voucherId}")
    @SaCheckPermission("financeVoucher:query")
    public ResponseDTO<FinanceVoucherDetailVO> detail(@PathVariable Long voucherId) {
        return financeVoucherService.detail(voucherId);
    }

    @Operation(summary = "生成会计凭证（借贷必须平衡） @author xsy-scm")
    @PostMapping("/finance/voucher/add")
    @SaCheckPermission("financeVoucher:add")
    public ResponseDTO<String> add(@RequestBody @Valid VoucherAddForm addForm) {
        return financeVoucherService.add(addForm);
    }

    @Operation(summary = "作废会计凭证 @author xsy-scm")
    @PostMapping("/finance/voucher/invalidate/{voucherId}")
    @SaCheckPermission("financeVoucher:invalidate")
    public ResponseDTO<String> invalidate(@PathVariable Long voucherId) {
        return financeVoucherService.invalidate(voucherId);
    }

    @Operation(summary = "提交凭证同步 @author xsy-scm")
    @PostMapping("/finance/voucher/push")
    @SaCheckPermission("financeVoucher:push")
    public ResponseDTO<String> push(@RequestBody @Valid VoucherPushForm pushForm) {
        return financeVoucherService.push(pushForm);
    }

    @Operation(summary = "凭证同步结果回执 @author xsy-scm")
    @PostMapping("/finance/voucher/confirmSync")
    @SaCheckPermission("financeVoucher:push")
    public ResponseDTO<String> confirmSync(@RequestBody @Valid VoucherPushConfirmForm confirmForm) {
        return financeVoucherService.confirmSync(confirmForm);
    }

    @Operation(summary = "删除会计凭证 @author xsy-scm")
    @GetMapping("/finance/voucher/delete/{voucherId}")
    @SaCheckPermission("financeVoucher:delete")
    public ResponseDTO<String> delete(@PathVariable Long voucherId) {
        return financeVoucherService.delete(voucherId);
    }

    @Operation(summary = "批量删除会计凭证 @author xsy-scm")
    @PostMapping("/finance/voucher/batchDelete")
    @SaCheckPermission("financeVoucher:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return financeVoucherService.batchDelete(idList);
    }
}
