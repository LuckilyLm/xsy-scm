package com.xsy.scm.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.form.OrderPoolAssignForm;
import com.xsy.scm.order.domain.form.OrderPoolClaimForm;
import com.xsy.scm.order.domain.form.OrderPoolQueryForm;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.domain.vo.OrderPoolAssigneeVO;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.permission.OrderPermission;

import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

/**
 * 商城未分配订单的独立领取与指派入口；只修改订单自身的负责人快照。
 */
@Service
@RequiredArgsConstructor
public class OrderPoolService {
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderQueryService salesOrderQueryService;
    private final EmployeeDao employeeDao;
    private final LoginManager loginManager;
    private final OrderIdempotencyService idempotencyService;
    private final OrderOperationLogRecorder operationLogs;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<SalesOrderVO> unassigned(OrderPoolQueryForm form) {
        requireAssignPermission();
        var page = SmartPageUtil.convert2PageQuery(form);
        List<SalesOrderVO> rows = salesOrderDao.unassignedMallOrders(page, form).stream()
            .map(salesOrderQueryService::order).toList();
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public List<OrderPoolAssigneeVO> assignees() {
        requireAssignPermission();
        return employeeDao
            .selectList(new LambdaQueryWrapper<EmployeeEntity>()
                .select(EmployeeEntity::getEmployeeId, EmployeeEntity::getActualName,
                    EmployeeEntity::getAdministratorFlag)
                .eq(EmployeeEntity::getDeletedFlag, false).eq(EmployeeEntity::getDisabledFlag, false))
            .stream().filter(this::canReceiveOrder)
            .sorted(Comparator.comparing(EmployeeEntity::getActualName, Comparator.nullsLast(String::compareTo))
                .thenComparing(EmployeeEntity::getEmployeeId))
            .map(employee -> new OrderPoolAssigneeVO(employee.getEmployeeId(), employee.getActualName())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO claim(OrderPoolClaimForm form, String key) {
        Long employeeId = requireAssignPermission();
        OrderPoolAssignForm assignment = new OrderPoolAssignForm();
        assignment.setOrderId(form.getOrderId());
        assignment.setVersion(form.getVersion());
        assignment.setSellerId(employeeId);
        return assignTo(assignment, key);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO assign(OrderPoolAssignForm form, String key) {
        requireAssignPermission();
        return assignTo(form, key);
    }

    private SalesOrderVO assignTo(OrderPoolAssignForm form, String key) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("orderId", form.getOrderId());
        fingerprint.put("sellerId", form.getSellerId());
        // 重试时版本可能已递增；幂等身份只绑定目标订单和目标业务员。
        var claim = idempotencyService.claim("ORDER_POOL_ASSIGN:" + form.getOrderId(), key, fingerprint);
        if (claim.replay()) {
            return idempotencyService.replay(claim, SalesOrderVO.class);
        }
        var order = salesOrderDao.lock(form.getOrderId());
        if (order == null || !ScmOrderSourceEnum.MALL.name().equals(order.getOrderSource()) || !Set
            .of(ScmOrderStatusEnum.DRAFT.name(), ScmOrderStatusEnum.PENDING.name(), ScmOrderStatusEnum.CONFIRMED.name())
            .contains(order.getStatus())) {
            throw new ScmBusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getSellerId() != null) {
            throw new ScmBusinessException(OrderErrorCode.ORDER_ALREADY_ASSIGNED);
        }
        if (!Objects.equals(form.getVersion(), order.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        EmployeeEntity assignee = employeeDao.selectById(form.getSellerId());
        if (assignee == null || Boolean.TRUE.equals(assignee.getDisabledFlag())
            || Boolean.TRUE.equals(assignee.getDeletedFlag()) || !canReceiveOrder(assignee)) {
            throw new ScmBusinessException(OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);
        }
        SalesOrderVO before = salesOrderQueryService.order(order);
        if (salesOrderDao.assignUnassignedMallOrder(order.getId(), order.getVersion(), form.getSellerId(),
            ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        SalesOrderVO after = salesOrderQueryService.order(salesOrderDao.selectById(order.getId()));
        operationLogs.record(order.getId(), ScmOrderOperationTypeEnum.ASSIGN, "指派业务员：" + assignee.getActualName(),
            before, after);
        idempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(), order.getId(), after);
        return after;
    }

    private Long requireAssignPermission() {
        var scope = dataScopeService.resolve();
        if (scope.getEmployeeId() == null
            || (!ScmDataScopeService.isAdministrator() && !ScmDataScopeService.hasPermission(OrderPermission.ASSIGN))) {
            throw new ScmDataScopeException();
        }
        return scope.getEmployeeId();
    }

    private boolean canReceiveOrder(EmployeeEntity employee) {
        if (Boolean.TRUE.equals(employee.getAdministratorFlag())) {
            return true;
        }
        var permission = loginManager.getUserPermission(employee.getEmployeeId());
        return permission != null && permission.getPermissionList() != null
            && permission.getPermissionList().contains(OrderPermission.QUERY);
    }
}
