package com.xsy.scm.mall.order.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderCancelForm;
import com.xsy.scm.mall.order.domain.form.MallOrderQueryForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSummaryQueryForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSummaryVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.domain.vo.MallReorderVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.mall.order.service.MallOrderService;
import com.xsy.scm.mall.order.service.MallPaymentService;
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
@RequestMapping("/scm/mall/orders")
@RequiredArgsConstructor
public class MallOrderController {
    private final MallCheckoutService checkoutService;
    private final MallOrderQueryService orderQueryService;
    private final MallOrderService orderService;
    private final MallPaymentService paymentService;

    /**
     * 订单列表；{@code displayStatus} 按展示分类筛选，留空为全部
     */
    @GetMapping
    public ResponseDTO<PageResult<MallOrderVO>> list(@Valid MallOrderQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(orderQueryService.list(MallRequestSupport.current(), form));
    }

    /**
     * 下单商品汇总：按商品规格聚合当前客户的历史下单，可按日期区间收窄。
     *
     * <p>
     * 路径是字面量 {@code /summary}，不会被下面的 {@code /{orderId}} 吞掉：Spring 优先匹配精确路径。
     */
    @GetMapping("/summary")
    public ResponseDTO<MallOrderSummaryVO> summary(@Valid MallOrderSummaryQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(orderQueryService.summary(MallRequestSupport.current(), form));
    }

    @GetMapping("/{orderId}")
    public ResponseDTO<MallOrderVO> detail(@PathVariable Long orderId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(orderQueryService.detail(MallRequestSupport.current(), orderId));
    }

    /**
     * 客户取消订单：只有状态机允许取消、且尚未进入配送流程的订单能成功。 必须携带 {@code Idempotency-Key}，重复点击返回首次结果而不是报错。
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseDTO<MallOrderVO> cancel(@PathVariable Long orderId, @Valid @RequestBody MallOrderCancelForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(orderService.cancel(MallRequestSupport.current(), orderId, form, key));
    }

    /**
     * 再来一单：按当前可见性与客户价重新校验后写回购物车，返回加入与跳过明细
     */
    @PostMapping("/{orderId}/reorder")
    public ResponseDTO<MallReorderVO> reorder(@PathVariable Long orderId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(orderService.reorder(MallRequestSupport.current(), orderId));
    }

    /**
     * 余额支付：客户对已确认且约定余额结算的订单主动发起，扣款金额由服务端按正式算法计算（应付净额 − 已付），客户端不传金额。 必须携带
     * {@code Idempotency-Key}：首笔提交后待付归零，重复点击落到「订单已支付」，不会二次扣款。
     */
    @PostMapping("/{orderId}/pay")
    public ResponseDTO<MallOrderVO> pay(@PathVariable Long orderId,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(paymentService.pay(MallRequestSupport.current(), orderId, key));
    }

    /**
     * 结算预览：返回明细、预估金额与价格指纹；不占用库存、不建单
     */
    @PostMapping("/preview")
    public ResponseDTO<MallCheckoutPreviewVO> preview(@Valid @RequestBody MallCheckoutPreviewForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(checkoutService.preview(MallRequestSupport.current(), form));
    }

    /**
     * 提交订单：必须携带 {@code Idempotency-Key} 与预览返回的 {@code priceFingerprint}。 服务端重验客户、地址、商品与价格后写入正式销售订单；库存由后台在货到后显式预留。
     */
    @PostMapping
    public ResponseDTO<MallOrderSubmitVO> submit(@Valid @RequestBody MallOrderSubmitForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(checkoutService.submit(MallRequestSupport.current(), form, key));
    }
}
