package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.finance.service.FinanceReceivableService;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import com.xsy.scm.order.constant.ScmOrderReturnSourceEnum;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.OrderReturnReceiptItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.entity.OrderReturnItemEntity;
import com.xsy.scm.order.domain.entity.OrderReturnReceiptItemEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentAddForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentItemForm;
import com.xsy.scm.order.domain.form.OrderReturnPricePreviewForm;
import com.xsy.scm.order.domain.form.OrderReturnQueryForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.OrderReturnPricePreviewVO;
import com.xsy.scm.order.domain.vo.OrderReturnItemVO;
import com.xsy.scm.order.domain.vo.OrderReturnReceiptVO;
import com.xsy.scm.order.domain.vo.OrderReturnVO;
import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.manager.OrderValidator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_OVERRIDE_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_APPROVAL_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_ITEM_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_NOT_FOUND;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_ORDER_NOT_CONFIRMED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_QUANTITY_EXCEEDED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_STATUS_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_SKU_DUPLICATE;

/**
 * 售后写操作先在原订单上加锁，再锁退货单和退款单。
 */
@Service
@RequiredArgsConstructor
public class OrderReturnService {
    private final SalesOrderService salesOrderService;
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderReturnDao orderReturnDao;
    private final OrderReturnItemDao orderReturnItemDao;
    private final OrderReturnReceiptItemDao orderReturnReceiptItemDao;
    private final OrderRefundDao orderRefundDao;
    private final OrderNumberGenerator numbers;
    private final OrderIdempotencyService orderIdempotencyService;
    private final OrderOperationLogRecorder orderLogs;
    private final ScmDataScopeService dataScopeService;
    private final CustomerService customerService;
    private final OrderReturnPriceService returnPriceService;
    /**
     * 红字应收生成器。依赖方向是 order → finance，finance 只读订单与退货表、不反向引用订单域，因此不成环；生成失败则整笔批准回滚。
     */
    private final FinanceReceivableService financeReceivableService;

    public PageResult<OrderReturnVO> query(OrderReturnQueryForm orderReturnQueryForm) {
        ScmDataScopeContext dataScopeContext = dataScopeService.resolve();
        if (dataScopeContext.getOrderSellerScope().isEmpty())
            return ScmDataScopeService.emptyPage(orderReturnQueryForm);
        var page = SmartPageUtil.convert2PageQuery(orderReturnQueryForm);
        return SmartPageUtil.convert2PageResult(page,
            orderReturnDao.query(page, orderReturnQueryForm, dataScopeContext.getOrderSellerScope()));
    }

    private OrderReturnVO vo(OrderReturnEntity orderReturnEntity) {
        var orderReturnResultVO = new OrderReturnVO();
        BeanUtils.copyProperties(orderReturnEntity, orderReturnResultVO);
        orderReturnResultVO.setReturnId(orderReturnEntity.getId());
        // 客户名称用退货单自己的快照：独立退货没有订单可联，原单退货也不随订单改名变化。
        orderReturnResultVO.setCustomerName(orderReturnEntity.getCustomerNameSnapshot());
        if (orderReturnEntity.getOrderId() != null) {
            var order = salesOrderDao.selectById(orderReturnEntity.getOrderId());
            if (order != null && !Boolean.TRUE.equals(order.getDeleted())) {
                orderReturnResultVO.setOrderNo(order.getOrderNo());
            }
        }
        return orderReturnResultVO;
    }

    /**
     * 退货单详情读（HTTP 入口）：可见性跟随父订单的负责人范围。
     */
    public OrderReturnDetailVO detail(Long orderReturnId) {
        return detail(orderReturnId, dataScopeService.resolve());
    }

    /**
     * 退货单详情读 + 显式范围。原单退货跟随父订单的业务员，独立退货跟随客户的负责业务员； 都读不到时按 30005 处理，避免看得到退货单却看不到它的归属来源。
     */
    public OrderReturnDetailVO detail(Long orderReturnId, ScmDataScopeContext dataScopeContext) {
        var orderReturnEntity = orderReturnDao.selectById(orderReturnId);
        if (orderReturnEntity == null)
            throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        requireReturnVisible(orderReturnEntity, dataScopeContext.getOrderSellerScope());
        return detailSnapshot(orderReturnEntity);
    }

