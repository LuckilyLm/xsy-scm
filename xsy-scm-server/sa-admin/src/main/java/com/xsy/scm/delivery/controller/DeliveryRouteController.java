package com.xsy.scm.delivery.controller;

import com.xsy.scm.delivery.permission.DeliveryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import com.xsy.scm.delivery.service.DeliveryCandidateOrderQueryService;
import com.xsy.scm.delivery.service.DeliveryDriverService;
import com.xsy.scm.delivery.service.DeliveryRouteQueryService;
import com.xsy.scm.delivery.service.DeliveryRouteService;
import com.xsy.scm.delivery.service.DeliveryVehicleService;
import com.xsy.scm.delivery.domain.form.DeliveryOrdersForm;
import com.xsy.scm.delivery.domain.form.DeliveryPrintCustomersForm;
import com.xsy.scm.delivery.domain.form.DeliveryPrintOrdersForm;
import com.xsy.scm.delivery.domain.form.DeliveryQueryForm;
import com.xsy.scm.delivery.domain.form.DeliveryReorderForm;
import com.xsy.scm.delivery.domain.form.DeliveryRouteForm;
import com.xsy.scm.delivery.domain.form.DeliverySignForm;
import com.xsy.scm.delivery.domain.form.DeliveryStopForm;
import com.xsy.scm.delivery.domain.form.DeliveryVersionForm;
import com.xsy.scm.delivery.domain.vo.DeliveryCandidateVO;
import com.xsy.scm.delivery.domain.vo.DeliveryCustomerViewVO;
import com.xsy.scm.delivery.domain.vo.DeliveryDetailVO;
import com.xsy.scm.delivery.domain.vo.DeliveryDispatchResultVO;
import com.xsy.scm.delivery.domain.vo.DeliveryOrderViewVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPrintResultVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPrintVO;
import com.xsy.scm.delivery.domain.vo.DeliveryRouteVO;
import com.xsy.scm.delivery.domain.entity.DeliveryDriverEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryVehicleEntity;
import com.xsy.scm.warehouse.service.WarehouseQueryService;
import com.xsy.scm.warehouse.domain.vo.WarehouseVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/delivery")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "SCM配送线路")
public class DeliveryRouteController {
    private final DeliveryRouteService deliveryRouteService;
    private final DeliveryRouteQueryService deliveryRouteQueryService;
    private final DeliveryCandidateOrderQueryService deliveryCandidateOrderQueryService;
    private final DeliveryDriverService deliveryDriverService;
    private final DeliveryVehicleService deliveryVehicleService;
    private final WarehouseQueryService warehouseQueryService;

    @GetMapping("/routes")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<PageResult<DeliveryRouteVO>> list(@Valid @ModelAttribute DeliveryQueryForm form) {
        return ResponseDTO.ok(deliveryRouteQueryService.query(form));
    }

