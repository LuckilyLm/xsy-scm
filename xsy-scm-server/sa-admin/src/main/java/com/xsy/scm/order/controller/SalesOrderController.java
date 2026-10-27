package com.xsy.scm.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
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
import com.xsy.scm.order.domain.form.OrderLastPurchasePriceQueryForm;
import com.xsy.scm.order.domain.form.OrderLockForm;
import com.xsy.scm.order.domain.form.OrderLogQueryForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.OrderRepriceForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.domain.form.SalesOrderUpdateForm;
import com.xsy.scm.order.domain.vo.OrderLastPurchasePriceVO;
import com.xsy.scm.order.domain.vo.OrderOperationLogVO;
import com.xsy.scm.order.domain.vo.OrderRecentPriceVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.OrderLockService;
import com.xsy.scm.order.service.SalesOrderDetailReadService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.report.constant.ScmReportPermission;
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
    private final OrderLockService orderLockService;
    private final SalesOrderQueryService salesOrderQueryService;
    private final SalesOrderDetailReadService salesOrderDetailReadService;
    private final PriceResolver priceResolver;
    private final OrderCreditService orderCreditService;
    private final CustomerService customerService;
    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    @PostMapping("/reprice")
    @SaCheckPermission(value = {OrderPermission.UPDATE, PricingPermission.RESOLVE_QUERY}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> reprice(@Valid @RequestBody OrderRepriceForm form) {
        return ResponseDTO.ok(salesOrderService.reprice(form));
    }

    @PostMapping("/query")
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<PageResult<SalesOrderVO>> query(@Valid @RequestBody SalesOrderQueryForm salesOrderQueryForm) {
        return ResponseDTO.ok(salesOrderQueryService.query(salesOrderQueryForm));
    }

    @GetMapping("/detail/{orderId}")
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<SalesOrderDetailVO> detail(@PathVariable Long orderId) {
        return ResponseDTO.ok(salesOrderDetailReadService.detail(orderId));
    }

    @PostMapping("/log/query")
    @SaCheckPermission(OrderPermission.LOG_QUERY)
    public ResponseDTO<PageResult<OrderOperationLogVO>> logs(@Valid @RequestBody OrderLogQueryForm orderLogQueryForm) {
        return ResponseDTO.ok(salesOrderQueryService.logs(orderLogQueryForm));
    }

    /**
     * 订单录入时的价格预览复用定价服务。调用者必须同时具备订单查询和价格查询权限，客户也必须属于当前数据范围。
     */
    @PostMapping("/price/preview")
    @SaCheckPermission(value = {OrderPermission.QUERY, PricingPermission.RESOLVE_QUERY}, mode = SaMode.AND)
    public ResponseDTO<com.xsy.scm.pricing.domain.vo.PriceResolveResultVO> preview(
        @Valid @RequestBody com.xsy.scm.pricing.domain.form.PriceResolveForm priceResolveForm) {
        customerService.requireInCurrentScope(priceResolveForm.getCustomerId());
        if (priceResolveForm.getWarehouseId() != null) {
            warehouseScopeGuard.require(priceResolveForm.getWarehouseId());
        }
        return ResponseDTO.ok(priceResolver.preview(priceResolveForm.getCustomerId(), priceResolveForm.getSkuIds(),
            priceResolveForm.getAt(), priceResolveForm.getWarehouseId()));
    }

    /**
     * 最近成交价只参考该客户已确认订单的锁定价格，不参与当前商品定价。查询必须同时满足订单和客户权限。
     */
    @GetMapping("/reference/recent-prices")
    @SaCheckPermission(value = {OrderPermission.QUERY, CustomerPermission.QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<OrderRecentPriceVO>> recentPrices(@RequestParam Long customerId, @RequestParam Long skuId,
        @RequestParam(defaultValue = "5") int limit) {
        return ResponseDTO.ok(salesOrderQueryService.recentPrices(customerId, skuId, limit));
    }

    /**
     * 最近采购价只作为录单参考，不参与当前商品定价。查询必须同时具备订单查询和成本查看权限； 取数再按采购员数据范围收窄，范围为空时失败关闭。
     */
    @GetMapping("/reference/last-purchase-prices")
    @SaCheckPermission(value = {OrderPermission.QUERY, ScmReportPermission.COST_QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<OrderLastPurchasePriceVO>> lastPurchasePrices(
        @Valid OrderLastPurchasePriceQueryForm queryForm) {
        return ResponseDTO.ok(salesOrderQueryService.lastPurchasePrices(queryForm.getSkuIds()));
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
     * 预留库存是显式操作：本业务的库存通常在订单确认之后才到货，若在确认时自动预留，「先接单再采购」就无法运转（见 docs/decisions.md）。
     */
    @PostMapping("/reserve-stock/{orderId}")
    @SaCheckPermission(OrderPermission.RESERVE_STOCK)
    @OperateLog
    public ResponseDTO<String> reserveStock(@PathVariable Long orderId) {
        salesOrderService.reserveStock(orderId);
        return ResponseDTO.ok();
    }

    /**
     * 锁单后订单仍是已确认，只是改单要额外授权；手动锁单与 7 天自动锁单写同一种锁标记。
     */
    @PostMapping("/lock")
    @SaCheckPermission(OrderPermission.LOCK)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> lock(@Valid @RequestBody OrderLockForm orderLockForm) {
        return ResponseDTO.ok(orderLockService.lock(orderLockForm));
    }

    @PostMapping("/unlock")
    @SaCheckPermission(OrderPermission.UNLOCK)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> unlock(@Valid @RequestBody OrderLockForm orderLockForm) {
        return ResponseDTO.ok(orderLockService.unlock(orderLockForm));
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