    /**
     * 不带范围的详情：审批、驳回、取消等写命令在同一事务里回读自己刚改过的单据；归属判定只属于读接口，写流程靠订单锁与状态机把关。
     */
    public OrderReturnDetailVO detailSnapshot(Long orderReturnId) {
        var orderReturnEntity = orderReturnDao.selectById(orderReturnId);
        if (orderReturnEntity == null)
            throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        return detailSnapshot(orderReturnEntity);
    }

    private OrderReturnDetailVO detailSnapshot(OrderReturnEntity orderReturnEntity) {
        var orderReturnResultVO = new OrderReturnDetailVO();
        BeanUtils.copyProperties(vo(orderReturnEntity), orderReturnResultVO);
        orderReturnResultVO.setItems(orderReturnItemDao.list(orderReturnEntity.getId()).stream().map(returnItem -> {
            var returnItemVO = new OrderReturnItemVO();
            BeanUtils.copyProperties(returnItem, returnItemVO);
            returnItemVO.setReturnItemId(returnItem.getId());
            returnItemVO.setReceivedQuantity(orderReturnReceiptItemDao.receivedQuantity(returnItem.getId()));
            returnItemVO.setReceiptAllocations(orderReturnReceiptItemDao.listByReturnItemId(returnItem.getId()).stream()
                .map(this::receiptAllocation).toList());
            // 商品与单位一律读退货行快照：独立退货没有订单明细可回查。
            returnItemVO.setProductName(returnItem.getSkuNameSnapshot());
            returnItemVO.setUnit(returnItem.getSaleUnitSnapshot());
            return returnItemVO;
        }).toList());
        return orderReturnResultVO;
    }

    private OrderReturnReceiptVO.Item receiptAllocation(OrderReturnReceiptItemEntity entity) {
        var item = new OrderReturnReceiptVO.Item();
        item.setReceiptItemId(entity.getId());
        item.setReturnItemId(entity.getReturnItemId());
        item.setSourceSalesOutMovementId(entity.getSourceSalesOutMovementId());
        item.setSourceOutboundItemId(entity.getSourceOutboundItemId());
        item.setDisposition(entity.getDisposition());
        item.setQuantity(entity.getQuantity());
        item.setUnit(entity.getUnitSnapshot());
        item.setStockQuantity(entity.getStockQuantity());
        item.setStockUnit(entity.getStockUnitSnapshot());
        item.setUnitCost(entity.getUnitCost());
        return item;
    }

