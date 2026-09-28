package com.xsy.scm.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.delivery.dao.DeliveryDriverDao;
import com.xsy.scm.delivery.dao.DeliveryQueryDao;
import com.xsy.scm.delivery.dao.DeliveryRouteDao;
import com.xsy.scm.delivery.dao.DeliveryRouteOrderDao;
import com.xsy.scm.delivery.dao.DeliveryRouteStopDao;
import com.xsy.scm.delivery.dao.DeliveryVehicleDao;
import com.xsy.scm.delivery.constant.ScmDeliveryAssignmentStatusEnum;
import com.xsy.scm.delivery.constant.ScmDeliveryIdempotencyResourceTypeEnum;
import com.xsy.scm.delivery.constant.ScmDeliveryRouteStatusEnum;
import com.xsy.scm.delivery.constant.ScmDeliverySignResultEnum;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.delivery.domain.dto.DeliverySortedLine;
import com.xsy.scm.delivery.domain.entity.DeliveryRecord;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;
import com.xsy.scm.delivery.domain.form.DeliveryOrdersForm;
import com.xsy.scm.delivery.domain.form.DeliveryPrintCustomersForm;
import com.xsy.scm.delivery.domain.form.DeliveryPrintOrdersForm;
import com.xsy.scm.delivery.domain.form.DeliveryReorderForm;
import com.xsy.scm.delivery.domain.form.DeliveryRouteForm;
import com.xsy.scm.delivery.domain.form.DeliverySignForm;
import com.xsy.scm.delivery.domain.form.DeliveryStopForm;
import com.xsy.scm.delivery.domain.form.DeliveryVersionForm;
import com.xsy.scm.delivery.domain.vo.DeliveryCandidateVO;
import com.xsy.scm.delivery.domain.vo.DeliveryDispatchResultVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPrintResultVO;
import com.xsy.scm.finance.service.FinanceReceivableService;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.warehouse.dao.WarehouseDao;

import static com.xsy.scm.delivery.constant.DeliveryErrorCode.DISPATCH_ROUTE_INELIGIBLE;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.EMPTY_ROUTE;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.LOCATION_REQUIRED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.LIMIT_EXCEEDED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.MASTER_DISABLED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.NOT_FOUND;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.ORDER_ASSIGNED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.ORDER_INELIGIBLE;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.ROUTE_NOT_ALL_SIGNED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.SIGN_REASON_REQUIRED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.SIGN_RESULT_INVALID;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.STATE_INVALID;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.STOP_ORDER_INVALID;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
/**
 * Route is the aggregate lock. Order rows are locked in ID order before assignment/plan/release.
 */
@Service
@RequiredArgsConstructor
public class DeliveryRouteService {
    private final DeliveryRouteDao deliveryRouteDao;
    private final DeliveryRouteStopDao deliveryRouteStopDao;
    private final DeliveryRouteOrderDao deliveryRouteOrderDao;
    private final DeliveryQueryDao deliveryQueryDao;
    private final DeliveryDriverDao deliveryDriverDao;
    private final DeliveryVehicleDao deliveryVehicleDao;
    private final WarehouseDao warehouseDao;
    private final SalesOrderDao salesOrderDao;
    private final DeliveryEligibilityPolicy eligibility;
    private final ScmIdempotencyService idempotencyService;
    private final DeliveryRoutePrintService deliveryRoutePrintService;
    /**
     * 库存域唯一的写入口：本类不直接修改余额、预留或库存流水。
     */
    private final InventoryFulfillmentService inventoryFulfillmentService;
    /**
     * 只为「签收」这一件事注入：司机维度收窄必须与读侧同源，见 {@link #sign} 里的说明。
     */
    private final ScmDataScopeService dataScopeService;
    /**
     * 应收生成器：签收成功即在同一事务内派生正常应收。 依赖方向是 delivery → finance，finance 对配送 / 订单 / 库存表只读、不反向 import 配送域， 因此不构成环；生成失败即整笔签收回滚，与
     * {@link #inventoryFulfillmentService} 的库存写入同事务。
     */
    private final FinanceReceivableService financeReceivableService;

