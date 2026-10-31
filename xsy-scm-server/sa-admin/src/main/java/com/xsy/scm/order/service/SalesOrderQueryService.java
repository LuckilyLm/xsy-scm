package com.xsy.scm.order.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.json.ScmOperatorNameResolver;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.order.domain.entity.OrderOperationLogEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.manager.OrderEditPolicy;

import com.xsy.scm.order.domain.form.OrderLogQueryForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

import com.xsy.scm.order.domain.vo.OrderAddressSnapshotVO;
import com.xsy.scm.order.domain.vo.OrderLastPurchasePriceVO;
import com.xsy.scm.order.domain.vo.OrderOperationLogVO;
import com.xsy.scm.order.domain.vo.OrderRecentPriceVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;

import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.OrderOperationLogDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.dao.OrderTagDao;
import com.xsy.scm.order.domain.vo.OrderTagVO;

import com.xsy.scm.promotion.service.PromotionDiscountService;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.vo.PurchaseLastPriceVO;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_NOT_FOUND;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import java.util.stream.Collectors;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartPageUtil;

@Service
@RequiredArgsConstructor
public class SalesOrderQueryService {
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderTagDao orderTagDao;
    private final OrderAddressSnapshotDao orderAddressSnapshotDao;
    private final OrderOperationLogDao orderOperationLogDao;
    private final ScmOperatorNameResolver operatorNameResolver;
    private final MallCustomerAccountDao mallCustomerAccountDao;
    private final ScmDataScopeService dataScopeService;
    private final CustomerService customerService;
    private final PurchaseOrderItemDao purchaseOrderItemDao;

    /**
     * 订单详情带上优惠（只读）。订单域不重复实现优惠规则，直接引用营销域的读结果，让详情页能看到减免金额和所用券；没有优惠时为 null。
     */
    private final PromotionDiscountService promotionDiscountService;

    /**
     * 最近成交参考价取数条数区间：limit 最大 10。
     */
    private static final int RECENT_PRICE_MIN_LIMIT = 1;
    private static final int RECENT_PRICE_MAX_LIMIT = 10;

