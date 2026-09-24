package net.lab1024.sa.admin.module.scm.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import net.lab1024.sa.admin.module.scm.delivery.service.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseQueryService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseVO;
import net.lab1024.sa.base.common.domain.*;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/delivery")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "SCM配送线路")
public class DeliveryRouteController {
    private final DeliveryRouteService service;
    private final DeliveryRouteQueryService query;
    private final DeliveryCandidateOrderQueryService candidates;
    private final DeliveryDriverService drivers;
    private final DeliveryVehicleService vehicles;
    private final WarehouseQueryService warehouses;

    @GetMapping("/routes")
    @SaCheckPermission("scm:delivery:route:query")
    public ResponseDTO<PageResult<DeliveryRouteVO>> list(@Valid @ModelAttribute DeliveryQueryForm form) {
        return ResponseDTO.ok(query.query(form));
    }

    /**
     * 线路详情：返回体里 {@code orders} 是 {@code DeliveryRouteOrderEntity} 清单，含 {@code orderNoSnapshot}、
     * {@code customerId}、{@code orderAmountSnapshot}，前端线路明细表直接渲染金额列——只有配送权的人经此
     * 就能读到订单事实，故与 orders-view / customers-view 同口径，<b>同时</b>要求
     * {@code scm:delivery:route:query} 与 {@code scm:order:query}（{@link SaMode#AND}）。
     * 权限种子目前只建了 role 1，而它两条都有，所以收紧不影响任何现有账号。
     */
    @GetMapping("/routes/{id}")
    @SaCheckPermission(value = {"scm:delivery:route:query", "scm:order:query"}, mode = SaMode.AND)
    public ResponseDTO<DeliveryDetailVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(query.detail(id));
    }

    /**
     * 线路地图视图：与 {@link #detail} 同一份返回体，门禁必须一致，否则换个路径就能读到订单金额。
     */
    @GetMapping("/routes/{id}/map")
    @SaCheckPermission(value = {"scm:delivery:route:query", "scm:order:query"}, mode = SaMode.AND)
    public ResponseDTO<DeliveryDetailVO> map(@PathVariable Long id) {
        return ResponseDTO.ok(query.detail(id));
    }

    /**
     * 打印预览：除线路本身外还吐出订单明细行（含锁定价、结算额与改价原因）。
     *
     * <p>因此<b>同时</b>要求 {@code scm:delivery:route:print} 与 {@code scm:order:query}（{@link SaMode#AND}）：
     * 只有打印权的人不能经此读到订单价格口径。
     */
    @GetMapping("/routes/{id}/print")
    @SaCheckPermission(value = {"scm:delivery:route:print", "scm:order:query"}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<DeliveryPrintVO> print(@PathVariable Long id) {
        return ResponseDTO.ok(query.print(id));
    }

    /**
     * 按订单视角看线路：返回订单号、客户名、收货地址与订单金额，取数源是订单事实。
     */
    @GetMapping("/routes/{id}/orders-view")
    @SaCheckPermission(value = {"scm:delivery:route:query", "scm:order:query"}, mode = SaMode.AND)
    public ResponseDTO<List<DeliveryOrderViewVO>> ordersView(@PathVariable Long id) {
        return ResponseDTO.ok(query.orderView(id));
    }

    /**
     * 按客户视角看线路：跨订单聚合的下单笔数与合计金额，同为订单事实。
     */
    @GetMapping("/routes/{id}/customers-view")
    @SaCheckPermission(value = {"scm:delivery:route:query", "scm:order:query"}, mode = SaMode.AND)
    public ResponseDTO<List<DeliveryCustomerViewVO>> customersView(@PathVariable Long id) {
        return ResponseDTO.ok(query.customerView(id));
    }

    // 正式生成入口统一走 POST 并带 Idempotency-Key；GET /print 只预览不计次。
    // 两个正式打印入口的返回体带 DeliveryOrderViewVO 清单，价格与收货地址面与预览同型，门禁口径一并收紧。
    @PostMapping("/routes/{id}/print/orders")
    @SaCheckPermission(value = {"scm:delivery:route:print", "scm:order:query"}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<DeliveryPrintResultVO> printOrders(@PathVariable Long id, @Valid @RequestBody DeliveryPrintOrdersForm form,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.printOrders(id, form, key));
    }

    @PostMapping("/routes/{id}/print/customers")
    @SaCheckPermission(value = {"scm:delivery:route:print", "scm:order:query"}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<DeliveryPrintResultVO> printCustomers(@PathVariable Long id, @Valid @RequestBody DeliveryPrintCustomersForm form,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.printCustomers(id, form, key));
    }

    @PostMapping("/routes")
    @SaCheckPermission("scm:delivery:route:add")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody DeliveryRouteForm form) {
        return ResponseDTO.ok(service.create(form));
    }

    @PutMapping("/routes/{id}")
    @SaCheckPermission("scm:delivery:route:update")
    @OperateLog
    public ResponseDTO<String> update(@PathVariable Long id, @Valid @RequestBody DeliveryRouteForm form) {
        service.update(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/routes/{id}/plan")
    @SaCheckPermission("scm:delivery:route:plan")
    @OperateLog
    public ResponseDTO<String> plan(@PathVariable Long id, @Valid @RequestBody DeliveryVersionForm form) {
        service.plan(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/routes/{id}/cancel")
    @SaCheckPermission("scm:delivery:route:cancel")
    @OperateLog
    public ResponseDTO<String> cancel(@PathVariable Long id, @Valid @RequestBody DeliveryVersionForm form) {
        service.cancel(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 待选订单列表：等价于一个带完整检索条件（订单号 / 客户 / 地址关键词 / 金额区间 / 时间区间）的订单列表页，
     * 且继承 {@code OrderAddressSnapshotVO} 输出收货人姓名、电话与地址。
     *
     * <p>因此<b>同时</b>要求 {@code scm:delivery:route:query} 与 {@code scm:order:query}（{@link SaMode#AND}）：
     * 只有线路查看权的人不能经此读到他人订单的收货人明细与金额。
     */
    @GetMapping("/candidate-orders")
    @SaCheckPermission(value = {"scm:delivery:route:query", "scm:order:query"}, mode = SaMode.AND)
    public ResponseDTO<PageResult<DeliveryCandidateVO>> candidates(@Valid @ModelAttribute DeliveryQueryForm form) {
        return ResponseDTO.ok(candidates.query(form));
    }

    @PostMapping("/routes/{id}/orders")
    @SaCheckPermission("scm:delivery:route:update")
    @OperateLog
    public ResponseDTO<String> add(@PathVariable Long id, @Valid @RequestBody DeliveryOrdersForm form) {
        service.addOrders(id, form);
        return ResponseDTO.ok();
    }

    @DeleteMapping("/routes/{id}/orders/{orderId}")
    @SaCheckPermission("scm:delivery:route:update")
    @OperateLog
    public ResponseDTO<String> remove(@PathVariable Long id, @PathVariable Long orderId, @Valid @RequestBody DeliveryVersionForm form) {
        service.removeOrder(id, orderId, form);
        return ResponseDTO.ok();
    }

    @PutMapping("/routes/{id}/stops/reorder")
    @SaCheckPermission("scm:delivery:route:update")
    @OperateLog
    public ResponseDTO<String> reorder(@PathVariable Long id, @Valid @RequestBody DeliveryReorderForm form) {
        service.reorder(id, form);
        return ResponseDTO.ok();
    }

    @PutMapping("/routes/{id}/stops/{stopId}")
    @SaCheckPermission("scm:delivery:route:update")
    @OperateLog
    public ResponseDTO<String> locate(@PathVariable Long id, @PathVariable Long stopId, @Valid @RequestBody DeliveryStopForm form) {
        service.locate(id, stopId, form);
        return ResponseDTO.ok();
    }

    // Route selectors share route-query permission, avoiding an implicit dependency on master-data menus.
    @GetMapping("/options/drivers")
    @SaCheckPermission("scm:delivery:route:query")
    public ResponseDTO<List<DeliveryDriverEntity>> driverOptions() {
        return ResponseDTO.ok(drivers.options());
    }

    @GetMapping("/options/vehicles")
    @SaCheckPermission("scm:delivery:route:query")
    public ResponseDTO<List<DeliveryVehicleEntity>> vehicleOptions() {
        return ResponseDTO.ok(vehicles.options());
    }

    @GetMapping("/options/warehouses")
    @SaCheckPermission("scm:delivery:route:query")
    public ResponseDTO<List<WarehouseVO>> warehouseOptions() {
        return ResponseDTO.ok(warehouses.list());
    }
}
