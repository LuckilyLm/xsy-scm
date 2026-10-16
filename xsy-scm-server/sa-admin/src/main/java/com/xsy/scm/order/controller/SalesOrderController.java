package com.xsy.scm.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.domain.vo.CustomerCreditCheckVO;
import com.xsy.scm.order.service.OrderCreditService;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderBatchDeleteForm;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.domain.form.OrderConfirmForm;
import com.xsy.scm.order.domain.form.OrderLogQueryForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.domain.form.SalesOrderUpdateForm;
import com.xsy.scm.order.domain.vo.OrderOperationLogVO;
import com.xsy.scm.order.domain.vo.OrderRecentPriceVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.PriceResolver;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/order")
public class SalesOrderController {
    private final SalesOrderService salesOrderService;
    private final SalesOrderQueryService salesOrderQueryService;
    private final PriceResolver priceResolver;
    private final OrderCreditService orderCreditService;
    private final CustomerService customerService;

    @PostMapping("/query")
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<PageResult<SalesOrderVO>> query(@Valid @RequestBody SalesOrderQueryForm salesOrderQueryForm) {
        return ResponseDTO.ok(salesOrderQueryService.query(salesOrderQueryForm));
    }

    @GetMapping("/detail/{orderId}")
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<SalesOrderDetailVO> detail(@PathVariable Long orderId) {
        return ResponseDTO.ok(salesOrderQueryService.detail(orderId));
    }

    @PostMapping("/log/query")
    @SaCheckPermission(OrderPermission.LOG_QUERY)
    public ResponseDTO<PageResult<OrderOperationLogVO>> logs(@Valid @RequestBody OrderLogQueryForm orderLogQueryForm) {
        return ResponseDTO.ok(salesOrderQueryService.logs(orderLogQueryForm));
    }

    /** 订单录入时的价格预览复用定价服务。调用者必须同时具备订单查询和价格查询权限，客户也必须属于当前数据范围。 */
    @PostMapping("/price/preview")
    @SaCheckPermission(value = {OrderPermission.QUERY, PricingPermission.RESOLVE_QUERY}, mode = SaMode.AND)
    public ResponseDTO<com.xsy.scm.pricing.domain.vo.PriceResolveResultVO> preview(
            @Valid @RequestBody com.xsy.scm.pricing.domain.form.PriceResolveForm priceResolveForm) {
        customerService.requireInCurrentScope(priceResolveForm.getCustomerId());
        return ResponseDTO.ok(priceResolver.preview(priceResolveForm.getCustomerId(), priceResolveForm.getSkuIds(),
                priceResolveForm.getAt()));
    }

    /** 最近成交价只参考该客户已确认订单的锁定价格，不参与当前商品定价。查询必须同时满足订单和客户权限。 */
    @GetMapping("/reference/recent-prices")
    @SaCheckPermission(value = {OrderPermission.QUERY, CustomerPermission.QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<OrderRecentPriceVO>> recentPrices(@RequestParam Long customerId, @RequestParam Long skuId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseDTO.ok(salesOrderQueryService.recentPrices(customerId, skuId, limit));
    }

    @PostMapping("/create")
    @SaCheckPermission(OrderPermission.ADD)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> create(@Valid @RequestBody SalesOrderAddForm salesOrderAddForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        overridePermission(salesOrderAddForm);
        return ResponseDTO.ok(salesOrderService.create(salesOrderAddForm, key));
    }

    @PostMapping("/create-and-progress")
    @SaCheckPermission(OrderPermission.ADD)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> createAndProgress(@Valid @RequestBody SalesOrderAddForm salesOrderAddForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        overridePermission(salesOrderAddForm);
        return ResponseDTO.ok(salesOrderService.createAndProgress(salesOrderAddForm, key));
    }

    @PostMapping("/update")
    @SaCheckPermission(OrderPermission.UPDATE)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> update(@Valid @RequestBody SalesOrderUpdateForm salesOrderUpdateForm) {
        overridePermission(salesOrderUpdateForm);
        return ResponseDTO.ok(salesOrderService.update(salesOrderUpdateForm));
    }

    @PostMapping("/submit")
    @SaCheckPermission(OrderPermission.SUBMIT)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> submit(@Valid @RequestBody OrderVersionForm orderVersionForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(salesOrderService.submit(orderVersionForm, key));
    }

    @GetMapping("/credit-check/{customerId}")
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<CustomerCreditCheckVO> creditCheck(@PathVariable Long customerId,
            @RequestParam(required = false) java.math.BigDecimal requestedAmount) {
        return ResponseDTO.ok(orderCreditService.check(customerId, requestedAmount));
    }

    @GetMapping("/credit-check/order/{orderId}")
    @SaCheckPermission(OrderPermission.CONFIRM)
    public ResponseDTO<CustomerCreditCheckVO> orderCreditCheck(@PathVariable Long orderId) {
        return ResponseDTO.ok(salesOrderService.creditCheck(orderId));
    }

    @PostMapping("/confirm")
    @SaCheckPermission(OrderPermission.CONFIRM)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> confirm(@Valid @RequestBody OrderConfirmForm orderVersionForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(salesOrderService.confirm(orderVersionForm, key));
    }

    @PostMapping("/cancel")
    @SaCheckPermission(OrderPermission.CANCEL)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> cancel(@Valid @RequestBody OrderCancelForm orderCancelForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(salesOrderService.cancel(orderCancelForm, key));
    }

    @PostMapping("/item/actual-quantity")
    @SaCheckPermission(OrderPermission.ACTUAL_QUANTITY)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> actual(@Valid @RequestBody OrderActualQuantityForm orderActualQuantityForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(salesOrderService.actualQuantity(orderActualQuantityForm, key));
    }

    @PostMapping("/delete")
    @SaCheckPermission(OrderPermission.DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody OrderVersionForm orderVersionForm) {
        salesOrderService.delete(orderVersionForm);
        return ResponseDTO.ok();
    }

    @PostMapping("/batch-delete")
    @SaCheckPermission(OrderPermission.DELETE)
    @OperateLog
    public ResponseDTO<String> batchDelete(@Valid @RequestBody OrderBatchDeleteForm batchDeleteForm) {
        salesOrderService.batchDelete(batchDeleteForm);
        return ResponseDTO.ok();
    }

    /**
     * 为已确认的订单预留库存。
     *
     * <p>
     * 显式操作而非确认时自动预留：本业务的库存在订单确认之后才产生，把预留挂在确认上会让「先接单→再采购」链路无法运转（见 docs/decisions.md）。货到之后由业务人员对本单执行预留，占用可用量。
     */
    @PostMapping("/reserve-stock/{orderId}")
    @SaCheckPermission(OrderPermission.RESERVE_STOCK)
    @OperateLog
    public ResponseDTO<String> reserveStock(@PathVariable Long orderId) {
        salesOrderService.reserveStock(orderId);
        return ResponseDTO.ok();
    }

    private void overridePermission(SalesOrderAddForm salesOrderForm) {
        if (!java.util.Set.of(ScmOrderSourceEnum.ADMIN.name(), ScmOrderSourceEnum.SUPPLEMENT.name())
                .contains(salesOrderForm.getOrderSource()))
            throw new ScmBusinessException(OrderErrorCode.ORDER_SOURCE_INVALID);
        if (salesOrderForm.getItems().stream()
                .anyMatch(orderItemForm -> Boolean.TRUE.equals(orderItemForm.getManualPriceOverride())))
            StpUtil.checkPermission(OrderPermission.PRICE_OVERRIDE);
    }
}
