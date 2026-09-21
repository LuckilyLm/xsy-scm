package net.lab1024.sa.admin.module.scm.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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

    @GetMapping("/routes/{id}")
    @SaCheckPermission("scm:delivery:route:query")
    public ResponseDTO<DeliveryDetailVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(query.detail(id));
    }

    @GetMapping("/routes/{id}/map")
    @SaCheckPermission("scm:delivery:route:query")
    public ResponseDTO<DeliveryDetailVO> map(@PathVariable Long id) {
        return ResponseDTO.ok(query.detail(id));
    }

    @GetMapping("/routes/{id}/print")
    @SaCheckPermission("scm:delivery:route:print")
    @OperateLog
    public ResponseDTO<DeliveryPrintVO> print(@PathVariable Long id) {
        return ResponseDTO.ok(query.print(id));
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

    @GetMapping("/candidate-orders")
    @SaCheckPermission("scm:delivery:route:query")
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