    public PageResult<SalesOrderVO> query(SalesOrderQueryForm salesOrderQueryForm) {
        ScmDataScopeContext dataScopeContext = dataScopeService.resolve();
        // 没有任何授权 id 时直接返回空分页，避免空集合拼成非法的 IN ()。
        if (dataScopeContext.getOrderSellerScope().isEmpty())
            return ScmDataScopeService.emptyPage(salesOrderQueryForm);
        var page = SmartPageUtil.convert2PageQuery(salesOrderQueryForm);
        var salesOrders = salesOrderDao.query(page, salesOrderQueryForm, dataScopeContext.getOrderSellerScope());
        var rows = salesOrders.stream().map(this::order).toList();
        if (!rows.isEmpty()) {
            Map<Long,
                List<OrderTagVO>> tags = orderTagDao.tagsByOrders(rows.stream().map(SalesOrderVO::getOrderId).toList())
                    .stream().collect(Collectors.groupingBy(OrderTagVO::getOrderId));
            rows.forEach(row -> row.setTags(tags.getOrDefault(row.getOrderId(), List.of())));
        }
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    public SalesOrderVO order(SalesOrderEntity salesOrder) {
        var salesOrderVO = new SalesOrderVO();
        BeanUtils.copyProperties(salesOrder, salesOrderVO);
        salesOrderVO.setOrderId(salesOrder.getId());
        // 改单资格由后端统一判定：前端按钮是否可点、写接口是否放行用同一套条件。
        var blockedReason = OrderEditPolicy.blockedReason(salesOrder);
        salesOrderVO.setEditable(blockedReason == null);
        salesOrderVO.setEditBlockedReason(blockedReason);
        return salesOrderVO;
    }

    /**
     * 详情读（HTTP 入口默认形态）：按当前调用者的订单业务员范围判定，越权按 30005 拒绝。
     *
     * <p>
     * 范围判定放在这里，新加的读入口就自动带范围；写路径需要不带范围的同一行数据，见 {@link #detailSnapshot(Long)}。
     */
    public SalesOrderDetailVO detail(Long orderId) {
        return detail(orderId, dataScopeService.resolve());
    }

    /**
     * 详情读 + 显式范围：调用方已解析过范围时用它，避免重复解析。
     *
     * <p>
     * 订单不存在按 {@code ORDER_NOT_FOUND} 处理；存在但负责人不在授权范围内按 30005 拒绝，否则猜 id 就能读到别人的整单（含价格与收货地址）。
     */
    public SalesOrderDetailVO detail(Long orderId, ScmDataScopeContext dataScopeContext) {
        var salesOrder = salesOrderDao.selectById(orderId);
        if (salesOrder == null)
            throw new ScmBusinessException(ORDER_NOT_FOUND);
        if (!dataScopeContext.getOrderSellerScope().allows(salesOrder.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return detailSnapshot(salesOrder);
    }

    /**
     * 详情快照（刻意不限定范围）：写路径在同一事务里取操作日志的前后数据，读的是自己刚写下的行；数据范围只约束读接口。
     */
    public SalesOrderDetailVO detailSnapshot(Long orderId) {
        var salesOrder = salesOrderDao.selectById(orderId);
        if (salesOrder == null)
            throw new ScmBusinessException(ORDER_NOT_FOUND);
        return detailSnapshot(salesOrder);
    }

    private SalesOrderDetailVO detailSnapshot(SalesOrderEntity salesOrder) {
        var salesOrderDetailVO = new SalesOrderDetailVO();
        BeanUtils.copyProperties(order(salesOrder), salesOrderDetailVO);
        salesOrderDetailVO.setItems(salesOrderItemDao.list(salesOrder.getId()).stream()
            .sorted(Comparator.comparing(SalesOrderItemEntity::getSortOrder).thenComparing(SalesOrderItemEntity::getId))
            .map(salesOrderItem -> {
                var salesOrderItemVO = new SalesOrderItemVO();
                BeanUtils.copyProperties(salesOrderItem, salesOrderItemVO);
                salesOrderItemVO.setItemId(salesOrderItem.getId());
                return salesOrderItemVO;
            }).toList());
        salesOrderDetailVO
            .setAddress(orderAddressSnapshotDao.list(salesOrder.getId()).stream().findFirst().map(addressSnapshot -> {
                var addressSnapshotVO = new OrderAddressSnapshotVO();
                BeanUtils.copyProperties(addressSnapshot, addressSnapshotVO);
                return addressSnapshotVO;
            }).orElse(null));
        salesOrderDetailVO.setDiscount(promotionDiscountService.getByOrder(salesOrder.getId()));
        salesOrderDetailVO.setGifts(promotionDiscountService.listGifts(salesOrder.getId()));
        return salesOrderDetailVO;
    }

    /**
     * 某客户某 SKU 的最近成交参考价（只读）：只供录单参考，不回算当前价格、不参与定价。
     *
     * <p>
     * 查询前校验客户归属，避免猜客户 ID 读取其他业务员的成交记录。
     */
    public List<OrderRecentPriceVO> recentPrices(Long customerId, Long skuId, int limit) {
        customerService.requireInCurrentScope(customerId);
        int n = Math.min(Math.max(limit, RECENT_PRICE_MIN_LIMIT), RECENT_PRICE_MAX_LIMIT);
        return salesOrderItemDao.recentPrices(customerId, skuId, n, dataScopeService.resolve().getOrderSellerScope());
    }

    /**
     * 某批商品规格的最近采购价（只读）：按采购员的授权范围取数，只供录单参考，不回算当前价格、不参与定价。
     *
     * <p>
     * 采购范围为空时失败关闭，返回空结果而不是全量。成本可见性由接口权限注解保证。
     */
    public List<OrderLastPurchasePriceVO> lastPurchasePrices(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty())
            return List.of();
        List<Long> distinctSkuIds = skuIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctSkuIds.isEmpty())
            return List.of();
        ScmValueScope purchaserScope = dataScopeService.resolve().getPurchaserScope();
        if (purchaserScope.isEmpty())
            return List.of();
        return purchaseOrderItemDao
            .latestPurchasePrices(distinctSkuIds, ScmPurchaseStatusEnum.committedNames(), purchaserScope).stream()
            .map(SalesOrderQueryService::lastPurchasePrice).toList();
    }

    private static OrderLastPurchasePriceVO lastPurchasePrice(PurchaseLastPriceVO row) {
        var vo = new OrderLastPurchasePriceVO();
        vo.setSkuId(row.getSkuId());
        vo.setPurchasePrice(row.getPurchasePrice());
        vo.setPurchaseUnit(row.getPurchaseUnit());
        vo.setPurchaseOrderNo(row.getPurchaseOrderNo());
        vo.setSubmittedAt(row.getSubmittedAt());
        vo.setSupplierName(row.getSupplierName());
        return vo;
    }

    /**
     * 操作日志列表：日志行没有归属列，范围经父订单生效（见 OrderOperationLogMapper.xml）。
     */
    public PageResult<OrderOperationLogVO> logs(OrderLogQueryForm logQueryForm) {
        ScmDataScopeContext dataScopeContext = dataScopeService.resolve();
        if (dataScopeContext.getOrderSellerScope().isEmpty())
            return ScmDataScopeService.emptyPage(logQueryForm);
        var page = SmartPageUtil.convert2PageQuery(logQueryForm);
        var operationLogs = orderOperationLogDao.query(page, logQueryForm, dataScopeContext.getOrderSellerScope());
        String mallCustomerPrefix = UserTypeEnum.MALL_CUSTOMER.getValue() + ":";
        var mallAccountIds = operatorIds(operationLogs, mallCustomerPrefix);
        var mallAccountNameById = mallAccountIds.isEmpty()
            ? Map.<Long, String>of()
            : mallCustomerAccountDao.listNamesByIds(mallAccountIds).stream()
                .collect(Collectors.toMap(account -> account.getAccountId(),
                    account -> account.getCustomerName() == null
                        ? "商城客户（账号 " + account.getAccountId() + "）"
                        : account.getCustomerName() + "（商城客户）",
                    (first, ignored) -> first));
        return SmartPageUtil.convert2PageResult(page, operationLogs.stream().map(operationLog -> {
            var operationLogVO = new OrderOperationLogVO();
            BeanUtils.copyProperties(operationLog, operationLogVO);
            operationLogVO.setLogId(operationLog.getId());
            operationLogVO.setOperatorName(
                operatorName(operationLog.getOperator(), mallAccountNameById, operatorNameResolver::display));
            return operationLogVO;
        }).toList());
    }

    static List<Long> operatorIds(List<OrderOperationLogEntity> logs, String prefix) {
        return logs.stream().map(OrderOperationLogEntity::getOperator).map(operator -> operatorId(operator, prefix))
            .filter(java.util.Objects::nonNull).distinct().toList();
    }

    static String operatorName(String operator, Map<Long, String> mallAccountNames,
        UnaryOperator<String> employeeDisplay) {
        if (operator == null) {
            return null;
        }
        int separator = operator.indexOf(':');
        if (separator < 0) {
            return operator;
        }
        Long id = operatorId(operator, operator.substring(0, separator + 1));
        if (id == null) {
            return operator;
        }
        if (operator.startsWith(UserTypeEnum.MALL_CUSTOMER.getValue() + ":")) {
            return mallAccountNames.getOrDefault(id, "商城客户（账号 " + id + "）");
        }
        return employeeDisplay.apply(operator);
    }

    private static Long operatorId(String operator, String prefix) {
        if (operator == null || !operator.startsWith(prefix) || operator.length() == prefix.length()) {
            return null;
        }
        try {
            return Long.valueOf(operator.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
