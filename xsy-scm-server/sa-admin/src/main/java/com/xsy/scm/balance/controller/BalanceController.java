package com.xsy.scm.balance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.balance.constant.ScmBalancePermission;
import com.xsy.scm.balance.domain.form.BalanceCorrectionForm;
import com.xsy.scm.balance.domain.form.BalanceMovementQueryForm;
import com.xsy.scm.balance.domain.form.BalanceQueryForm;
import com.xsy.scm.balance.domain.vo.BalanceMovementVO;
import com.xsy.scm.balance.domain.vo.CustomerBalanceVO;
import com.xsy.scm.balance.service.CustomerBalanceQueryService;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.balance.support.BalanceVoAssembler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户余额：概览、流水、人工更正。
 *
 * <p>
 * <b>没有「编辑余额」接口</b>：更正也是追加一条流水，历史不可改（库上 append-only）。
 */
@RestController
@RequestMapping("/scm/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final CustomerBalanceService customerBalanceService;

    private final CustomerBalanceQueryService customerBalanceQueryService;

    /** 余额概览：服务端解析结算主体，不接受客户端指定钱包账户 id。 */
    @PostMapping("/query")
    @SaCheckPermission(ScmBalancePermission.QUERY)
    public ResponseDTO<CustomerBalanceVO> query(@Valid @RequestBody BalanceQueryForm form) {
        return ResponseDTO.ok(customerBalanceQueryService.balanceView(form));
    }

    @PostMapping("/movement/query")
    @SaCheckPermission(ScmBalancePermission.MOVEMENT_QUERY)
    public ResponseDTO<PageResult<BalanceMovementVO>> movementQuery(
            @Valid @RequestBody BalanceMovementQueryForm form) {
        return ResponseDTO.ok(customerBalanceQueryService.movementPage(form));
    }

    /**
     * 人工更正余额。
     *
     * <p>
     * 动的是客户的钱，因此要求独立权限 + 方向 + 金额 + 原因 + {@code Idempotency-Key}
     * （重复提交的代价是真金白银）+ 操作人（落流水）。
     */
    @PostMapping("/correction")
    @SaCheckPermission(ScmBalancePermission.CORRECTION)
    @OperateLog
    public ResponseDTO<BalanceMovementVO> correction(@Valid @RequestBody BalanceCorrectionForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(BalanceVoAssembler.toMovement(customerBalanceService.correct(form, key)));
    }
}
