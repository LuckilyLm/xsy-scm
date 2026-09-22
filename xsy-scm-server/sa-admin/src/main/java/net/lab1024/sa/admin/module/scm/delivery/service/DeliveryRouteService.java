package net.lab1024.sa.admin.module.scm.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.delivery.dao.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderDao;
import net.lab1024.sa.admin.module.scm.order.service.OrderIdempotencyService;
import net.lab1024.sa.admin.module.scm.warehouse.dao.WarehouseDao;

import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.*;

/**
 * Route is the aggregate lock. Order rows are locked in ID order before assignment/plan/release.
 */
@Service
@RequiredArgsConstructor
public class DeliveryRouteService {
    // 与只读预览 DeliveryRouteQueryService.print 保持同一可打印状态集合。
    private static final Set<String> PRINTABLE = Set.of("PLANNED", "DISPATCHED", "COMPLETED");
    private final DeliveryRouteDao routes;
    private final DeliveryRouteStopDao stops;
    private final DeliveryRouteOrderDao assignments;
    private final DeliveryQueryDao queries;
    private final DeliveryDriverDao drivers;
    private final DeliveryVehicleDao vehicles;
    private final WarehouseDao warehouses;
    private final SalesOrderDao orders;
    private final DeliveryEligibilityPolicy eligibility;
    private final OrderIdempotencyService idempotency;