    @Transactional(rollbackFor = Exception.class)
    public Long create(DeliveryRouteForm form) {
        var route = new DeliveryRouteEntity();
        applyHeader(route, form);
        String sequence = String.format("%06d", deliveryQueryDao.nextNumber());
        route.setRouteNo("DR" + form.getDeliveryDate().format(DateTimeFormatter.BASIC_ISO_DATE) + sequence);
        route.setStatus(ScmDeliveryRouteStatusEnum.DRAFT.name());
        stamp(route, true);
        deliveryRouteDao.insert(route);
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
        var warehouse = warehouseDao.selectById(form.getWarehouseId());
        if (warehouse == null || !ScmWarehouseStatusEnum.ENABLED.name().equals(warehouse.getStatus()))
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
            var driver = deliveryDriverDao.selectById(form.getDriverId());
            if (driver == null || !ScmEnableStatusEnum.ENABLED.name().equals(driver.getStatus()))
                throw new ScmBusinessException(MASTER_DISABLED);
            route.setDriverNameSnapshot(driver.getDriverName());
            route.setDriverPhoneSnapshot(driver.getPhone());
        }
        if (form.getVehicleId() != null) {
            var vehicle = deliveryVehicleDao.selectById(form.getVehicleId());
            if (vehicle == null || !ScmEnableStatusEnum.ENABLED.name().equals(vehicle.getStatus()))
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
        if (current.size() + ids.size() > 500)
            throw new ScmBusinessException(LIMIT_EXCEEDED);
        // Lock all requested orders before reading snapshots or testing the unique ACTIVE assignment.
        for (Long orderId : ids) {
            if (!eligibility.eligible(salesOrderDao.lock(orderId)))
                throw new ScmBusinessException(ORDER_INELIGIBLE);
        }
        var routeStops = new ArrayList<>(deliveryRouteStopDao.selectList(new LambdaQueryWrapper<
                DeliveryRouteStopEntity>().eq(DeliveryRouteStopEntity::getRouteId, id)
                .orderByAsc(DeliveryRouteStopEntity::getStopSeq)));
        // ACTIVE 占用判断与订单快照读取各合成一条：原先逐单查，500 单就是 1000 次往返。
        // 上面已把所有请求订单加锁，这里读到的是稳定快照；ids 受 LIMIT_EXCEEDED 约束在 500 以内。
        var alreadyAssigned = new HashSet<
                Long>();
        var snapshots = new HashMap<
                Long,
                DeliveryCandidateVO>();
        if (!ids.isEmpty()) {
            deliveryRouteOrderDao.selectList(new LambdaQueryWrapper<
                    DeliveryRouteOrderEntity>().select(DeliveryRouteOrderEntity::getOrderId)
                    .in(DeliveryRouteOrderEntity::getOrderId, ids)
                    .eq(DeliveryRouteOrderEntity::getAssignmentStatus, ScmDeliveryAssignmentStatusEnum.ACTIVE.name()))
                    .forEach(assigned -> alreadyAssigned.add(assigned.getOrderId()));
            deliveryQueryDao.candidateByIds(ids).forEach(snapshot -> snapshots.put(snapshot.getOrderId(), snapshot));
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
                deliveryRouteStopDao.insert(stop);
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
            assignment.setAssignmentStatus(ScmDeliveryAssignmentStatusEnum.ACTIVE.name());
            stamp(assignment, true);
            try {
                deliveryRouteOrderDao.insert(assignment);
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
        salesOrderDao.lock(orderId);
        var assignment = active(id).stream().filter(a -> a.getOrderId().equals(orderId)).findFirst()
                .orElseThrow(() -> new ScmBusinessException(NOT_FOUND));
        assignment.setAssignmentStatus(ScmDeliveryAssignmentStatusEnum.RELEASED.name());
        stamp(assignment, false);
        deliveryRouteOrderDao.updateById(assignment);
        deliveryRouteOrderDao.deleteById(assignment.getId());
        if (deliveryRouteOrderDao.selectCount(new LambdaQueryWrapper<
                DeliveryRouteOrderEntity>().eq(DeliveryRouteOrderEntity::getStopId, assignment.getStopId())
                .eq(DeliveryRouteOrderEntity::getAssignmentStatus,
                        ScmDeliveryAssignmentStatusEnum.ACTIVE.name())) == 0) {
            deliveryRouteStopDao.deleteById(assignment.getStopId());
        }
        // Repack gaps so stop count and displayed sequence stay consistent.
        var remaining = deliveryQueryDao.stops(id);
        deliveryQueryDao.bumpStopSequences(id);
        int seq = 0;
        for (var stop : remaining) {
            stop.setStopSeq(++seq);
            stamp(stop, false);
            deliveryRouteStopDao.updateById(stop);
        }
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(Long id, DeliveryReorderForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        var existing = deliveryRouteStopDao.selectList(new LambdaQueryWrapper<
                DeliveryRouteStopEntity>().eq(DeliveryRouteStopEntity::getRouteId, id));
        var ids = new HashSet<>(form.getStopIds());
        if (ids.size() != form.getStopIds().size() || ids.size() != existing.size()
                || !ids.equals(new HashSet<>(existing.stream().map(DeliveryRouteStopEntity::getId).toList())))
            throw new ScmBusinessException(STOP_ORDER_INVALID);
        // Move to a disjoint positive sequence range before swapping; partial unique index remains active.
        deliveryQueryDao.bumpStopSequences(id);
        var byId = new HashMap<
                Long,
                DeliveryRouteStopEntity>();
        existing.forEach(s -> byId.put(s.getId(), s));
        int seq = 0;
        for (Long stopId : form.getStopIds()) {
            var stop = byId.get(stopId);
            stop.setStopSeq(++seq);
            stamp(stop, false);
            deliveryRouteStopDao.updateById(stop);
        }
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void locate(Long id, Long stopId, DeliveryStopForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        var stop = deliveryRouteStopDao.selectById(stopId);
        if (stop == null || !Objects.equals(stop.getRouteId(), id))
            throw new ScmBusinessException(NOT_FOUND);
        if (!form.isLocationComplete())
            throw new ScmBusinessException(VALIDATION_ERROR);
        stop.setLongitude(form.getLongitude());
        stop.setLatitude(form.getLatitude());
        stop.setGeomCrs(form.getGeomCrs());
        stop.setPlannedArrivalTime(form.getPlannedArrivalTime());
        stop.setRemark(form.getRemark());
        stamp(stop, false);
        deliveryRouteStopDao.updateById(stop);
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void plan(Long id, DeliveryVersionForm form) {
        var route = lock(id, form.getVersion());
        draft(route);
        var assigned = active(id);
        if (assigned.isEmpty())
            throw new ScmBusinessException(EMPTY_ROUTE);
        for (var assignment : assigned.stream().sorted(Comparator.comparing(DeliveryRouteOrderEntity::getOrderId))
                .toList()) {
            if (!eligibility.eligible(salesOrderDao.lock(assignment.getOrderId())))
                throw new ScmBusinessException(ORDER_INELIGIBLE);
        }
        var warehouse = warehouseDao.selectById(route.getWarehouseId());
        if (warehouse == null || !ScmWarehouseStatusEnum.ENABLED.name().equals(warehouse.getStatus()))
            throw new ScmBusinessException(MASTER_DISABLED);
        if (route.getDriverId() != null) {
            var deliveryDriver = deliveryDriverDao.selectById(route.getDriverId());
            if (deliveryDriver == null || !ScmEnableStatusEnum.ENABLED.name().equals(deliveryDriver.getStatus()))
                throw new ScmBusinessException(MASTER_DISABLED);
        }
        if (route.getVehicleId() != null) {
            var deliveryVehicle = deliveryVehicleDao.selectById(route.getVehicleId());
            if (deliveryVehicle == null || !ScmEnableStatusEnum.ENABLED.name().equals(deliveryVehicle.getStatus()))
                throw new ScmBusinessException(MASTER_DISABLED);
        }
        var routeStops = deliveryQueryDao.stops(id);
        if (route.getStartLongitude() == null || route.getStartLatitude() == null || route.getStartGeomCrs() == null
                || routeStops.isEmpty() || routeStops.stream().anyMatch(s -> s.getLongitude() == null
                        || s.getLatitude() == null || !Objects.equals(route.getStartGeomCrs(), s.getGeomCrs())))
            throw new ScmBusinessException(LOCATION_REQUIRED);
        route.setStatus(ScmDeliveryRouteStatusEnum.PLANNED.name());
        save(route);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, DeliveryVersionForm form) {
        reason(form);
        var route = lock(id, form.getVersion());
        if (!Set.of(ScmDeliveryRouteStatusEnum.DRAFT.name(), ScmDeliveryRouteStatusEnum.PLANNED.name())
                .contains(route.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        var assigned = active(id);
        assigned.stream().map(DeliveryRouteOrderEntity::getOrderId).sorted().forEach(salesOrderDao::lock);
        for (var assignment : assigned) {
            assignment.setAssignmentStatus(ScmDeliveryAssignmentStatusEnum.RELEASED.name());
            stamp(assignment, false);
            deliveryRouteOrderDao.updateById(assignment);
        }
        route.setStatus(ScmDeliveryRouteStatusEnum.CANCELLED.name());
        route.setCancelReason(form.getReason().trim());
        save(route);
    }

    /**
     * 发车：整条线路原子出库，{@code PLANNED → DISPATCHED}。
     *
     * <p>
     * 实发量一律取分拣的 {@code sorted_quantity}，本方法不读 {@code actual_quantity}、 不重新计算差异。锁序：线路聚合锁 → 逐订单行锁 → 库存命令内部的预留锁与余额锁。
     *
     * <p>
     * 线路内任一订单在锁上复核后不再合格（例如分拣被重开）就<b>整条拒绝</b>， 不做「先发能发的」；库存不足同样整条回滚，一行库存都不扣。 全部订单都实发 0（整线
     * OUT_OF_STOCK）时不生成出库单，{@code outboundId} 返回 null —— 没有实物离开仓库，不该留下一张空出库单。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryDispatchResultVO dispatch(Long id, DeliveryVersionForm form, String key) {
        var claim = idempotencyService.claim("DELIVERY_DISPATCH:" + id, key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, DeliveryDispatchResultVO.class);
        }
        var route = lock(id, form.getVersion());
        if (!ScmDeliveryRouteStatusEnum.PLANNED.name().equals(route.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        var assigned = active(id);
        if (assigned.isEmpty())
            throw new ScmBusinessException(EMPTY_ROUTE);
        // 发车前在锁上重查资格：PLANNED 之后分拣任务可能被重开，规划那一刻的结论不算数。
        for (var orderId : assigned.stream().map(DeliveryRouteOrderEntity::getOrderId).sorted().toList()) {
            if (!eligibility.eligible(salesOrderDao.lock(orderId)))
                throw new ScmBusinessException(DISPATCH_ROUTE_INELIGIBLE);
        }

        var orderIds = assigned.stream().map(DeliveryRouteOrderEntity::getOrderId).toList();
        var linesByOrder = deliveryQueryDao.sortedLines(orderIds).stream().collect(
                Collectors.groupingBy(DeliverySortedLine::getOrderId, LinkedHashMap::new, Collectors.toList()));
        var lines = new ArrayList<
                InventoryFulfillmentService.Line>();
        for (var orderId : orderIds) {
            var sorted = linesByOrder.get(orderId);
            // 一条行都取不到 = 该订单其实没有被分拣覆盖，与上面的资格判定矛盾，宁可不发。
            if (sorted == null || sorted.isEmpty())
                throw new ScmBusinessException(DISPATCH_ROUTE_INELIGIBLE);
            for (var line : sorted) {
                if (line.getSortedQuantity() == null)
                    throw new ScmBusinessException(DISPATCH_ROUTE_INELIGIBLE);
                lines.add(new InventoryFulfillmentService.Line(orderId, line.getSalesOrderItemId(), line.getSkuId(),
                        line.getSortedQuantity()));
            }
        }

        var now = OffsetDateTime.now();
        var operator = ScmOperator.current();
        var outbound = inventoryFulfillmentService.dispatchOutbound(
                new InventoryFulfillmentService.Command(id, route.getWarehouseId(), now, operator, lines));

        route.setStatus(ScmDeliveryRouteStatusEnum.DISPATCHED.name());
        route.setOutboundId(outbound.outboundId());
        route.setDispatchedAt(now);
        route.setDispatchedBy(operator);
        save(route);
        if (deliveryQueryDao.markInTransit(id, operator) != assigned.size())
            throw new ScmBusinessException(STATE_INVALID);

        var result = new DeliveryDispatchResultVO();
        result.setRouteId(id);
        result.setStatus(route.getStatus());
        result.setDispatchedAt(now);
        result.setOutboundId(outbound.outboundId());
        result.setOutboundNo(outbound.outboundNo());
        result.setOrderCount(assigned.size());
        result.setShippedLineCount(outbound.shippedLineCount());
        idempotencyService.complete(claim, ScmDeliveryIdempotencyResourceTypeEnum.DELIVERY_ROUTE.name(), id, result);
        return result;
    }

    /**
     * 订单级签收：{@code IN_TRANSIT → SIGNED | EXCEPTION}。
     *
     * <p>
     * 本方法先锁线路行，再按 {@code route → sales_order} 的顺序锁被签订单行。 与退货批准共用订单行锁，确保签收与批准按同一顺序串行，避免遗漏红字应收。 线路内某一单的行级并发另外由
     * {@code version} 乐观锁 + 条件更新兜底 （{@code markSigned} 返回 0 即「有人比你先签了」）。 签收是单向推进（{@code PENDING / IN_TRANSIT}
     * 只能走向终态），因此完成线路所要求的 「全部活动订单已终态」对并发签收是单调的。
     *
     * <p>
     * 应收生成不获取业务行锁：订单行锁由本方法这个调用方持有， 生成器只 INSERT 财务自己的表；跨线路重复签同一订单由 {@code uk_finance_receivable_source_active}
     * 仲裁，后到者命中唯一索引即按「已生成」静默返回。
     *
     * <p>
     * 异常签收<b>不反冲</b> {@code SALES_OUT}：库存已真实出库，冲销必须由后续退货流程新增反向事实。
     */
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long routeId, Long orderId, DeliverySignForm form) {
        var route = deliveryQueryDao.lockRoute(routeId);
        if (route == null)
            throw new ScmBusinessException(NOT_FOUND);
        // 签收要按司机维度收窄，与本域其它写动作不同：plan / cancel 的执行者是持全量范围的调度岗，
        // 而 SCM_DRIVER 也持签收权。不在这一步收口，任何司机都能凭一个 routeId 替别人的线路签收。
        if (!dataScopeService.resolve().getDriverScope().allows(route.getDriverId()))
            throw new ScmDataScopeException();
        if (!ScmDeliveryRouteStatusEnum.DISPATCHED.name().equals(route.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        boolean exception = ScmDeliverySignResultEnum.EXCEPTION.name().equals(form.getResult());
        if (!ScmDeliverySignResultEnum.SIGNED.name().equals(form.getResult()) && !exception)
            throw new ScmBusinessException(SIGN_RESULT_INVALID);
        if (exception && (form.getReason() == null || form.getReason().isBlank()))
            throw new ScmBusinessException(SIGN_REASON_REQUIRED);
        var assignment = deliveryRouteOrderDao.selectOne(new LambdaQueryWrapper<
                DeliveryRouteOrderEntity>().eq(DeliveryRouteOrderEntity::getRouteId, routeId)
                .eq(DeliveryRouteOrderEntity::getOrderId, orderId)
                .eq(DeliveryRouteOrderEntity::getAssignmentStatus, ScmDeliveryAssignmentStatusEnum.ACTIVE.name()));
        if (assignment == null)
            throw new ScmBusinessException(NOT_FOUND);
        // 签收与退货批准必须在同一订单行锁上串行，否则两边都可能看不见对方：
        // 批准方在 salesOrderDao.lock 之后才写 APPROVED，签收方若不锁同一行就可能在 APPROVED 提交前完成
        // 「查已批准退货」这一步，红字于是永久漏生成（来源唯一索引修不了「没人尝试 INSERT」）。
        // 锁序 route → sales_order 与本域 dispatch / plan / addOrders 完全一致；
        // 订单、退货、退款域都只锁 sales_order 及其子行，从不锁 delivery_route，因此不存在反向路径。
        salesOrderDao.lock(orderId);
        String reason = form.getReason() == null ? null : form.getReason().trim();
        // 状态条件与 version 一起进 WHERE：受影响行数 != 1 就是「有人比你先签了」或「这单已不在线路上」。
        if (deliveryQueryDao.markSigned(assignment.getId(), form.getVersion(), form.getResult(), reason,
                ScmOperator.current()) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);

        // 客户签收是订单级终态，也是应收的形成时点。
        // 只有 markSigned 真的改到那一行才生成 —— 返回 0 已经先抛 VERSION_CONFLICT，
        // 那一笔应收归那次成功的签收所有，绝不出现两笔。EXCEPTION 不形成应收，
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
        if (!ScmDeliveryRouteStatusEnum.DISPATCHED.name().equals(route.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        if (deliveryQueryDao.countUnfinished(id) != 0)
            throw new ScmBusinessException(ROUTE_NOT_ALL_SIGNED);
        route.setStatus(ScmDeliveryRouteStatusEnum.COMPLETED.name());
        route.setCompletedAt(OffsetDateTime.now());
        route.setCompletedBy(ScmOperator.current());
        save(route);
    }

    /**
     * 按订单正式生成打印：只对本次显式提交且当前仍为 ACTIVE 的订单计次。 持有线路聚合锁，故同一线路的计次串行累加，不丢失；同一幂等键重试只计一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printOrders(Long id, DeliveryPrintOrdersForm form, String key) {
        return deliveryRoutePrintService.printOrders(id, form, key);
    }

    /**
     * 按客户正式生成打印：先按客户状态圈定客户，再决定这些客户中打印哪些订单。 客户状态在线路锁内按当前 ACTIVE 订单重新聚合，前端名单只是候选范围——预览后计数已变的
     * 客户会被排除，而不是按过期状态重打；展开后无订单则拒绝而非生成零单打印。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printCustomers(Long id, DeliveryPrintCustomersForm form, String key) {
        return deliveryRoutePrintService.printCustomers(id, form, key);
    }

    private List<
            DeliveryRouteOrderEntity> active(Long id) {
        return deliveryRouteOrderDao.selectList(new LambdaQueryWrapper<
                DeliveryRouteOrderEntity>().eq(DeliveryRouteOrderEntity::getRouteId, id)
                .eq(DeliveryRouteOrderEntity::getAssignmentStatus, ScmDeliveryAssignmentStatusEnum.ACTIVE.name())
                .orderByAsc(DeliveryRouteOrderEntity::getOrderId));
    }

    private DeliveryRouteEntity lock(Long id, Integer version) {
        var route = deliveryQueryDao.lockRoute(id);
        if (route == null)
            throw new ScmBusinessException(NOT_FOUND);
        if (!Objects.equals(route.getVersion(), version))
            throw new ScmBusinessException(VERSION_CONFLICT);
        return route;
    }

    private void draft(DeliveryRouteEntity route) {
        if (!ScmDeliveryRouteStatusEnum.DRAFT.name().equals(route.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
    }

    private void reason(DeliveryVersionForm form) {
        if (form.getReason() == null || form.getReason().isBlank())
            throw new ScmBusinessException(VALIDATION_ERROR);
    }

    private void save(DeliveryRouteEntity route) {
        stamp(route, false);
        if (deliveryRouteDao.updateById(route) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
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
