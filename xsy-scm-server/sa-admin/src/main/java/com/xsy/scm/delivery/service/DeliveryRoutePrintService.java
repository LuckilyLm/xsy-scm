package com.xsy.scm.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.delivery.constant.ScmDeliveryAssignmentStatusEnum;
import com.xsy.scm.delivery.constant.ScmDeliveryCustomerPrintFilterEnum;
import com.xsy.scm.delivery.constant.ScmDeliveryIdempotencyResourceTypeEnum;
import com.xsy.scm.delivery.constant.ScmDeliveryOrderPrintFilterEnum;
import com.xsy.scm.delivery.constant.ScmDeliveryRouteStatusEnum;
import com.xsy.scm.delivery.dao.DeliveryQueryDao;
import com.xsy.scm.delivery.dao.DeliveryRouteOrderDao;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;
import com.xsy.scm.delivery.domain.form.DeliveryPrintCustomersForm;
import com.xsy.scm.delivery.domain.form.DeliveryPrintOrdersForm;
import com.xsy.scm.delivery.domain.vo.DeliveryOrderViewVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPrintResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.NOT_FOUND;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.STATE_INVALID;

/** Route-scoped print registration and result construction. */
@Service
public class DeliveryRoutePrintService {

    private static final Set<String> PRINTABLE = Set.of(ScmDeliveryRouteStatusEnum.PLANNED.name(),
            ScmDeliveryRouteStatusEnum.DISPATCHED.name(), ScmDeliveryRouteStatusEnum.COMPLETED.name());

    private final DeliveryQueryDao deliveryQueryDao;
    private final DeliveryRouteOrderDao deliveryRouteOrderDao;
    private final ScmIdempotencyService idempotencyService;

    public DeliveryRoutePrintService(DeliveryQueryDao deliveryQueryDao, DeliveryRouteOrderDao deliveryRouteOrderDao,
            ScmIdempotencyService idempotencyService) {
        this.deliveryQueryDao = deliveryQueryDao;
        this.deliveryRouteOrderDao = deliveryRouteOrderDao;
        this.idempotencyService = idempotencyService;
    }

    /** Count only explicitly selected orders that remain active under the locked route. */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printOrders(Long id, DeliveryPrintOrdersForm form, String key) {
        var claim = idempotencyService.claim("DELIVERY_PRINT_ORDERS:" + id, key, form);
        if (claim.replay()) {
            return DeliveryVisibility.current()
                    .printResult(idempotencyService.replay(claim, DeliveryPrintResultVO.class));
        }
        printable(lock(id, form.getVersion()));
        var wanted = new HashSet<>(form.getOrderIds());
        var selected = active(id).stream().filter(assignment -> wanted.contains(assignment.getOrderId())).toList();
        if (selected.size() != wanted.size())
            throw new ScmBusinessException(STATE_INVALID);
        var result = recordAndBuild(id, selected);
        idempotencyService.complete(claim, ScmDeliveryIdempotencyResourceTypeEnum.DELIVERY_ROUTE.name(), id, result);
        return DeliveryVisibility.current().printResult(result);
    }

    /** Re-evaluate customer and order filters while holding the route lock before recording prints. */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintResultVO printCustomers(Long id, DeliveryPrintCustomersForm form, String key) {
        var claim = idempotencyService.claim("DELIVERY_PRINT_CUSTOMERS:" + id, key, form);
        if (claim.replay()) {
            return DeliveryVisibility.current()
                    .printResult(idempotencyService.replay(claim, DeliveryPrintResultVO.class));
        }
        printable(lock(id, form.getVersion()));
        var customerStatusFilter = customerPrintFilter(form.getCustomerStatusFilter(),
                ScmDeliveryCustomerPrintFilterEnum.ALL);
        var candidates = form.getCustomerIds() == null ? Set.<Long>of() : new HashSet<>(form.getCustomerIds());
        if (candidates.isEmpty() && customerStatusFilter == ScmDeliveryCustomerPrintFilterEnum.ALL) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        var orderFilter = orderPrintFilter(form.getOrderPrintFilter());
        var selected = new ArrayList<DeliveryRouteOrderEntity>();
        for (var entry : active(id).stream().collect(
                Collectors.groupingBy(DeliveryRouteOrderEntity::getCustomerId, LinkedHashMap::new, Collectors.toList()))
                .entrySet()) {
            if (!candidates.isEmpty() && !candidates.contains(entry.getKey()))
                continue;
            if (!matchesCustomerStatus(customerStatusFilter, entry.getValue()))
                continue;
            entry.getValue().stream().filter(assignment -> matchesOrderPrint(orderFilter, assignment))
                    .forEach(selected::add);
        }
        if (selected.isEmpty())
            throw new ScmBusinessException(STATE_INVALID);
        var result = recordAndBuild(id, selected);
        idempotencyService.complete(claim, ScmDeliveryIdempotencyResourceTypeEnum.DELIVERY_ROUTE.name(), id, result);
        return DeliveryVisibility.current().printResult(result);
    }