    private void requireParentOrderVisible(Long orderId, ScmValueScope orderSellerScope) {
        var order = salesOrderDao.selectById(orderId);
        if (order == null || !orderSellerScope.allows(order.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }

    private void requireCustomerVisible(Long customerId, ScmValueScope orderSellerScope) {
        CustomerEntity customer = customerService.require(customerId);
        if (!orderSellerScope.allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 退货单没有自己的归属列：原单退货跟订单业务员，独立退货跟客户负责业务员。
     */
    private void requireReturnVisible(OrderReturnEntity orderReturnEntity, ScmValueScope orderSellerScope) {
        if (orderReturnEntity.getOrderId() != null) {
            requireParentOrderVisible(orderReturnEntity.getOrderId(), orderSellerScope);
        } else {
            requireCustomerVisible(orderReturnEntity.getCustomerId(), orderSellerScope);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO create(OrderReturnAddForm orderReturnAddForm, String key) {
        return create(orderReturnAddForm, key, true);
    }

    /**
     * 商城客户申请售后：与管理端共用同一条建单路径和数量上限，只跳过员工负责人范围。
     *
     * <p>
     * 仅已确认订单可退、累计申请不超过实数量等校验不变；归属由商城适配器按登录客户判定。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO createFromMall(OrderReturnAddForm orderReturnAddForm, String key) {
        return create(orderReturnAddForm, key, false);
    }

    private OrderReturnDetailVO create(OrderReturnAddForm orderReturnAddForm, String key,
        boolean enforceEmployeeScope) {
        var claim = orderIdempotencyService.claim("ORDER_RETURN_CREATE", key, orderReturnAddForm);
        if (claim.replay()) {
            // 重放也要过范围：幂等 scope 含 operator，B 拿不到 A 的记录；
            // 但同一个人换了负责范围后重放旧键仍会绕过写命令内的校验。
            // 商城入口已由 MallAfterSaleService 按登录客户判过归属，不再套员工范围。
            if (enforceEmployeeScope) {
                requireParentOrderVisible(orderReturnAddForm.getOrderId(),
                    dataScopeService.resolve().getOrderSellerScope());
            }
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        }
        var order = salesOrderService.lock(orderReturnAddForm.getOrderId());
        if (enforceEmployeeScope && !dataScopeService.resolve().getOrderSellerScope().allows(order.getSellerId()))
            throw new ScmDataScopeException();
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus()))
            throw new ScmBusinessException(ORDER_RETURN_ORDER_NOT_CONFIRMED);
        OrderValidator.reason(orderReturnAddForm.getReason(), ORDER_RETURN_ITEM_INVALID);
        var originals = salesOrderItemDao.list(order.getId()).stream()
            .collect(Collectors.toMap(SalesOrderItemEntity::getId, Function.identity()));
        var orderReturnEntity = new OrderReturnEntity();
        orderReturnEntity.setOrderId(order.getId());
        orderReturnEntity.setSourceType(ScmOrderReturnSourceEnum.ORDER.name());
        orderReturnEntity.setCustomerId(order.getCustomerId());
        orderReturnEntity.setCustomerNameSnapshot(order.getCustomerNameSnapshot());
        orderReturnEntity.setReturnNo(numbers.returned());
        orderReturnEntity.setStatus(ScmOrderReturnStatusEnum.PENDING.name());
        orderReturnEntity.setReason(orderReturnAddForm.getReason().trim());
        orderReturnEntity.setApprovedAmount(BigDecimal.ZERO.setScale(4));
        stamp(orderReturnEntity, true);
        var pending = new ArrayList<OrderReturnItemEntity>();
        var seen = new HashSet<Long>();
        for (var returnItem : orderReturnAddForm.getItems()) {
            var original = originals.get(returnItem.getOrderItemId());
            if (original == null || !seen.add(returnItem.getOrderItemId()))
                throw new ScmBusinessException(ORDER_RETURN_ITEM_INVALID);
            // 显式锁订单明细；所有退货命令都按先订单后明细的顺序加锁。
            salesOrderItemDao.lock(original.getId());
            var quantity = OrderValidator.decimal(returnItem.getRequestedQuantity(), true);
            if (original.getActualQuantity() == null || orderReturnItemDao.reserved(original.getId()).add(quantity)
                .compareTo(original.getActualQuantity()) > 0)
                throw new ScmBusinessException(ORDER_RETURN_QUANTITY_EXCEEDED);
            var row = new OrderReturnItemEntity();
            row.setOrderItemId(original.getId());
            row.setSkuId(original.getSkuId());
            row.setSkuNameSnapshot(original.getProductNameSnapshot());
            row.setSpecNameSnapshot(original.getSpecNameSnapshot());
            row.setSaleUnitSnapshot(original.getSaleUnitSnapshot());
            row.setRequestedQuantity(quantity);
            row.setLockedUnitPrice(original.getLockedUnitPrice());
            row.setApprovedAmount(BigDecimal.ZERO.setScale(4));
            row.setCreatedAt(OffsetDateTime.now());
            row.setCreatedBy(ScmOperator.current());
            row.setUpdatedAt(row.getCreatedAt());
            row.setUpdatedBy(row.getCreatedBy());
            pending.add(row);
        }
        if (pending.isEmpty())
            throw new ScmBusinessException(ORDER_RETURN_ITEM_INVALID);
        orderReturnDao.insert(orderReturnEntity);
        for (var row : pending) {
            row.setReturnId(orderReturnEntity.getId());
            orderReturnItemDao.insert(row);
        }
        var result = detailSnapshot(orderReturnEntity.getId());
        // 退货与取消、退款一样必须留操作日志，日志与业务变更使用同一事务。
        orderLogs.record(orderReturnEntity.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
            "退货单 " + orderReturnEntity.getReturnNo() + " 建单", null, Map.of("status", result.getStatus()));
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
            orderReturnEntity.getId(), result);
        return result;
    }

    /**
     * 独立退货建单：客户直接退回、找不到原单时按 SKU 建行，价格在建单时锁定。
     *
     * <p>
     * 原单退货的数量上限来自原订单实发量；独立退货没有原单可比，只要求数量为正，重复商品在建单时就拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO createIndependent(OrderReturnIndependentAddForm form, String key) {
        var claim = orderIdempotencyService.claim("ORDER_RETURN_INDEPENDENT_CREATE", key, form);
        if (claim.replay()) {
            requireCustomerVisible(form.getCustomerId(), dataScopeService.resolve().getOrderSellerScope());
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        }
        requireCustomerVisible(form.getCustomerId(), dataScopeService.resolve().getOrderSellerScope());
        CustomerEntity customer = customerService.require(form.getCustomerId());
        OrderValidator.reason(form.getReason(), ORDER_RETURN_ITEM_INVALID);
        var skuIds = form.getItems().stream().map(OrderReturnIndependentItemForm::getSkuId).distinct().toList();
        var skus = returnPriceService.skus(skuIds);
        var pending = new ArrayList<OrderReturnItemEntity>();
        var seen = new HashSet<String>();
        for (var item : form.getItems()) {
            var sku = skus.get(item.getSkuId());
            if (sku == null)
                throw new ScmBusinessException(ORDER_RETURN_ITEM_INVALID);
            var saleUnit = returnPriceService.saleUnit(sku, item.getSaleUnit());
            if (!seen.add(item.getSkuId() + "|" + saleUnit))
                throw new ScmBusinessException(ORDER_SKU_DUPLICATE);
            var quantity = OrderValidator.decimal(item.getRequestedQuantity(), true);
            var manualPrice = ScmDecimalStrings.parseScale4(item.getUnitPrice());
            if (manualPrice != null)
                OrderValidator.reason(item.getPriceReason(), ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
            var price = returnPriceService.require(form.getCustomerId(), sku.getSkuId(), saleUnit, manualPrice);
            var row = new OrderReturnItemEntity();
            row.setSkuId(sku.getSkuId());
            row.setSkuNameSnapshot(sku.getProductName());
            row.setSpecNameSnapshot(sku.getSpecName());
            row.setSaleUnitSnapshot(saleUnit);
            row.setPriceSource(price.source());
            row.setRequestedQuantity(quantity);
            row.setLockedUnitPrice(price.unitPrice());
            row.setApprovedAmount(BigDecimal.ZERO.setScale(4));
            row.setCreatedAt(OffsetDateTime.now());
            row.setCreatedBy(ScmOperator.current());
            row.setUpdatedAt(row.getCreatedAt());
            row.setUpdatedBy(row.getCreatedBy());
            pending.add(row);
        }
        var orderReturnEntity = new OrderReturnEntity();
        orderReturnEntity.setSourceType(ScmOrderReturnSourceEnum.INDEPENDENT.name());
        orderReturnEntity.setCustomerId(customer.getId());
        orderReturnEntity.setCustomerNameSnapshot(customer.getName());
        orderReturnEntity.setReturnNo(numbers.returned());
        orderReturnEntity.setStatus(ScmOrderReturnStatusEnum.PENDING.name());
        orderReturnEntity.setReason(form.getReason().trim());
        orderReturnEntity.setApprovedAmount(BigDecimal.ZERO.setScale(4));
        stamp(orderReturnEntity, true);
        orderReturnDao.insert(orderReturnEntity);
        for (var row : pending) {
            row.setReturnId(orderReturnEntity.getId());
            orderReturnItemDao.insert(row);
        }
        var result = detailSnapshot(orderReturnEntity.getId());
        recordLog(orderReturnEntity, "退货单 " + orderReturnEntity.getReturnNo() + " 建单", null,
            Map.of("status", result.getStatus()));
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
            orderReturnEntity.getId(), result);
        return result;
    }

    /**
     * 独立退货建单前的取价预览：只读，不建单；可见性校验与建单一致。
     */
    public List<OrderReturnPricePreviewVO> previewIndependentPrices(OrderReturnPricePreviewForm form) {
        requireCustomerVisible(form.getCustomerId(), dataScopeService.resolve().getOrderSellerScope());
        return returnPriceService.preview(form.getCustomerId(), form.getSkuIds());
    }

    /**
     * 原单退货与独立退货的日志主体不同：有订单挂订单，没有订单挂客户。
     */
    private void recordLog(OrderReturnEntity orderReturnEntity, String reason, Object before, Object after) {
        if (orderReturnEntity.getOrderId() == null) {
            orderLogs.recordForCustomer(orderReturnEntity.getCustomerId(), ScmOrderOperationTypeEnum.RETURN, reason,
                before, after);
        } else {
            orderLogs.record(orderReturnEntity.getOrderId(), ScmOrderOperationTypeEnum.RETURN, reason, before, after);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO approve(OrderReturnApproveForm orderReturnApproveForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_RETURN_APPROVE:" + orderReturnApproveForm.getReturnId(), key,
            orderReturnApproveForm);
        if (claim.replay()) {
            detail(orderReturnApproveForm.getReturnId());
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        }
        var orderReturnEntity = lock(orderReturnApproveForm.getReturnId());
        requireSource(orderReturnEntity, ScmOrderReturnSourceEnum.ORDER);
        SalesOrderService.version(orderReturnEntity.getVersion(), orderReturnApproveForm.getVersion());
        pending(orderReturnEntity);
        var rows = orderReturnItemDao.list(orderReturnEntity.getId());
        var quantities = new HashMap<Long, BigDecimal>();
        for (var returnItem : orderReturnApproveForm.getItems()) {
            var row = rows.stream()
                .filter(candidate -> candidate.getOrderItemId() != null
                    && candidate.getOrderItemId().equals(returnItem.getOrderItemId()))
                .findFirst().orElseThrow(() -> new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID));
            if (quantities.put(row.getId(), OrderValidator.decimal(returnItem.getApprovedQuantity(), false)) != null)
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        }
        return applyApproval(claim, orderReturnEntity, rows, quantities);
    }

    /**
     * 独立退货审批：明细没有原订单行，按退货行 ID 给批准数量，其余校验与原单退货一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO approveIndependent(OrderReturnIndependentApproveForm form, String key) {
        var claim = orderIdempotencyService.claim("ORDER_RETURN_APPROVE:" + form.getReturnId(), key, form);
        if (claim.replay()) {
            detail(form.getReturnId());
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        }
        var orderReturnEntity = lock(form.getReturnId());
        requireSource(orderReturnEntity, ScmOrderReturnSourceEnum.INDEPENDENT);
        SalesOrderService.version(orderReturnEntity.getVersion(), form.getVersion());
        pending(orderReturnEntity);
        var rows = orderReturnItemDao.list(orderReturnEntity.getId());
        var quantities = new HashMap<Long, BigDecimal>();
        for (var item : form.getItems()) {
            if (rows.stream().noneMatch(row -> row.getId().equals(item.getReturnItemId())) || quantities
                .put(item.getReturnItemId(), OrderValidator.decimal(item.getApprovedQuantity(), false)) != null)
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        }
        return applyApproval(claim, orderReturnEntity, rows, quantities);
    }

    /**
     * 审批的公共部分：两套入口只负责把「表单行」映射成「退货行 ID → 批准数量」，之后的校验、记账与日志完全共用。
     */
    private OrderReturnDetailVO applyApproval(OrderIdempotencyService.Claim claim, OrderReturnEntity orderReturnEntity,
        List<OrderReturnItemEntity> rows, Map<Long, BigDecimal> quantities) {
        if (quantities.size() != rows.size())
            throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        for (var row : rows) {
            var qty = quantities.get(row.getId());
            if (qty == null || qty.compareTo(row.getRequestedQuantity()) > 0)
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            row.setApprovedQuantity(qty);
            row.setApprovedAmount(OrderAmountCalculator.lineAmount(qty, row.getLockedUnitPrice()));
        }
        var total = OrderAmountCalculator
            .orderAmount(rows.stream().map(OrderReturnItemEntity::getApprovedAmount).toList());
        if (total.signum() <= 0)
            throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        for (var row : rows) {
            row.setUpdatedAt(OffsetDateTime.now());
            row.setUpdatedBy(ScmOperator.current());
            if (orderReturnItemDao.updateById(row) != 1)
                throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
        }
        orderReturnEntity.setApprovedAmount(total);
        orderReturnEntity.setStatus(ScmOrderReturnStatusEnum.APPROVED.name());
        orderReturnEntity.setApprovedAt(OffsetDateTime.now());
        stamp(orderReturnEntity, false);
        if (orderReturnDao.updateById(orderReturnEntity) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        var result = detailSnapshot(orderReturnEntity.getId());
        if (ScmOrderReturnSourceEnum.INDEPENDENT.name().equals(orderReturnEntity.getSourceType())) {
            // 独立退货没有原订单资金事实：只生成客户级红字，不生成退款单。
            BigDecimal redAmount = financeReceivableService
                .generateRedOnIndependentReturnApproved(orderReturnEntity.getId());
            recordLog(orderReturnEntity,
                "独立退货单 " + orderReturnEntity.getReturnNo() + " 审批通过，生成客户级红字 " + redAmount.toPlainString(),
                Map.of("status", ScmOrderReturnStatusEnum.PENDING.name()),
                Map.of("status", result.getStatus(), "approvedAmount", String.valueOf(total)));
        } else {
            approveOrderReturn(claim, orderReturnEntity, total, result);
            return result;
        }
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
            orderReturnEntity.getId(), result);
        return result;
    }

    /**
     * 原单退货审批的收尾：红字与退款共用订单确认时保存的优惠分摊和净额算法。
     */
    private void approveOrderReturn(OrderIdempotencyService.Claim claim, OrderReturnEntity orderReturnEntity,
        BigDecimal total, OrderReturnDetailVO result) {
        // 这一步仍在退货批准事务内；正常应收尚未生成时先算退款净额，签收后再补红字。
        BigDecimal refundAmount = financeReceivableService.generateRedOnReturnApproved(orderReturnEntity.getId());
        if (refundAmount.signum() > 0) {
            var refund = new OrderRefundEntity();
            refund.setRefundNo(numbers.refund());
            refund.setReturnId(orderReturnEntity.getId());
            refund.setOrderId(orderReturnEntity.getOrderId());
            refund.setCustomerId(orderReturnEntity.getCustomerId());
            refund.setRefundAmount(refundAmount);
            refund.setStatus(ScmOrderRefundStatusEnum.PENDING.name());
            refund.setCreatedAt(OffsetDateTime.now());
            refund.setUpdatedAt(refund.getCreatedAt());
            refund.setCreatedBy(ScmOperator.current());
            refund.setUpdatedBy(refund.getCreatedBy());
            orderRefundDao.insert(refund);
            orderLogs.record(orderReturnEntity.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                "退货单 " + orderReturnEntity.getReturnNo() + " 审批通过，并生成退款单 " + refund.getRefundNo(),
                Map.of("status", ScmOrderReturnStatusEnum.PENDING.name()), Map.of("status", result.getStatus(),
                    "approvedAmount", String.valueOf(total), "refundAmount", String.valueOf(refund.getRefundAmount())));
        } else {
            // 优惠抵满订单金额时退款净额为 0：退货仍然成立，只是没有可退的钱。
            // 不建退款单（refund_amount 恒 > 0），也不记 0 元记录，这是「允许退货、无需退款」，不是失败。
            orderLogs.record(orderReturnEntity.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                "退货单 " + orderReturnEntity.getReturnNo() + " 审批通过，退款净额为 0，无需退款",
                Map.of("status", ScmOrderReturnStatusEnum.PENDING.name()), Map.of("status", result.getStatus(),
                    "approvedAmount", String.valueOf(total), "refundAmount", refundAmount.toPlainString()));
        }
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
            orderReturnEntity.getId(), result);
    }

    private static void requireSource(OrderReturnEntity orderReturnEntity, ScmOrderReturnSourceEnum expected) {
        if (!expected.name().equals(orderReturnEntity.getSourceType()))
            throw new ScmBusinessException(ORDER_RETURN_STATUS_INVALID);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO reject(OrderReturnDecisionForm orderReturnDecisionForm, String key) {
        return decide(orderReturnDecisionForm, key, ScmOrderReturnStatusEnum.REJECTED, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO cancel(OrderReturnDecisionForm orderReturnDecisionForm, String key) {
        return decide(orderReturnDecisionForm, key, ScmOrderReturnStatusEnum.CANCELLED, true);
    }

    /**
     * 商城客户撤销售后申请：与管理端共用同一条撤销路径和待审核状态门槛，只跳过员工负责人范围。
     *
     * <p>
     * 只有 {@code PENDING} 可撤销；归属由商城适配器按退货单客户判定。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO cancelFromMall(OrderReturnDecisionForm orderReturnDecisionForm, String key) {
        return decide(orderReturnDecisionForm, key, ScmOrderReturnStatusEnum.CANCELLED, false);
    }

    private OrderReturnDetailVO decide(OrderReturnDecisionForm orderReturnDecisionForm, String key,
        ScmOrderReturnStatusEnum state, boolean enforceEmployeeScope) {
        // 商城撤销后版本会变化，因此指纹只含售后单与撤销原因；
        // 管理端驳回/撤销仍用原表单指纹和版本并发约束。
        Object requestFingerprint = !enforceEmployeeScope && state == ScmOrderReturnStatusEnum.CANCELLED
            ? mallCancelFingerprint(orderReturnDecisionForm)
            : orderReturnDecisionForm;
        var claim = orderIdempotencyService.claim(
            "ORDER_RETURN_" + state.name() + ":" + orderReturnDecisionForm.getReturnId(), key, requestFingerprint);
        if (claim.replay()) {
            // 商城重放已由 MallAfterSaleService 按客户归属判过，不再套员工范围。
            if (enforceEmployeeScope) {
                detail(orderReturnDecisionForm.getReturnId());
            }
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        }
        var orderReturnEntity = lock(orderReturnDecisionForm.getReturnId(), enforceEmployeeScope);
        SalesOrderService.version(orderReturnEntity.getVersion(), orderReturnDecisionForm.getVersion());
        pending(orderReturnEntity);
        OrderValidator.reason(orderReturnDecisionForm.getDecisionReason(), ORDER_RETURN_APPROVAL_INVALID);
        orderReturnEntity.setStatus(state.name());
        orderReturnEntity.setDecisionReason(orderReturnDecisionForm.getDecisionReason().trim());
        if (ScmOrderReturnStatusEnum.REJECTED == state)
            orderReturnEntity.setRejectedAt(OffsetDateTime.now());
        else
            orderReturnEntity.setCancelledAt(OffsetDateTime.now());
        stamp(orderReturnEntity, false);
        if (orderReturnDao.updateById(orderReturnEntity) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        var result = detailSnapshot(orderReturnEntity.getId());
        recordLog(orderReturnEntity, orderReturnDecisionForm.getDecisionReason().trim(),
            Map.of("status", ScmOrderReturnStatusEnum.PENDING.name()),
            Map.of("status", state.name(), "decisionReason", orderReturnEntity.getDecisionReason()));
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
            orderReturnEntity.getId(), result);
        return result;
    }

    static LinkedHashMap<String, Object> mallCancelFingerprint(OrderReturnDecisionForm form) {
        LinkedHashMap<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("returnId", form.getReturnId());
        fingerprint.put("decisionReason", form.getDecisionReason() == null ? null : form.getDecisionReason().trim());
        return fingerprint;
    }

    public OrderReturnEntity lockForReceipt(Long orderReturnId) {
        return lock(orderReturnId);
    }

    /**
     * 退货写命令统一先锁父订单，再锁明细和退货单，并校验父订单的业务员归属；不能只依赖详情页的数据权限。
     */
    private OrderReturnEntity lock(Long orderReturnId) {
        return lock(orderReturnId, true);
    }

    private OrderReturnEntity lock(Long orderReturnId, boolean enforceEmployeeScope) {
        var orderReturnEntity = orderReturnDao.selectById(orderReturnId);
        if (orderReturnEntity == null)
            throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        if (orderReturnEntity.getOrderId() != null) {
            var order = salesOrderService.lock(orderReturnEntity.getOrderId());
            if (enforceEmployeeScope && !dataScopeService.resolve().getOrderSellerScope().allows(order.getSellerId()))
                throw new ScmDataScopeException();
            for (var salesOrderItem : salesOrderItemDao.list(orderReturnEntity.getOrderId())) {
                salesOrderItemDao.lock(salesOrderItem.getId());
            }
        } else if (enforceEmployeeScope) {
            // 独立退货没有订单锁：范围校验换到客户负责业务员，避免绕过数据权限。
            requireCustomerVisible(orderReturnEntity.getCustomerId(), dataScopeService.resolve().getOrderSellerScope());
        }
        return orderReturnDao.lock(orderReturnId);
    }

    private void pending(OrderReturnEntity orderReturnEntity) {
        if (!ScmOrderReturnStatusEnum.PENDING.name().equals(orderReturnEntity.getStatus()))
            throw new ScmBusinessException(ORDER_RETURN_STATUS_INVALID);
    }

    private void stamp(OrderReturnEntity orderReturnEntity, boolean creating) {
        orderReturnEntity.setUpdatedAt(OffsetDateTime.now());
        orderReturnEntity.setUpdatedBy(ScmOperator.current());
        if (creating) {
            orderReturnEntity.setCreatedAt(orderReturnEntity.getUpdatedAt());
            orderReturnEntity.setCreatedBy(orderReturnEntity.getUpdatedBy());
        }
    }
}
