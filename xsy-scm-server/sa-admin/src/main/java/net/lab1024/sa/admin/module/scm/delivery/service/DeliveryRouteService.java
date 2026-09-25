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
import java.util.stream.Collectors;

import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.delivery.dao.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.dto.DeliverySortedLine;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.finance.service.FinanceReceivableService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryFulfillmentService;
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
    /**
     * 库存域唯一的写入口：本类不出现任何直接改余额 / 预留 / 流水的代码（P2 裁决第 4 条）。
     */
    private final InventoryFulfillmentService fulfillment;
    /**
     * 只为「签收」这一件事注入：司机维度收窄必须与读侧同源，见 {@link #sign} 里的说明。
     */
    private final ScmDataScopeService scopeService;
    /**
     * 应收生成器（Finance R1 F1-2B）：签收成功即在同一事务内派生正常应收。
     * 依赖方向是 delivery → finance，finance 对配送 / 订单 / 库存表只读、不反向 import 配送域，
     * 因此不构成环；生成失败即整笔签收回滚（与 {@link #fulfillment} 的库存写入同一条纪律）。
     */
    private final FinanceReceivableService financeReceivableService;

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
        // ACTIVE 占用判断与订单快照读取各合成一条：原先逐单查，500 单就是 1000 次往返。
        // 上面已把所有请求订单加锁，这里读到的是稳定快照；ids 受 LIMIT_EXCEEDED 约束在 500 以内。
        var alreadyAssigned = new HashSet<Long>();
        var snapshots = new HashMap<Long, DeliveryCandidateVO>();
        if (!ids.isEmpty()) {
            assignments.selectList(new LambdaQueryWrapper<DeliveryRouteOrderEntity>()
                            .select(DeliveryRouteOrderEntity::getOrderId)
                            .in(DeliveryRouteOrderEntity::getOrderId, ids)
                            .eq(DeliveryRouteOrderEntity::getAssignmentStatus, "ACTIVE"))
                    .forEach(assigned -> alreadyAssigned.add(assigned.getOrderId()));
            queries.candidateByIds(ids).forEach(snapshot -> snapshots.put(snapshot.getOrderId(), snapshot));
        }
        int seq = routeStops.stream().mapToInt(DeliveryRouteStopEntity::getStopSeq).max().orElse(0);
        for (Long orderId : ids) {
            if (alreadyAssigned.contains(orderId))
                throw new ScmBusinessException(ORDER_ASSIGNED);
            var candidate = snapshots.get(orderId);
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
     * 发车：整条线路原子出库，{@code PLANNED → DISPATCHED}（P2 裁决第 1、2、9 条）。
     *
     * <p>实发量一律取分拣的 {@code sorted_quantity}，本方法不读 {@code actual_quantity}、
     * 不重新计算差异。锁序：线路聚合锁 → 逐订单行锁 → 库存命令内部的预留锁与余额锁。
     *
     * <p>线路内任一订单在锁上复核后不再合格（例如分拣被重开）就<b>整条拒绝</b>，
     * 不做「先发能发的」；库存不足同样整条回滚，一行库存都不扣。
     * 全部订单都实发 0（整线 OUT_OF_STOCK）时不生成出库单，{@code outboundId} 返回 null ——
     * 没有实物离开仓库，不该留下一张空出库单。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryDispatchResultVO dispatch(Long id, DeliveryVersionForm form, String key) {
        var claim = idempotency.claim("DELIVERY_DISPATCH:" + id, key, form);
        if (claim.replay()) {
            return idempotency.replay(claim, DeliveryDispatchResultVO.class);
        }
        var route = lock(id, form.getVersion());
        if (!"PLANNED".equals(route.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        var assigned = active(id);
        if (assigned.isEmpty()) throw new ScmBusinessException(EMPTY_ROUTE);
        // 发车前在锁上重查资格：PLANNED 之后分拣任务可能被重开，规划那一刻的结论不算数。
        for (var orderId : assigned.stream().map(DeliveryRouteOrderEntity::getOrderId).sorted().toList()) {
            if (!eligibility.eligible(orders.lock(orderId))) throw new ScmBusinessException(DISPATCH_ROUTE_INELIGIBLE);
        }

        var orderIds = assigned.stream().map(DeliveryRouteOrderEntity::getOrderId).toList();
        var linesByOrder = queries.sortedLines(orderIds).stream()
                .collect(Collectors.groupingBy(DeliverySortedLine::getOrderId, LinkedHashMap::new, Collectors.toList()));
        var lines = new ArrayList<InventoryFulfillmentService.Line>();
        for (var orderId : orderIds) {
            var sorted = linesByOrder.get(orderId);
            // 一条行都取不到 = 该订单其实没有被分拣覆盖，与上面的资格判定矛盾，宁可不发。
            if (sorted == null || sorted.isEmpty()) throw new ScmBusinessException(DISPATCH_ROUTE_INELIGIBLE);
            for (var line : sorted) {
                if (line.getSortedQuantity() == null) throw new ScmBusinessException(DISPATCH_ROUTE_INELIGIBLE);
                lines.add(new InventoryFulfillmentService.Line(orderId, line.getSalesOrderItemId(),
                        line.getSkuId(), line.getSortedQuantity()));
            }
        }

        var now = OffsetDateTime.now();
        var operator = ScmOperator.current();
        var outbound = fulfillment.dispatchOutbound(new InventoryFulfillmentService.Command(
                id, route.getWarehouseId(), now, operator, lines));

        route.setStatus("DISPATCHED");
        route.setOutboundId(outbound.outboundId());
        route.setDispatchedAt(now);
        route.setDispatchedBy(operator);
        save(route);
        if (queries.markInTransit(id, operator) != assigned.size()) throw new ScmBusinessException(STATE_INVALID);

        var result = new DeliveryDispatchResultVO();
        result.setRouteId(id);
        result.setStatus(route.getStatus());
        result.setDispatchedAt(now);
        result.setOutboundId(outbound.outboundId());
        result.setOutboundNo(outbound.outboundNo());
        result.setOrderCount(assigned.size());
        result.setShippedLineCount(outbound.shippedLineCount());
        idempotency.complete(claim, "DELIVERY_ROUTE", id, result);
        return result;
    }

    /**
     * 订单级签收：{@code IN_TRANSIT → SIGNED | EXCEPTION}（P2 裁决第 12、13 条）。
     *
     * <p><b>锁的实况</b>：本方法第一行就是 {@code lockRoute}（{@code SELECT … FOR UPDATE}），
     * 所以同一线路的签收是串行的；线路内某一单的行级并发另外由 {@code version} 乐观锁 +
     * 条件更新兜底（{@code markSigned} 返回 0 即「有人比你先签了」）。
     * 签收是单向推进（{@code PENDING / IN_TRANSIT} 只能走向终态），因此完成线路所要求的
     * 「全部活动订单已终态」对并发签收是单调的。
     *
     * <p>Finance R1 F1-2B 接在这里之后没有引入任何新的锁：应收生成只 INSERT 自己的两张表，
     * 不锁业务表也不锁余额（全局不变量 4），只是把线路锁的持有时长延长几条 INSERT；
     * 跨线路对同一订单的重复签收由 {@code uk_finance_receivable_source_active} 仲裁，
     * 后到者命中唯一索引即按「已生成」静默返回，不会产生第二张应收。
     *
     * <p>异常签收<b>不反冲</b> {@code SALES_OUT}：库存已真实出库，冲销必须由后续退货流程新增反向事实。
     */
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long routeId, Long orderId, DeliverySignForm form) {
        var route = queries.lockRoute(routeId);
        if (route == null) throw new ScmBusinessException(NOT_FOUND);
        // 签收要按司机维度收窄，与本域其它写动作不同：plan / cancel 的执行者是持全量范围的调度岗，
        // 而 SCM_DRIVER 也持签收权。不在这一步收口，任何司机都能凭一个 routeId 替别人的线路签收。
        if (!scopeService.resolve().getDriverScope().allows(route.getDriverId())) throw new ScmDataScopeException();
        if (!"DISPATCHED".equals(route.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        boolean exception = "EXCEPTION".equals(form.getResult());
        if (!"SIGNED".equals(form.getResult()) && !exception) throw new ScmBusinessException(SIGN_RESULT_INVALID);
        if (exception && (form.getReason() == null || form.getReason().isBlank()))
            throw new ScmBusinessException(SIGN_REASON_REQUIRED);
        var assignment = assignments.selectOne(new LambdaQueryWrapper<DeliveryRouteOrderEntity>()
                .eq(DeliveryRouteOrderEntity::getRouteId, routeId)
                .eq(DeliveryRouteOrderEntity::getOrderId, orderId)
                .eq(DeliveryRouteOrderEntity::getAssignmentStatus, "ACTIVE"));
        if (assignment == null) throw new ScmBusinessException(NOT_FOUND);
        String reason = form.getReason() == null ? null : form.getReason().trim();
        // 状态条件与 version 一起进 WHERE：受影响行数 != 1 就是「有人比你先签了」或「这单已不在线路上」。
        if (queries.markSigned(assignment.getId(), form.getVersion(), form.getResult(), reason, ScmOperator.current()) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);

        // Finance R1（第一批 Q1）：客户签收是订单级终态，也是应收的形成时点。
        // 只有 markSigned 真的改到那一行才生成 —— 返回 0 已经先抛 VERSION_CONFLICT，
        // 那一笔应收归那次成功的签收所有，绝不出现两笔。EXCEPTION 不形成应收（第二批 Q6），
        // 也不反冲 SALES_OUT；签收时刻与签收人由生成器回读 delivery_route_order，
        // 因为 markSigned 的 signed_at 是数据库时钟，在这里现取 now() 会造出第二个时点事实。
        if (!exception) {
            financeReceivableService.generateOnSign(assignment.getId());
        }
    }

    /**
     * 完成线路：{@code DISPATCHED → COMPLETED}，硬前置是全部活动订单已进入终态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, DeliveryVersionForm form) {
        var route = lock(id, form.getVersion());
        if (!"DISPATCHED".equals(route.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        if (queries.countUnfinished(id) != 0) throw new ScmBusinessException(ROUTE_NOT_ALL_SIGNED);
        route.setStatus("COMPLETED");
        route.setCompletedAt(OffsetDateTime.now());
        route.setCompletedBy(ScmOperator.current());
        save(route);
    }

    /**
     * 按订单正式生成打印：只对本次显式提交且当前仍为 ACTIVE 的订单计次。
     * 持有线路聚合锁，故同一线路的计次串行累加，不丢失；同一幂等键重试只计一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printOrders(Long id, DeliveryPrintOrdersForm form, String key) {
        var claim = idempotency.claim("DELIVERY_PRINT_ORDERS:" + id, key, form);
        // 重放结果取自幂等记录里的原始明细，金额同样要在返回前过一遍可见性口径。
        if (claim.replay())
            return DeliveryVisibility.current().printResult(idempotency.replay(claim, DeliveryPrintResultVO.class));
        printable(lock(id, form.getVersion()));
        var wanted = new HashSet<>(form.getOrderIds());
        var selected = active(id).stream().filter(a -> wanted.contains(a.getOrderId())).toList();
        // 请求集合必须在锁定的 ACTIVE 集合中一一对应；缺少任一订单说明预览后线路已变化，拒绝旧请求。
        if (selected.size() != wanted.size()) throw new ScmBusinessException(STATE_INVALID);
        var result = recordAndBuild(id, selected);
        idempotency.complete(claim, "DELIVERY_ROUTE", id, result);
        return DeliveryVisibility.current().printResult(result);
    }

    /**
     * 按客户正式生成打印：先按客户状态圈定客户，再决定这些客户中打印哪些订单。
     * 客户状态在线路锁内按当前 ACTIVE 订单重新聚合，前端名单只是候选范围——预览后计数已变的
     * 客户会被排除，而不是按过期状态重打；展开后无订单则拒绝而非生成零单打印。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printCustomers(Long id, DeliveryPrintCustomersForm form, String key) {
        var claim = idempotency.claim("DELIVERY_PRINT_CUSTOMERS:" + id, key, form);
        if (claim.replay())
            return DeliveryVisibility.current().printResult(idempotency.replay(claim, DeliveryPrintResultVO.class));
        printable(lock(id, form.getVersion()));
        var statusFilter = form.getCustomerStatusFilter() == null ? "ALL" : form.getCustomerStatusFilter();
        var candidates = form.getCustomerIds() == null ? Set.<Long>of() : new HashSet<>(form.getCustomerIds());
        if (candidates.isEmpty() && "ALL".equals(statusFilter)) throw new ScmBusinessException(VALIDATION_ERROR);
        var orderFilter = form.getOrderPrintFilter() == null ? "ALL" : form.getOrderPrintFilter();
        var selected = new ArrayList<DeliveryRouteOrderEntity>();
        // 聚合键用 LinkedHashMap：线路锁内读到的顺序即打印顺序，两次同请求生成同一份清单。
        for (var entry : active(id).stream()
                .collect(Collectors.groupingBy(DeliveryRouteOrderEntity::getCustomerId, LinkedHashMap::new, Collectors.toList()))
                .entrySet()) {
            if (!candidates.isEmpty() && !candidates.contains(entry.getKey())) continue;
            if (!matchesCustomerStatus(statusFilter, entry.getValue())) continue;
            entry.getValue().stream().filter(a -> matchesOrderPrint(orderFilter, a)).forEach(selected::add);
        }
        if (selected.isEmpty()) throw new ScmBusinessException(STATE_INVALID);
        var result = recordAndBuild(id, selected);
        idempotency.complete(claim, "DELIVERY_ROUTE", id, result);
        return DeliveryVisibility.current().printResult(result);
    }

    /**
     * 客户维度打印状态：与只读 customerView 的判定口径一致（已打印有效订单数对比总有效订单数）。
     */
    private boolean matchesCustomerStatus(String statusFilter, List<DeliveryRouteOrderEntity> customerOrders) {
        if ("ALL".equals(statusFilter)) return true;
        long printed = customerOrders.stream().filter(a -> hasPrinted(a)).count();
        return switch (statusFilter) {
            case "PRINTED" -> printed == customerOrders.size();
            case "UNPRINTED" -> printed == 0;
            case "PARTIAL" -> printed > 0 && printed < customerOrders.size();
            default -> throw new ScmBusinessException(VALIDATION_ERROR);
        };
    }

    private boolean matchesOrderPrint(String orderFilter, DeliveryRouteOrderEntity assignment) {
        return switch (orderFilter) {
            case "PRINTED" -> hasPrinted(assignment);
            case "UNPRINTED" -> !hasPrinted(assignment);
            case "ALL" -> true;
            default -> throw new ScmBusinessException(VALIDATION_ERROR);
        };
    }

    private boolean hasPrinted(DeliveryRouteOrderEntity assignment) {
        return assignment.getPrintCount() != null && assignment.getPrintCount() > 0;
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
