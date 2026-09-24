package net.lab1024.sa.admin.module.scm.order.controller;

import net.lab1024.sa.admin.module.scm.order.service.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/order")
public class SalesOrderController {
    private final SalesOrderService service;
    private final SalesOrderQueryService query;
    private final net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver prices;

    @PostMapping("/query")
    @SaCheckPermission("scm:order:query")
    public ResponseDTO<PageResult<SalesOrderVO>> query(@Valid @RequestBody SalesOrderQueryForm f) {
        return ResponseDTO.ok(query.query(f));
    }

    @GetMapping("/detail/{orderId}")
    @SaCheckPermission("scm:order:query")
    public ResponseDTO<SalesOrderDetailVO> detail(@PathVariable Long orderId) {
        return ResponseDTO.ok(query.detail(orderId));
    }

    @PostMapping("/log/query")
    @SaCheckPermission("scm:order:log:query")
    public ResponseDTO<PageResult<OrderOperationLogVO>> logs(@Valid @RequestBody OrderLogQueryForm f) {
        return ResponseDTO.ok(query.logs(f));
    }

    /**
     * 录单时的价格解析预览。
     *
     * <p>返回体是定价域的 {@code PriceResolveResultVO}，与 {@code PriceResolveController#preview} 调用
     * 同一个 {@link net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver#preview}，因此<b>同时</b>要求
     * {@code scm:order:query} 与 {@code scm:pricing:resolve:query}（{@link SaMode#AND}）：只有订单查看权的人
     * 不能经此旁路批量读到客户协议价与类型价解析结果，那本来需要单独的定价查看权。
     */
    @PostMapping("/price/preview")
    @SaCheckPermission(value = {"scm:order:query", "scm:pricing:resolve:query"}, mode = SaMode.AND)
    public ResponseDTO<net.lab1024.sa.admin.module.scm.pricing.domain.vo.PriceResolveResultVO> preview(@Valid @RequestBody net.lab1024.sa.admin.module.scm.pricing.domain.form.PriceResolveForm f) {
        return ResponseDTO.ok(prices.preview(f.getCustomerId(), f.getSkuIds(), f.getAt()));
    }

    /**
     * 某客户某 SKU 的最近已确认订单价（Wave 3 §7.5，只读）：仅取 CONFIRMED 单的锁定价，只用于录单旁证，不参与定价、不改价格优先级。
     *
     * <p>取数源是指定客户的历史成交事实，与客户 360 的 {@code frequent-skus} 同数据面，因此门禁口径一致：
     * <b>同时</b>要求 {@code scm:order:query} 与 {@code scm:customer:query}（{@link SaMode#AND}）。
     */
    @GetMapping("/reference/recent-prices")
    @SaCheckPermission(value = {"scm:order:query", "scm:customer:query"}, mode = SaMode.AND)
    public ResponseDTO<List<OrderRecentPriceVO>> recentPrices(@RequestParam Long customerId,
                                                              @RequestParam Long skuId,
                                                              @RequestParam(defaultValue = "5") int limit) {
        return ResponseDTO.ok(query.recentPrices(customerId, skuId, limit));
    }

    @PostMapping("/create")
    @SaCheckPermission("scm:order:add")
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> create(@Valid @RequestBody SalesOrderAddForm f, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        overridePermission(f);
        return ResponseDTO.ok(service.create(f, key));
    }

    @PostMapping("/create-and-progress")
    @SaCheckPermission("scm:order:add")
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> createAndProgress(@Valid @RequestBody SalesOrderAddForm f, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        overridePermission(f);
        return ResponseDTO.ok(service.createAndProgress(f, key));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:order:update")
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> update(@Valid @RequestBody SalesOrderUpdateForm f) {
        overridePermission(f);
        return ResponseDTO.ok(service.update(f));
    }

    @PostMapping("/submit")
    @SaCheckPermission("scm:order:submit")
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> submit(@Valid @RequestBody OrderVersionForm f, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.submit(f, key));
    }

    @PostMapping("/confirm")
    @SaCheckPermission("scm:order:confirm")
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> confirm(@Valid @RequestBody OrderVersionForm f, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.confirm(f, key));
    }

    @PostMapping("/cancel")
    @SaCheckPermission("scm:order:cancel")
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> cancel(@Valid @RequestBody OrderCancelForm f, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.cancel(f, key));
    }

    @PostMapping("/item/actual-quantity")
    @SaCheckPermission("scm:order:actual-quantity")
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> actual(@Valid @RequestBody OrderActualQuantityForm f, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.actualQuantity(f, key));
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:order:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody OrderVersionForm f) {
        service.delete(f);
        return ResponseDTO.ok();
    }

    @PostMapping("/batch-delete")
    @SaCheckPermission("scm:order:delete")
    @OperateLog
    public ResponseDTO<String> batchDelete(@Valid @RequestBody OrderBatchDeleteForm f) {
        service.batchDelete(f);
        return ResponseDTO.ok();
    }

    /**
     * 为已确认的订单预留库存（出库波次）。
     *
     * <p>显式操作而非确认时自动预留：本业务的库存在订单确认之后才产生，
     * 把预留挂在确认上会让「先接单→再采购」链路无法运转（见 docs/decisions.md）。
     * 货到之后由业务人员对本单执行预留，占用可用量。
     */
    @PostMapping("/reserve-stock/{orderId}")
    @SaCheckPermission("scm:order:reserve-stock")
    @OperateLog
    public ResponseDTO<String> reserveStock(@PathVariable Long orderId) {
        service.reserveStock(orderId);
        return ResponseDTO.ok();
    }

    private void overridePermission(SalesOrderAddForm f) {
        if (!java.util.Set.of("ADMIN", "SUPPLEMENT").contains(f.getOrderSource()))
            throw new net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException(net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.ORDER_SOURCE_INVALID);
        if (f.getItems().stream().anyMatch(x -> Boolean.TRUE.equals(x.getManualPriceOverride())))
            cn.dev33.satoken.stp.StpUtil.checkPermission("scm:order:price-override");
    }
}