    private boolean matchesCustomerStatus(ScmDeliveryCustomerPrintFilterEnum filter,
            List<DeliveryRouteOrderEntity> customerOrders) {
        if (filter == ScmDeliveryCustomerPrintFilterEnum.ALL)
            return true;
        long printed = customerOrders.stream().filter(this::hasPrinted).count();
        return switch (filter) {
            case PRINTED -> printed == customerOrders.size();
            case UNPRINTED -> printed == 0;
            case PARTIAL -> printed > 0 && printed < customerOrders.size();
            default -> throw new ScmBusinessException(VALIDATION_ERROR);
        };
    }

    private boolean matchesOrderPrint(ScmDeliveryOrderPrintFilterEnum filter, DeliveryRouteOrderEntity assignment) {
        return switch (filter) {
            case PRINTED -> hasPrinted(assignment);
            case UNPRINTED -> !hasPrinted(assignment);
            case ALL -> true;
            default -> throw new ScmBusinessException(VALIDATION_ERROR);
        };
    }

    private ScmDeliveryCustomerPrintFilterEnum customerPrintFilter(String value,
            ScmDeliveryCustomerPrintFilterEnum defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return ScmDeliveryCustomerPrintFilterEnum.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private ScmDeliveryOrderPrintFilterEnum orderPrintFilter(String value) {
        if (value == null)
            return ScmDeliveryOrderPrintFilterEnum.ALL;
        try {
            return ScmDeliveryOrderPrintFilterEnum.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private boolean hasPrinted(DeliveryRouteOrderEntity assignment) {
        return assignment.getPrintCount() != null && assignment.getPrintCount() > 0;
    }

    private DeliveryPrintResultVO recordAndBuild(Long id, List<DeliveryRouteOrderEntity> selected) {
        var assignmentIds = selected.stream().map(DeliveryRouteOrderEntity::getId).toList();
        if (deliveryQueryDao.markPrinted(assignmentIds, ScmOperator.current()) != assignmentIds.size()) {
            throw new ScmBusinessException(STATE_INVALID);
        }
        var orderIds = new HashSet<>(selected.stream().map(DeliveryRouteOrderEntity::getOrderId).toList());
        var rows = deliveryQueryDao.orderView(id).stream()
                .filter(orderView -> orderIds.contains(orderView.getOrderId())).toList();
        var result = new DeliveryPrintResultVO();
        result.setRouteId(id);
        result.setGeneratedAt(OffsetDateTime.now());
        result.setOrderCount(rows.size());
        result.setTotalAmount(rows.stream().map(DeliveryOrderViewVO::getOrderAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setOrders(rows);
        return result;
    }

    private void printable(DeliveryRouteEntity route) {
        if (!PRINTABLE.contains(route.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
    }

    private List<DeliveryRouteOrderEntity> active(Long routeId) {
        return deliveryRouteOrderDao.selectList(
                new LambdaQueryWrapper<DeliveryRouteOrderEntity>().eq(DeliveryRouteOrderEntity::getRouteId, routeId)
                        .eq(DeliveryRouteOrderEntity::getAssignmentStatus,
                                ScmDeliveryAssignmentStatusEnum.ACTIVE.name())
                        .orderByAsc(DeliveryRouteOrderEntity::getOrderId));
    }

    private DeliveryRouteEntity lock(Long routeId, Integer version) {
        var route = deliveryQueryDao.lockRoute(routeId);
        if (route == null)
            throw new ScmBusinessException(NOT_FOUND);
        if (!Objects.equals(route.getVersion(), version))
            throw new ScmBusinessException(VERSION_CONFLICT);
        return route;
    }
}
