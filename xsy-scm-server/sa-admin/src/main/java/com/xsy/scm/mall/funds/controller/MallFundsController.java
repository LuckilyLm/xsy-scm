package com.xsy.scm.mall.funds.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.funds.domain.form.MallReceivableQueryForm;
import com.xsy.scm.mall.funds.domain.form.MallWalletMovementQueryForm;
import com.xsy.scm.mall.funds.domain.vo.MallCreditSummaryVO;
import com.xsy.scm.mall.funds.domain.vo.MallReceivableVO;
import com.xsy.scm.mall.funds.domain.vo.MallWalletMovementVO;
import com.xsy.scm.mall.funds.domain.vo.MallWalletSummaryVO;
import com.xsy.scm.mall.funds.service.MallFundsQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/funds")
@RequiredArgsConstructor
public class MallFundsController {
    private final MallFundsQueryService queryService;

    @GetMapping("/wallet/summary")
    public ResponseDTO<MallWalletSummaryVO> walletSummary() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(queryService.walletSummary(MallRequestSupport.current()));
    }

    @GetMapping("/wallet/movements")
    public ResponseDTO<PageResult<MallWalletMovementVO>> walletMovements(@Valid MallWalletMovementQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(queryService.walletMovements(MallRequestSupport.current(), form));
    }

    @GetMapping("/credit/summary")
    public ResponseDTO<MallCreditSummaryVO> creditSummary() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(queryService.creditSummary(MallRequestSupport.current()));
    }

    @GetMapping("/receivables")
    public ResponseDTO<PageResult<MallReceivableVO>> receivables(@Valid MallReceivableQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(queryService.receivables(MallRequestSupport.current(), form));
    }

    @GetMapping("/receivables/{receivableId}")
    public ResponseDTO<MallReceivableVO> receivable(@PathVariable Long receivableId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(queryService.receivable(MallRequestSupport.current(), receivableId));
    }
}
