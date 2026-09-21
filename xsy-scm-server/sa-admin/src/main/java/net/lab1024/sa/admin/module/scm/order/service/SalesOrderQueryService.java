package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.dao.*;
import net.lab1024.sa.admin.module.scm.order.manager.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

@Service
@RequiredArgsConstructor
public class SalesOrderQueryService {
    private final SalesOrderDao orders;
    private final SalesOrderItemDao items;
    private final OrderAddressSnapshotDao addresses;
    private final OrderOperationLogDao logs;
    private final net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao employees;

    public PageResult<SalesOrderVO> query(SalesOrderQueryForm f) {
        var page = SmartPageUtil.convert2PageQuery(f);
        var rows = orders.query(page, f);
        return SmartPageUtil.convert2PageResult(page, rows.stream().map(this::order).toList());
    }

    public SalesOrderVO order(SalesOrderEntity row) {
        var v = new SalesOrderVO();
        BeanUtils.copyProperties(row, v);
        v.setOrderId(row.getId());
        return v;
    }

    public SalesOrderDetailVO detail(Long id) {
        var row = orders.selectById(id);
        if (row == null) throw new ScmBusinessException(ORDER_NOT_FOUND);
        var v = new SalesOrderDetailVO();
        BeanUtils.copyProperties(order(row), v);
        v.setItems(items.list(id).stream().sorted(Comparator.comparing(SalesOrderItemEntity::getSortOrder).thenComparing(SalesOrderItemEntity::getId)).map(x -> {
            var i = new SalesOrderItemVO();
            BeanUtils.copyProperties(x, i);
            i.setItemId(x.getId());
            return i;
        }).toList());
        v.setAddress(addresses.list(id).stream().findFirst().map(x -> {
            var a = new OrderAddressSnapshotVO();
            BeanUtils.copyProperties(x, a);
            return a;
        }).orElse(null));
        return v;
    }

    public PageResult<OrderOperationLogVO> logs(OrderLogQueryForm f) {
        var page = SmartPageUtil.convert2PageQuery(f);
        var rows = logs.query(page, f);
        var ids = rows.stream().map(OrderOperationLogEntity::getOperator).map(x -> Long.valueOf(x.substring(x.indexOf(':') + 1))).distinct().toList();
        var names = ids.isEmpty() ? Map.<Long, String>of() : employees.selectBatchIds(ids).stream().collect(Collectors.toMap(x -> x.getEmployeeId(), x -> x.getActualName()));
        return SmartPageUtil.convert2PageResult(page, rows.stream().map(x -> {
            var v = new OrderOperationLogVO();
            BeanUtils.copyProperties(x, v);
            v.setLogId(x.getId());
            v.setOperatorName(names.getOrDefault(Long.valueOf(x.getOperator().split(":")[1]), x.getOperator()));
            return v;
        }).toList());
    }
}
