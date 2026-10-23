package com.xsy.scm.mall.aftersale.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.aftersale.domain.form.MallAfterSaleApplyForm;
import com.xsy.scm.mall.aftersale.domain.form.MallAfterSaleQueryForm;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleVO;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleQueryService;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/after-sales")
@RequiredArgsConstructor
public class MallAfterSaleController {
    private final MallAfterSaleService afterSaleService;
    private final MallAfterSaleQueryService afterSaleQueryService;

    /**
     * 售后单列表；{@code orderId} 只看某一订单的售后，留空为全部
     */
    @GetMapping
    public ResponseDTO<PageResult<MallAfterSaleVO>> list(@Valid MallAfterSaleQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(afterSaleQueryService.list(MallRequestSupport.current(), form));
    }

    @GetMapping("/{returnId}")
    public ResponseDTO<MallAfterSaleVO> detail(@PathVariable Long returnId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(afterSaleQueryService.detail(MallRequestSupport.current(), returnId));
    }

    /**
     * 申请售后：按行选数量，订单状态与可退数量由正式退货建单路径统一校验。携带 {@code Idempotency-Key}，重复提交返回首次结果而不是重复建单。
     */
    @PostMapping
    public ResponseDTO<MallAfterSaleVO> apply(@Valid @RequestBody MallAfterSaleApplyForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(afterSaleService.apply(MallRequestSupport.current(), form, key));
    }

    /**
     * 撤销待审核的售后申请；已进入审核或已终结的申请由状态机拒绝
     */
    @PostMapping("/{returnId}/cancel")
    public ResponseDTO<MallAfterSaleVO> cancel(@PathVariable Long returnId,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(afterSaleService.cancel(MallRequestSupport.current(), returnId, key));
    }
}
