package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.dao.*;
import net.lab1024.sa.admin.module.scm.order.manager.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;

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
    private final ScmDataScopeService scopeService;

    /** 最近成交参考价取数条数区间（Wave 3 §7.5：limit 最大 10）。 */
    private static final int RECENT_PRICE_MIN_LIMIT = 1;
    private static final int RECENT_PRICE_MAX_LIMIT = 10;

    public PageResult<SalesOrderVO> query(SalesOrderQueryForm f) {
        ScmDataScopeContext scope = scopeService.resolve();
        // 维度里一个授权 id 都没有时直接给空分页：不必让数据库跑一次恒假的 IN，
        // 也避免把空集合送进 IN () 变成非法 SQL。
        if (scope.getOrderSellerScope().isEmpty()) return ScmDataScopeService.emptyPage(f);
        var page = SmartPageUtil.convert2PageQuery(f);
        var rows = orders.query(page, f, scope.getOrderSellerScope());
        return SmartPageUtil.convert2PageResult(page, rows.stream().map(this::order).toList());
    }

    public SalesOrderVO order(SalesOrderEntity row) {
        var v = new SalesOrderVO();
        BeanUtils.copyProperties(row, v);
        v.setOrderId(row.getId());
        return v;
    }

    /**
     * 详情读（HTTP 入口用的默认形态）：按当前调用者的订单业务员范围判定，越权 30005。
     *
     * <p>范围判定放在这里而不是各个 Controller 里，是为了让「新加的读入口自动带范围」。
     * 写路径需要的是同一行的<b>未收窄</b>快照，见 {@link #detailSnapshot(Long)}。
     */
    public SalesOrderDetailVO detail(Long id) {
        return detail(id, scopeService.resolve());
    }

    /**
     * 详情读 + 显式范围：调用方已经解析过范围时用它，避免同一请求里解析两次。
     *
     * <p>订单不存在仍按 {@code ORDER_NOT_FOUND} 处理；存在但负责人不在授权范围内按 30005 拒绝
     * ——否则列表收窄只是「藏起来」，猜到 id 直接调详情就能读到别人的整单（含价格与收货地址）。
     */
    public SalesOrderDetailVO detail(Long id, ScmDataScopeContext scope) {
        var row = orders.selectById(id);
        if (row == null) throw new ScmBusinessException(ORDER_NOT_FOUND);
        if (!scope.getOrderSellerScope().allows(row.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return detailSnapshot(row);
    }

    /**
     * 详情快照（<b>刻意不收范围</b>）：写路径在同一事务里取操作日志的前后镜像
     * （create/submit/confirm/cancel/actual-quantity，见 {@code SalesOrderService}），
     * 读的是自己刚写下的行，不是「查看别人的单」。数据范围只约束读接口，不约束写流程的自取快照。
     */
    public SalesOrderDetailVO detailSnapshot(Long id) {
        var row = orders.selectById(id);
        if (row == null) throw new ScmBusinessException(ORDER_NOT_FOUND);
        return detailSnapshot(row);
    }

    private SalesOrderDetailVO detailSnapshot(SalesOrderEntity row) {
        var v = new SalesOrderDetailVO();
        BeanUtils.copyProperties(order(row), v);
        v.setItems(items.list(row.getId()).stream().sorted(Comparator.comparing(SalesOrderItemEntity::getSortOrder).thenComparing(SalesOrderItemEntity::getId)).map(x -> {
            var i = new SalesOrderItemVO();
            BeanUtils.copyProperties(x, i);
            i.setItemId(x.getId());
            return i;
        }).toList());
        v.setAddress(addresses.list(row.getId()).stream().findFirst().map(x -> {
            var a = new OrderAddressSnapshotVO();
            BeanUtils.copyProperties(x, a);
            return a;
        }).orElse(null));
        return v;
    }

    /**
     * 某客户某 SKU 的最近成交参考价（Wave 3 §7.5，只读）：仅供录单旁证，不回算当前价格、不参与定价。
     *
     * <p>刻意不按调用者范围收窄：它是录单时的取价旁证入参，客户与 SKU 都由前端选择，
     * 真正的下单校验在 {@code SalesOrderService} 的归属判定里做（读不到该客户就建不了单）。
     */
    public List<OrderRecentPriceVO> recentPrices(Long customerId, Long skuId, int limit) {
        int n = Math.min(Math.max(limit, RECENT_PRICE_MIN_LIMIT), RECENT_PRICE_MAX_LIMIT);
        return items.recentPrices(customerId, skuId, n);
    }

    /**
     * 操作日志列表：日志行没有归属列，范围经父订单生效（见 OrderOperationLogMapper.xml）。
     */
    public PageResult<OrderOperationLogVO> logs(OrderLogQueryForm f) {
        ScmDataScopeContext scope = scopeService.resolve();
        if (scope.getOrderSellerScope().isEmpty()) return ScmDataScopeService.emptyPage(f);
        var page = SmartPageUtil.convert2PageQuery(f);
        var rows = logs.query(page, f, scope.getOrderSellerScope());
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