    @GetMapping("/routes/{id}")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<DeliveryDetailVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(deliveryRouteQueryService.detail(id));
    }

    @GetMapping("/routes/{id}/map")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<DeliveryDetailVO> map(@PathVariable Long id) {
        return ResponseDTO.ok(deliveryRouteQueryService.detail(id));
    }

    @GetMapping("/routes/{id}/print")
    @SaCheckPermission(DeliveryPermission.ROUTE_PRINT)
    @OperateLog
    public ResponseDTO<DeliveryPrintVO> print(@PathVariable Long id) {
        return ResponseDTO.ok(deliveryRouteQueryService.print(id));
    }

    @GetMapping("/routes/{id}/orders-view")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<List<DeliveryOrderViewVO>> ordersView(@PathVariable Long id) {
        return ResponseDTO.ok(deliveryRouteQueryService.orderView(id));
    }

    @GetMapping("/routes/{id}/customers-view")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<List<DeliveryCustomerViewVO>> customersView(@PathVariable Long id) {
        return ResponseDTO.ok(deliveryRouteQueryService.customerView(id));
    }

    // 正式生成入口统一走 POST 并带 Idempotency-Key；GET /print 只预览不计次。
    @PostMapping("/routes/{id}/print/orders")
    @SaCheckPermission(DeliveryPermission.ROUTE_PRINT)
    @OperateLog
    public ResponseDTO<DeliveryPrintResultVO> printOrders(@PathVariable Long id,
            @Valid @RequestBody DeliveryPrintOrdersForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(deliveryRouteService.printOrders(id, form, key));
    }

    @PostMapping("/routes/{id}/print/customers")
    @SaCheckPermission(DeliveryPermission.ROUTE_PRINT)
    @OperateLog
    public ResponseDTO<DeliveryPrintResultVO> printCustomers(@PathVariable Long id,
            @Valid @RequestBody DeliveryPrintCustomersForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(deliveryRouteService.printCustomers(id, form, key));
    }

    @PostMapping("/routes")
    @SaCheckPermission(DeliveryPermission.ROUTE_ADD)
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody DeliveryRouteForm form) {
        return ResponseDTO.ok(deliveryRouteService.create(form));
    }

    @PutMapping("/routes/{id}")
    @SaCheckPermission(DeliveryPermission.ROUTE_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@PathVariable Long id, @Valid @RequestBody DeliveryRouteForm form) {
        deliveryRouteService.update(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/routes/{id}/plan")
    @SaCheckPermission(DeliveryPermission.ROUTE_PLAN)
    @OperateLog
    public ResponseDTO<String> plan(@PathVariable Long id, @Valid @RequestBody DeliveryVersionForm form) {
        deliveryRouteService.plan(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/routes/{id}/cancel")
    @SaCheckPermission(DeliveryPermission.ROUTE_CANCEL)
    @OperateLog
    public ResponseDTO<String> cancel(@PathVariable Long id, @Valid @RequestBody DeliveryVersionForm form) {
        deliveryRouteService.cancel(id, form);
        return ResponseDTO.ok();
    }

    // 发车会产生库存事实，因此必须带 Idempotency-Key：重复请求回放原结果，不重复扣库存。
    @PostMapping("/routes/{id}/dispatch")
    @SaCheckPermission(DeliveryPermission.ROUTE_DISPATCH)
    @OperateLog
    public ResponseDTO<DeliveryDispatchResultVO> dispatch(@PathVariable Long id,
            @Valid @RequestBody DeliveryVersionForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(deliveryRouteService.dispatch(id, form, key));
    }

    // 签收落在订单上而不是线路上，因此权限点也是订单维度；司机维度收窄在服务层按读侧同源判定。
    @PostMapping("/routes/{id}/orders/{orderId}/sign")
    @SaCheckPermission(DeliveryPermission.ORDER_SIGN)
    @OperateLog
    public ResponseDTO<String> sign(@PathVariable Long id, @PathVariable Long orderId,
            @Valid @RequestBody DeliverySignForm form) {
        deliveryRouteService.sign(id, orderId, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/routes/{id}/complete")
    @SaCheckPermission(DeliveryPermission.ROUTE_COMPLETE)
    @OperateLog
    public ResponseDTO<String> complete(@PathVariable Long id, @Valid @RequestBody DeliveryVersionForm form) {
        deliveryRouteService.complete(id, form);
        return ResponseDTO.ok();
    }

    // 候选池是「尚未分配」的订单 + 客户地址电话，只有调度/规划岗可查；司机即使有线路查询权也拿不到。
    // 服务层还要再判一次组单权 + 授权仓库：那才是权威判定，本注解只是把无权请求挡在接口边界。
    @GetMapping("/candidate-orders")
    @SaCheckPermission(DeliveryPermission.ROUTE_PLAN)
    public ResponseDTO<PageResult<DeliveryCandidateVO>> candidates(@Valid @ModelAttribute DeliveryQueryForm form) {
        return ResponseDTO.ok(deliveryCandidateOrderQueryService.query(form));
    }

    @PostMapping("/routes/{id}/orders")
    @SaCheckPermission(DeliveryPermission.ROUTE_UPDATE)
    @OperateLog
    public ResponseDTO<String> add(@PathVariable Long id, @Valid @RequestBody DeliveryOrdersForm form) {
        deliveryRouteService.addOrders(id, form);
        return ResponseDTO.ok();
    }

    @DeleteMapping("/routes/{id}/orders/{orderId}")
    @SaCheckPermission(DeliveryPermission.ROUTE_UPDATE)
    @OperateLog
    public ResponseDTO<String> remove(@PathVariable Long id, @PathVariable Long orderId,
            @Valid @RequestBody DeliveryVersionForm form) {
        deliveryRouteService.removeOrder(id, orderId, form);
        return ResponseDTO.ok();
    }

    @PutMapping("/routes/{id}/stops/reorder")
    @SaCheckPermission(DeliveryPermission.ROUTE_UPDATE)
    @OperateLog
    public ResponseDTO<String> reorder(@PathVariable Long id, @Valid @RequestBody DeliveryReorderForm form) {
        deliveryRouteService.reorder(id, form);
        return ResponseDTO.ok();
    }

    @PutMapping("/routes/{id}/stops/{stopId}")
    @SaCheckPermission(DeliveryPermission.ROUTE_UPDATE)
    @OperateLog
    public ResponseDTO<String> locate(@PathVariable Long id, @PathVariable Long stopId,
            @Valid @RequestBody DeliveryStopForm form) {
        deliveryRouteService.locate(id, stopId, form);
        return ResponseDTO.ok();
    }

    // Route selectors share route-query permission, avoiding an implicit dependency on master-data menus.
    @GetMapping("/options/drivers")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<List<DeliveryDriverEntity>> driverOptions() {
        return ResponseDTO.ok(deliveryDriverService.options());
    }

    @GetMapping("/options/vehicles")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<List<DeliveryVehicleEntity>> vehicleOptions() {
        return ResponseDTO.ok(deliveryVehicleService.options());
    }

    @GetMapping("/options/warehouses")
    @SaCheckPermission(DeliveryPermission.ROUTE_QUERY)
    public ResponseDTO<List<WarehouseVO>> warehouseOptions() {
        return ResponseDTO.ok(warehouseQueryService.list());
    }
}