    @Transactional(rollbackFor = Exception.class)
    public Long create(DeliveryRouteForm form) {
        var route = new DeliveryRouteEntity();
        applyHeader(route, form);
        route.setRouteNo("DR" + form.getDeliveryDate().format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", queries.nextNumber()));
        route.setStatus("DRAFT");
        stamp(route, true);
        routes.insert(route);
        return route.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DeliveryRouteForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        applyHeader(route, form);
        save(route);
    }

    private void applyHeader(DeliveryRouteEntity route, DeliveryRouteForm form) {
        var warehouse = warehouses.selectById(form.getWarehouseId());
        if (warehouse == null || !"ENABLED".equals(warehouse.getStatus()))
            throw new ScmBusinessException(MASTER_DISABLED);
        route.setRouteName(form.getRouteName().trim());
        route.setDeliveryDate(form.getDeliveryDate());
        route.setWarehouseId(warehouse.getId());
        route.setWarehouseNameSnapshot(warehouse.getName());
        route.setWarehouseAddressSnapshot(warehouse.getAddress());
        route.setStartLongitude(warehouse.getLongitude());
        route.setStartLatitude(warehouse.getLatitude());
        route.setStartGeomCrs(warehouse.getGeomCrs());
        route.setDriverId(form.getDriverId());
        route.setVehicleId(form.getVehicleId());
        route.setDriverNameSnapshot(null);
        route.setDriverPhoneSnapshot(null);
        route.setVehicleNoSnapshot(null);
        if (form.getDriverId() != null) {
            var driver = drivers.selectById(form.getDriverId());
            if (driver == null || !"ENABLED".equals(driver.getStatus()))
                throw new ScmBusinessException(MASTER_DISABLED);
            route.setDriverNameSnapshot(driver.getDriverName());
            route.setDriverPhoneSnapshot(driver.getPhone());
        }
        if (form.getVehicleId() != null) {
            var vehicle = vehicles.selectById(form.getVehicleId());
            if (vehicle == null || !"ENABLED".equals(vehicle.getStatus()))
                throw new ScmBusinessException(MASTER_DISABLED);
            route.setVehicleNoSnapshot(vehicle.getVehicleNo());
        }
        route.setPlannedDepartureTime(form.getPlannedDepartureTime());
        route.setRemark(form.getRemark());
    }

    @Transactional(rollbackFor = Exception.class)
    public void addOrders(Long id, DeliveryOrdersForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        var ids = form.getOrderIds().stream().distinct().sorted().toList();
        var current = active(id);
        if (current.size() + ids.size() > 500) throw new ScmBusinessException(LIMIT_EXCEEDED);
        // Lock all requested orders before reading snapshots or testing the unique ACTIVE assignment.
        for (Long orderId : ids) {
            if (!eligibility.eligible(orders.lock(orderId))) throw new ScmBusinessException(ORDER_INELIGIBLE);
        }
        var routeStops = new ArrayList<>(stops.selectList(new LambdaQueryWrapper<DeliveryRouteStopEntity>()
                .eq(DeliveryRouteStopEntity::getRouteId, id).orderByAsc(DeliveryRouteStopEntity::getStopSeq)));
        int seq = routeStops.stream().mapToInt(DeliveryRouteStopEntity::getStopSeq).max().orElse(0);
        for (Long orderId : ids) {
            if (assignments.selectCount(new LambdaQueryWrapper<DeliveryRouteOrderEntity>()
                    .eq(DeliveryRouteOrderEntity::getOrderId, orderId).eq(DeliveryRouteOrderEntity::getAssignmentStatus, "ACTIVE")) > 0)
                throw new ScmBusinessException(ORDER_ASSIGNED);
            var candidate = queries.candidate(orderId);
            if (candidate == null || candidate.getAddress() == null || candidate.getAddress().isBlank())
                throw new ScmBusinessException(ORDER_INELIGIBLE);
            var stop = routeStops.stream().filter(s -> Objects.equals(s.getCustomerId(), candidate.getCustomerId())
                    && Objects.equals(s.getAddressSnapshot(), candidate.getAddress())).findFirst().orElse(null);
            if (stop == null) {
                stop = new DeliveryRouteStopEntity();
                BeanUtils.copyProperties(candidate, stop, "id", "createdAt", "createdBy");
                stop.setRouteId(id);
                stop.setStopSeq(++seq);
                stop.setCustomerNameSnapshot(candidate.getCustomerName());
                stop.setAddressSnapshot(candidate.getAddress());
                stop.setReceiverNameSnapshot(candidate.getReceiverName());
                stop.setReceiverPhoneSnapshot(candidate.getReceiverPhone());
                stamp(stop, true);
                stops.insert(stop);
                routeStops.add(stop);
            }
            var assignment = new DeliveryRouteOrderEntity();
            assignment.setRouteId(id);
            assignment.setStopId(stop.getId());
            assignment.setOrderId(orderId);
            assignment.setCustomerId(candidate.getCustomerId());
            assignment.setOrderNoSnapshot(candidate.getOrderNo());
            assignment.setOrderAmountSnapshot(candidate.getOrderAmount());
            assignment.setExpectDeliveryTimeSnapshot(candidate.getExpectDeliveryTime());
            assignment.setAssignmentStatus("ACTIVE");
            stamp(assignment, true);
            try {
                assignments.insert(assignment);
            } catch (DuplicateKeyException e) {
                throw new ScmBusinessException(ORDER_ASSIGNED);
            }
        }
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeOrder(Long id, Long orderId, DeliveryVersionForm form) {
        reason(form);
        var route = lock(id, form.getVersion());
        draft(route);
        orders.lock(orderId);
        var assignment = active(id).stream().filter(a -> a.getOrderId().equals(orderId)).findFirst().orElseThrow(() -> new ScmBusinessException(NOT_FOUND));
        assignment.setAssignmentStatus("RELEASED");
        stamp(assignment, false);
        assignments.updateById(assignment);
        assignments.deleteById(assignment.getId());
        if (assignments.selectCount(new LambdaQueryWrapper<DeliveryRouteOrderEntity>()
                .eq(DeliveryRouteOrderEntity::getStopId, assignment.getStopId()).eq(DeliveryRouteOrderEntity::getAssignmentStatus, "ACTIVE")) == 0) {
            stops.deleteById(assignment.getStopId());
        }
        // Repack gaps so stop count and displayed sequence stay consistent.
        var remaining = queries.stops(id);
        queries.bumpStopSequences(id);
        int seq = 0;
        for (var stop : remaining) {
            stop.setStopSeq(++seq);
            stamp(stop, false);
            stops.updateById(stop);
        }
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(Long id, DeliveryReorderForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        var existing = stops.selectList(new LambdaQueryWrapper<DeliveryRouteStopEntity>().eq(DeliveryRouteStopEntity::getRouteId, id));
        var ids = new HashSet<>(form.getStopIds());
        if (ids.size() != form.getStopIds().size() || ids.size() != existing.size()
                || !ids.equals(new HashSet<>(existing.stream().map(DeliveryRouteStopEntity::getId).toList())))
            throw new ScmBusinessException(STOP_ORDER_INVALID);
        // Move to a disjoint positive sequence range before swapping; partial unique index remains active.
        queries.bumpStopSequences(id);
        var byId = new HashMap<Long, DeliveryRouteStopEntity>();
        existing.forEach(s -> byId.put(s.getId(), s));
        int seq = 0;
        for (Long stopId : form.getStopIds()) {
            var stop = byId.get(stopId);
            stop.setStopSeq(++seq);
            stamp(stop, false);
            stops.updateById(stop);
        }
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void locate(Long id, Long stopId, DeliveryStopForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        var stop = stops.selectById(stopId);
        if (stop == null || !Objects.equals(stop.getRouteId(), id)) throw new ScmBusinessException(NOT_FOUND);
        if (!form.isLocationComplete()) throw new ScmBusinessException(VALIDATION_ERROR);
        stop.setLongitude(form.getLongitude());
        stop.setLatitude(form.getLatitude());
        stop.setGeomCrs(form.getGeomCrs());
        stop.setPlannedArrivalTime(form.getPlannedArrivalTime());
        stop.setRemark(form.getRemark());
        stamp(stop, false);
        stops.updateById(stop);
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void plan(Long id, DeliveryVersionForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        var assigned = active(id);
        if (assigned.isEmpty()) throw new ScmBusinessException(EMPTY_ROUTE);
        for (var assignment : assigned.stream().sorted(Comparator.comparing(DeliveryRouteOrderEntity::getOrderId)).toList()) {
            if (!eligibility.eligible(orders.lock(assignment.getOrderId())))
                throw new ScmBusinessException(ORDER_INELIGIBLE);
        }
        var warehouse = warehouses.selectById(route.getWarehouseId());
        if (warehouse == null || !"ENABLED".equals(warehouse.getStatus()))
            throw new ScmBusinessException(MASTER_DISABLED);
        if (route.getDriverId() != null) {
            var d = drivers.selectById(route.getDriverId());
            if (d == null || !"ENABLED".equals(d.getStatus())) throw new ScmBusinessException(MASTER_DISABLED);
        }
        if (route.getVehicleId() != null) {
            var v = vehicles.selectById(route.getVehicleId());
            if (v == null || !"ENABLED".equals(v.getStatus())) throw new ScmBusinessException(MASTER_DISABLED);
        }
        var routeStops = queries.stops(id);
        if (route.getStartLongitude() == null || route.getStartLatitude() == null || route.getStartGeomCrs() == null || routeStops.isEmpty()
                || routeStops.stream().anyMatch(s -> s.getLongitude() == null || s.getLatitude() == null || !Objects.equals(route.getStartGeomCrs(), s.getGeomCrs())))
            throw new ScmBusinessException(LOCATION_REQUIRED);
        route.setStatus("PLANNED");
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, DeliveryVersionForm form) {
        reason(form);
        var route = lock(id, form.getVersion());
        if (!Set.of("DRAFT", "PLANNED").contains(route.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        var assigned = active(id);
        assigned.stream().map(DeliveryRouteOrderEntity::getOrderId).sorted().forEach(orders::lock);
        for (var assignment : assigned) {
            assignment.setAssignmentStatus("RELEASED");
            stamp(assignment, false);
            assignments.updateById(assignment);
        }
        route.setStatus("CANCELLED");
        route.setCancelReason(form.getReason().trim());
        save(route);
    }

    /**
     * 按订单正式生成打印：只对本次显式提交且当前仍为 ACTIVE 的订单计次。
     * 持有线路聚合锁，故同一线路的计次串行累加，不丢失；同一幂等键重试只计一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printOrders(Long id, DeliveryPrintOrdersForm form, String key) {
        var claim = idempotency.claim("DELIVERY_PRINT_ORDERS:" + id, key, form);
        if (claim.replay()) return idempotency.replay(claim, DeliveryPrintResultVO.class);
        printable(lock(id, form.getVersion()));
        var wanted = new HashSet<>(form.getOrderIds());
        var selected = active(id).stream().filter(a -> wanted.contains(a.getOrderId())).toList();
        // 请求集合必须在锁定的 ACTIVE 集合中一一对应；缺少任一订单说明预览后线路已变化，拒绝旧请求。
        if (selected.size() != wanted.size()) throw new ScmBusinessException(STATE_INVALID);
        var result = recordAndBuild(id, selected);
        idempotency.complete(claim, "DELIVERY_ROUTE", id, result);
        return result;
    }

    /**
     * 按客户正式生成打印：把选定客户展开为其线路内有效订单，再按 {@code orderPrintFilter}
     * 决定这些客户中打印哪些订单（PARTIAL 客户可只补打未打印订单）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printCustomers(Long id, DeliveryPrintCustomersForm form, String key) {
        var claim = idempotency.claim("DELIVERY_PRINT_CUSTOMERS:" + id, key, form);
        if (claim.replay()) return idempotency.replay(claim, DeliveryPrintResultVO.class);
        printable(lock(id, form.getVersion()));
        var filter = form.getOrderPrintFilter() == null ? "ALL" : form.getOrderPrintFilter();
        var customers = new HashSet<>(form.getCustomerIds());
        var selected = active(id).stream()
                .filter(a -> customers.contains(a.getCustomerId()))
                .filter(a -> switch (filter) {
                    case "PRINTED" -> a.getPrintCount() != null && a.getPrintCount() > 0;
                    case "UNPRINTED" -> a.getPrintCount() == null || a.getPrintCount() == 0;
                    default -> true;
                })
                .toList();
        // 展开后为空说明所选客户当前无匹配有效订单（线路或集合已变化），拒绝而非静默生成零单。
        if (selected.isEmpty()) throw new ScmBusinessException(STATE_INVALID);
        var result = recordAndBuild(id, selected);
        idempotency.complete(claim, "DELIVERY_ROUTE", id, result);
        return result;
    }

    private DeliveryPrintResultVO recordAndBuild(Long id, List<DeliveryRouteOrderEntity> selected) {
        var ids = selected.stream().map(DeliveryRouteOrderEntity::getId).toList();
        if (queries.markPrinted(ids, ScmOperator.current()) != ids.size()) throw new ScmBusinessException(STATE_INVALID);
        var orderIds = new HashSet<>(selected.stream().map(DeliveryRouteOrderEntity::getOrderId).toList());
        var rows = queries.orderView(id).stream().filter(v -> orderIds.contains(v.getOrderId())).toList();
        var result = new DeliveryPrintResultVO();
        result.setRouteId(id);
        result.setGeneratedAt(OffsetDateTime.now());
        result.setOrderCount(rows.size());
        result.setTotalAmount(rows.stream().map(DeliveryOrderViewVO::getOrderAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setOrders(rows);
        return result;
    }

    private void printable(DeliveryRouteEntity route) {
        if (!PRINTABLE.contains(route.getStatus())) throw new ScmBusinessException(STATE_INVALID);
    }

    private List<DeliveryRouteOrderEntity> active(Long id) {
        return assignments.selectList(new LambdaQueryWrapper<DeliveryRouteOrderEntity>().eq(DeliveryRouteOrderEntity::getRouteId, id)
                .eq(DeliveryRouteOrderEntity::getAssignmentStatus, "ACTIVE").orderByAsc(DeliveryRouteOrderEntity::getOrderId));
    }

    private DeliveryRouteEntity lock(Long id, Integer version) {
        var route = queries.lockRoute(id);
        if (route == null) throw new ScmBusinessException(NOT_FOUND);
        if (!Objects.equals(route.getVersion(), version)) throw new ScmBusinessException(VERSION_CONFLICT);
        return route;
    }

    private void draft(DeliveryRouteEntity route) {
        if (!"DRAFT".equals(route.getStatus())) throw new ScmBusinessException(STATE_INVALID);
    }

    private void reason(DeliveryVersionForm form) {
        if (form.getReason() == null || form.getReason().isBlank()) throw new ScmBusinessException(VALIDATION_ERROR);
    }

    private void save(DeliveryRouteEntity route) {
        stamp(route, false);
        if (routes.updateById(route) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
    }

    public static void stamp(DeliveryRecord entity, boolean creating) {
        var now = OffsetDateTime.now();
        var operator = ScmOperator.current();
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        if (creating) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operator);
        }
    }
}
