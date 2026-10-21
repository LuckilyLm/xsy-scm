package com.xsy.scm.order.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.finance.domain.vo.CustomerCreditCheckVO;
import com.xsy.scm.inventory.service.InventoryReservationService;
import com.xsy.scm.mall.catalog.service.MallStockAvailabilityService;
import com.xsy.scm.notification.service.ScmNotificationService;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderQuantitySourceEnum;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.OrderBundleComponentDao;
import com.xsy.scm.order.dao.OrderExchangeLinkDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.OrderAddressSnapshotEntity;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.dto.MallOrderCreationResult;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderBatchDeleteForm;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.domain.form.OrderConfirmForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.OrderRepriceForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.form.SalesOrderUpdateForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderImportResultVO;
import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.order.manager.OrderBundleReservationPlanner;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.manager.OrderSnapshotFactory;
import com.xsy.scm.order.manager.OrderStateMachine;
import com.xsy.scm.order.manager.OrderValidator;
import com.xsy.scm.order.manager.SalesOrderItemChangeSet;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.pricing.constant.AgreementFormulaType;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.dao.ProductSpuDao;
import com.xsy.scm.promotion.service.PromotionDiscountService;
import com.xsy.scm.promotion.support.PromotionOrderFacts;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ACTUAL_NOT_ALLOWED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ACTUAL_QUANTITY_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ACTUAL_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_CANCEL_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_DELETE_STATE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_NOT_OWNED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_NOT_FOUND;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ORIGINAL_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_OVERRIDE_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_REPRICE_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RESERVE_STATE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_STATE_INVALID;

/**
 * 订单及其明细必须在同一个事务中修改。
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderAddressSnapshotDao orderAddressSnapshotDao;
    private final OrderBundleComponentDao orderBundleComponentDao;
    /** 删除换货草稿时清掉换货关联：残留关联会让退货单无法再建换货、详情对着已删草稿报错。 */
    private final OrderExchangeLinkDao orderExchangeLinkDao;
    private final OrderOperationLogRecorder orderLogs;
    private final CustomerService customerService;
    private final PriceResolver priceResolver;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final ProductSpuDao productSpuDao;
    private final OrderNumberGenerator numbers;
    private final OrderIdempotencyService orderIdempotencyService;
    /** 显式预留库存、取消时释放；库存变更通过库存域服务完成。 */
    private final InventoryReservationService inventoryReservationService;
    private final MallStockAvailabilityService mallStockAvailabilityService;
    private final OrderBundleSnapshotService bundleSnapshotService;
    private final SalesOrderQueryService salesOrderQueryService;
    /** SCM 数据范围的唯一解析入口；只在「能否对这户客户开单」这类归属判定上用。 */
    private final ScmDataScopeService dataScopeService;
    private final com.xsy.scm.common.scope.ScmWarehouseScopeGuard warehouseScopeGuard;
    private final com.xsy.scm.warehouse.service.WarehouseService warehouseService;
    private final OrderCreditService orderCreditService;
    private final OrderSettlementConfirmationPolicy settlementConfirmationPolicy;
    private final ScmNotificationService notificationService;

    /**
     * 优惠冻结由订单确认编排：营销域只按订单事实计算，订单域不重复实现优惠规则。
     */
    private final PromotionDiscountService promotionDiscountService;

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO create(SalesOrderAddForm salesOrderAddForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_CREATE", key, salesOrderAddForm);
        if (claim.replay()) {
            var result = orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
            // 重放也要按当前负责人范围放行：归属收窄后不能再凭旧幂等键拿到这单的快照
            salesOrderQueryService.detail(result.getOrderId());
            return result;
        }
        var result = createDraft(salesOrderAddForm);
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
                result.getOrderId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO createAndProgress(SalesOrderAddForm salesOrderAddForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_CREATE_AND_PROGRESS", key, salesOrderAddForm);
        if (claim.replay()) {
            var result = orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
            salesOrderQueryService.detail(result.getOrderId());
            return result;
        }
        var result = createDraft(salesOrderAddForm);
        result = submitOrder(result.getOrderId(), result.getVersion());
        if (result.getItems().stream()
                .allMatch(orderItem -> ScmProductTypeEnum.STANDARD.name().equals(orderItem.getProductTypeSnapshot()))) {
            result = confirmOrder(result.getOrderId(), result.getVersion());
        }
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
                result.getOrderId(), result);
        return result;
    }

    /** 商城客户下单先建单并提交，标品会尝试自动确认；同一幂等请求重试返回原订单，不因价格或地址后来变化而重新下单。库存预留需在到货后另外执行。 */
    @Transactional(rollbackFor = Exception.class)
    public MallOrderCreationResult createFromMall(SalesOrderAddForm salesOrderAddForm, Object idempotencyRequest,
            String key) {
        var claim = orderIdempotencyService.claim("ORDER_CREATE_MALL", key, idempotencyRequest);
        if (claim.replay()) {
            return new MallOrderCreationResult(orderIdempotencyService.replay(claim, SalesOrderDetailVO.class), true);
        }
        var result = createDraft(salesOrderAddForm, false);
        result = submitOrderForMall(result.getOrderId(), result.getVersion());
        if (result.getItems().stream()
                .allMatch(orderItem -> ScmProductTypeEnum.STANDARD.name().equals(orderItem.getProductTypeSnapshot()))) {
            result = confirmOrderForMall(result.getOrderId(), result.getVersion());
        }
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
                result.getOrderId(), result);
        return new MallOrderCreationResult(result, false);
    }

    /** 相同幂等键和请求指纹已成功下单时，直接返回原订单，不再按当前价格或地址重复校验。不同请求指纹必须按新请求处理或报冲突。 */
    public Optional<MallOrderCreationResult> replayMallOrder(Object idempotencyRequest, String key) {
        return orderIdempotencyService.peekCompleted("ORDER_CREATE_MALL", key, idempotencyRequest).map(
                claim -> new MallOrderCreationResult(orderIdempotencyService.replay(claim, SalesOrderDetailVO.class),
                        true));
    }

    /**
     * 商城取消的幂等指纹：只绑定订单与原因（客户身份已由幂等 scope 里的操作者体现）。
     *
     * <p>
     * 用 {@link LinkedHashMap} 而不是 {@code Map.of}：后者的迭代顺序在不同 JVM 之间会变， 序列化后哈希跟着漂移，重启后就认不出同一个键了。包内可见，便于直接验证指纹稳定性。
     */
    static Map<String, Object> cancelFingerprint(OrderCancelForm orderCancelForm) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("orderId", orderCancelForm.getOrderId());
        fingerprint.put("reason", orderCancelForm.getReason() == null ? null : orderCancelForm.getReason().trim());
        return fingerprint;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderImportResultVO importOrders(List<SalesOrderAddForm> forms, String fileHash, String key,
            int totalRows) {
        var request = Map.of("fileHash", fileHash, "forms", forms);
        var claim = orderIdempotencyService.claim("ORDER_IMPORT", key, request);
        if (claim.replay())
            return orderIdempotencyService.replay(claim, SalesOrderImportResultVO.class);
        var result = new SalesOrderImportResultVO();
        result.setTotalRows(totalRows);
        result.setTotalOrders(forms.size());
        var imported = new ArrayList<SalesOrderDetailVO>();
        for (int index = 0; index < forms.size(); index++) {
            try {
                var order = createDraft(forms.get(index));
                order = submitOrder(order.getOrderId(), order.getVersion());
                if (order.getItems().stream().allMatch(
                        orderItem -> ScmProductTypeEnum.STANDARD.name().equals(orderItem.getProductTypeSnapshot())))
                    order = confirmOrder(order.getOrderId(), order.getVersion());
                imported.add(order);
            } catch (ScmBusinessException | org.springframework.dao.DataIntegrityViolationException exception) {
                // Let the exception cross the transaction proxy before the import service formats errors.
                throw new ImportOrderException(index, exception);
            }
        }
        result.setOrders(imported);
        result.setConfirmedOrders((int) imported.stream()
                .filter(importedOrder -> ScmOrderStatusEnum.CONFIRMED.name().equals(importedOrder.getStatus()))
                .count());
        result.setPendingOrders((int) imported.stream()
                .filter(importedOrder -> ScmOrderStatusEnum.PENDING.name().equals(importedOrder.getStatus())).count());
        orderIdempotencyService.complete(claim, "SALES_ORDER_IMPORT", null, result);
        return result;
    }

    public static final class ImportOrderException extends RuntimeException {
        private final int orderIndex;

        public ImportOrderException(int orderIndex, RuntimeException cause) {
            super(cause);
            this.orderIndex = orderIndex;
        }

        public int getOrderIndex() {
            return orderIndex;
        }
    }

    private SalesOrderDetailVO createDraft(SalesOrderAddForm salesOrderAddForm) {
        return createDraft(salesOrderAddForm, true);
    }

    /**
     * @param enforceEmployeeScope
     *            是否套用员工负责人范围；商城入口传 {@code false}，由适配器按登录客户判定归属
     */
    private SalesOrderDetailVO createDraft(SalesOrderAddForm salesOrderAddForm, boolean enforceEmployeeScope) {
        OrderValidator.draft(salesOrderAddForm);
        var customer = customerService.requireTradable(salesOrderAddForm.getCustomerId());
        // 新建订单前必须确认当前调用者能读取该客户；订单负责人取自客户快照，
        // 只收窄列表等于「看不见但仍然能往别人名下塞单」，行级范围就不成立；因此新建入口按同一范围判定。
        // 分配权（scm:customer:assign）与全量订单范围同等放行：主管刚把客户指定给某人，
        // 就该能替他录单，否则「主管建客户 + 指定负责人」这条路会把主管自己挡在门外。
        if (enforceEmployeeScope && !dataScopeService.resolve().getOrderSellerScope().allows(customer.getSellerId())
                && !ScmDataScopeService.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM))
            throw new ScmDataScopeException();
        validateOriginal(salesOrderAddForm);
        if (salesOrderAddForm.getItems().stream().anyMatch(orderItemForm -> orderItemForm.getItemId() != null))
            throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
        Long selectedWarehouse = selectedWarehouseForCreate(salesOrderAddForm);
        if (enforceEmployeeScope && selectedWarehouse != null) {
            warehouseScopeGuard.require(selectedWarehouse);
            warehouseService.require(selectedWarehouse);
        }
        var rows = materialize(salesOrderAddForm);
        var salesOrder = new SalesOrderEntity();
        OrderSnapshotFactory.customer(salesOrder, customer);
        var settlementCustomer = customerService.requireSettlementCustomer(customer);
        salesOrder.setSettlementCustomerId(settlementCustomer.getId());
        salesOrder.setSettlementCustomerNameSnapshot(settlementCustomer.getName());
        salesOrder.setOrderNo(numbers.order());
        salesOrder.setStatus(ScmOrderStatusEnum.DRAFT.name());
        header(salesOrder, salesOrderAddForm);
        // 客户预选券只随商城建单持久化；后台录单即使误带该字段也不落库， 后台用券一律由确认命令显式给出。
        if (ScmOrderSourceEnum.MALL.name().equals(salesOrder.getOrderSource())) {
            // 本次商城下单选定的配送仓是不可变订单事实，不能在确认时重新按客户当前地址猜测。
            salesOrder.setMallDeliveryWarehouseId(salesOrderAddForm.getMallDeliveryWarehouseId());
            salesOrder.setMallCouponInstanceId(salesOrderAddForm.getMallCouponInstanceId());
            // 结算方式与预选券同源：只认商城渠道，后台录单不采信请求体里的结算方式。
            salesOrder.setSettlementMethod(salesOrderAddForm.getSettlementMethod());
        } else {
            // 后台与补单也冻结明确选择的配送仓；沿用既有订单仓库字段，不新建平行事实。
            salesOrder.setMallDeliveryWarehouseId(selectedWarehouse);
        }
        salesOrder.setOrderedTotalAmount(total(rows));
        stamp(salesOrder, true);
        salesOrderDao.insert(salesOrder);
        for (var row : rows)
            insert(salesOrder.getId(), row);
        var addressSnapshot = new OrderAddressSnapshotEntity();
        BeanUtils.copyProperties(salesOrderAddForm.getAddress(), addressSnapshot);
        addressSnapshot.setOrderId(salesOrder.getId());
        addressSnapshot.setCustomerId(salesOrder.getCustomerId());
        addressSnapshot.setCreatedAt(OffsetDateTime.now());
        addressSnapshot.setCreatedBy(ScmOperator.current());
        if (addressSnapshot.getAddress() != null && !addressSnapshot.getAddress().isBlank()
                && Objects.equals(addressSnapshot.getAddress(), customer.getAddress())) {
            addressSnapshot.setProvinceCode(customer.getProvinceCode());
            addressSnapshot.setProvinceName(customer.getProvinceName());
            addressSnapshot.setCityCode(customer.getCityCode());
            addressSnapshot.setCityName(customer.getCityName());
            addressSnapshot.setDistrictCode(customer.getDistrictCode());
            addressSnapshot.setDistrictName(customer.getDistrictName());
            if (customer.getGeomCrs() != null) {
                addressSnapshot.setLongitude(customer.getLongitude());
                addressSnapshot.setLatitude(customer.getLatitude());
                addressSnapshot.setGeomCrs(customer.getGeomCrs());
            }
        }
        orderAddressSnapshotDao.insert(addressSnapshot);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.CREATE, null, null, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO update(SalesOrderUpdateForm salesOrderUpdateForm) {
        OrderValidator.draft(salesOrderUpdateForm);
        var salesOrder = lock(salesOrderUpdateForm.getOrderId());
        requireOrderSellerScope(salesOrder);
        version(salesOrder.getVersion(), salesOrderUpdateForm.getVersion());
        OrderStateMachine.editable(salesOrder.getStatus());
        // Customer and address identity belong to the creation snapshot; editing cannot replace it.
        if (!Objects.equals(salesOrder.getCustomerId(), salesOrderUpdateForm.getCustomerId()))
            throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
        customerService.requireTradable(salesOrder.getCustomerId());
        validateOriginal(salesOrderUpdateForm);
        var oldAddress = orderAddressSnapshotDao.list(salesOrder.getId()).getFirst();
        if (!Objects.equals(oldAddress.getReceiverName(), salesOrderUpdateForm.getAddress().getReceiverName())
                || !Objects.equals(oldAddress.getReceiverPhone(), salesOrderUpdateForm.getAddress().getReceiverPhone())
                || !Objects.equals(oldAddress.getAddress(), salesOrderUpdateForm.getAddress().getAddress()))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        var existing = salesOrderItemDao.list(salesOrder.getId());
        Map<Long, SalesOrderItemEntity> existingById = existing.stream()
                .collect(Collectors.toMap(SalesOrderItemEntity::getId, Function.identity()));
        var requested = materialize(salesOrderUpdateForm);
        var changes = SalesOrderItemChangeSet.between(existing, requested);
        // Remove before insert so a removed SKU may be added again without violating the active unique index.
        for (var row : changes.removed())
            removeItem(row);
        for (var row : changes.updated()) {
            SalesOrderItemEntity previous = existingById.get(row.getId());
            if (Boolean.TRUE.equals(previous.getManualPriceOverride())
                    && !Boolean.TRUE.equals(row.getManualPriceOverride())) {
                throw new ScmBusinessException(ORDER_REPRICE_REQUIRED);
            }
            if (!Boolean.TRUE.equals(previous.getManualPriceOverride())
                    && !Boolean.TRUE.equals(row.getManualPriceOverride())) {
                row.setDraftUnitPrice(previous.getDraftUnitPrice());
                row.setDraftPriceSource(previous.getDraftPriceSource());
                row.setDraftPriceSourceId(previous.getDraftPriceSourceId());
                row.setPriceBasisSnapshot(previous.getPriceBasisSnapshot());
                row.setOrderedLineAmount(
                        OrderAmountCalculator.lineAmount(row.getOrderedQuantity(), row.getDraftUnitPrice()));
            }
            row.setOrderId(salesOrder.getId());
            saveItem(row);
        }
        for (var row : changes.inserted())
            insert(salesOrder.getId(), row);
        header(salesOrder, salesOrderUpdateForm);
        salesOrder.setOrderedTotalAmount(total(requested));
        save(salesOrder);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.UPDATE, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO submit(OrderVersionForm orderVersionForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_SUBMIT:" + orderVersionForm.getOrderId(), key,
                orderVersionForm);
        if (claim.replay()) {
            // 重放也要过范围：幂等 scope 含 operator，A 与 B 的键本就不同（跨人重放不可能），
            // 但同一个人换了负责范围后再重放同一个键仍会绕开写命令里的守卫。detail 会重新判范围。
            salesOrderQueryService.detail(orderVersionForm.getOrderId());
            return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        }
        var result = submitOrder(orderVersionForm.getOrderId(), orderVersionForm.getVersion());
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
                result.getOrderId(), result);
        return result;
    }

    private SalesOrderDetailVO submitOrder(Long orderId, Integer expectedVersion) {
        return submitOrder(orderId, expectedVersion, true);
    }

    private SalesOrderDetailVO submitOrderForMall(Long orderId, Integer expectedVersion) {
        return submitOrder(orderId, expectedVersion, false);
    }

    private SalesOrderDetailVO submitOrder(Long orderId, Integer expectedVersion, boolean enforceEmployeeScope) {
        var salesOrder = lock(orderId);
        requireEmployeeScopeIfNeeded(salesOrder, enforceEmployeeScope);
        version(salesOrder.getVersion(), expectedVersion);
        OrderStateMachine.transition(salesOrder.getStatus(), ScmOrderStatusEnum.PENDING.name());
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        var rows = salesOrderItemDao.list(salesOrder.getId());
        var automatic = rows.stream().filter(orderItem -> !orderItem.getManualPriceOverride())
                .map(SalesOrderItemEntity::getSkuId).toList();
        customerService.requireTradable(salesOrder.getCustomerId());
        var resolved = priceResolver
                .requireResolvable(salesOrder.getCustomerId(), automatic, OffsetDateTime.now(),
                        salesOrder.getMallDeliveryWarehouseId())
                .stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
        var manual = rows.stream().filter(SalesOrderItemEntity::getManualPriceOverride)
                .map(SalesOrderItemEntity::getSkuId).toList();
        var manualResolved = priceResolver.resolve(salesOrder.getCustomerId(), manual, OffsetDateTime.now(),
                salesOrder.getMallDeliveryWarehouseId());
        if (manualResolved.stream().anyMatch(resolvedPrice -> !resolvedPrice.isSellable()))
            throw new ScmBusinessException(com.xsy.scm.pricing.constant.PricingErrorCode.SKU_NOT_SELLABLE);
        for (var row : rows) {
            if (!row.getManualPriceOverride()) {
                if (!quoteMatches(row, resolved.get(row.getSkuId()))) {
                    throw new ScmBusinessException(ORDER_REPRICE_REQUIRED);
                }
            } else {
                OrderValidator.reason(row.getManualPriceReason(), ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                if (row.getDraftUnitPrice() == null)
                    throw new ScmBusinessException(ORDER_PRICE_INVALID);
            }
            row.setLockedUnitPrice(row.getDraftUnitPrice());
            row.setLockedPriceSource(row.getDraftPriceSource());
            row.setLockedPriceSourceId(row.getDraftPriceSourceId());
            if (row.getPriceBasisSnapshot() == null && !row.getManualPriceOverride()) {
                row.setPriceBasisSnapshot(resolved.get(row.getSkuId()).getPriceBasisSnapshot());
            }
            row.setOrderedLineAmount(
                    OrderAmountCalculator.lineAmount(row.getOrderedQuantity(), row.getLockedUnitPrice()));
            if (ScmProductTypeEnum.STANDARD.name().equals(row.getProductTypeSnapshot())) {
                row.setActualQuantity(row.getOrderedQuantity());
                row.setActualQuantitySource(ScmOrderQuantitySourceEnum.SYSTEM.name());
            }
            saveItem(row);
        }
        salesOrder.setOrderedTotalAmount(total(rows));
        salesOrder.setStatus(ScmOrderStatusEnum.PENDING.name());
        salesOrder.setSubmittedAt(OffsetDateTime.now());
        save(salesOrder);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.SUBMIT, null, before, result);
        return result;
    }

    private static boolean quoteMatches(SalesOrderItemEntity row, ResolvedPriceVO current) {
        if (current == null || current.getUnitPrice() == null || row.getDraftUnitPrice() == null
                || current.getUnitPrice().compareTo(row.getDraftUnitPrice()) != 0
                || !Objects.equals(row.getDraftPriceSource(),
                        current.getPriceSource() == null ? null : current.getPriceSource().name())
                || !Objects.equals(row.getDraftPriceSourceId(), current.getSourceRecordId())) {
            return false;
        }
        Map<String, Object> before = row.getPriceBasisSnapshot();
        Map<String, Object> after = current.getPriceBasisSnapshot();
        if (before == null || after == null) {
            if (before == null && after != null && AgreementFormulaType.FIXED.name().equals(after.get("formulaType"))) {
                return true;
            }
            return before == null && after == null;
        }
        for (String key : List.of("formulaType", "agreementVersion", "formulaParameter", "basisUnitPrice",
                "warehouseId", "stockUnit", "stockUnitCost", "stockUnitsPerSaleUnit", "bundleCostComponents")) {
            if (!Objects.equals(before.get(key), after.get(key))) {
                return false;
            }
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO reprice(OrderRepriceForm form) {
        SalesOrderEntity order = lock(form.getOrderId());
        requireOrderSellerScope(order);
        version(order.getVersion(), form.getVersion());
        if ((!ScmOrderStatusEnum.DRAFT.name().equals(order.getStatus())
                && !ScmOrderStatusEnum.PENDING.name().equals(order.getStatus()))
                || salesOrderDao.hasPriceFreezeBlockingFact(order.getId())) {
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        }
        var before = salesOrderQueryService.detailSnapshot(order.getId());
        List<SalesOrderItemEntity> rows = salesOrderItemDao.list(order.getId());
        List<Long> skuIds = rows.stream().filter(row -> !Boolean.TRUE.equals(row.getManualPriceOverride()))
                .map(SalesOrderItemEntity::getSkuId).toList();
        Map<Long,
                ResolvedPriceVO> current = priceResolver
                        .requireResolvable(order.getCustomerId(), skuIds, OffsetDateTime.now(),
                                order.getMallDeliveryWarehouseId())
                        .stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
        for (SalesOrderItemEntity row : rows) {
            if (Boolean.TRUE.equals(row.getManualPriceOverride())) {
                continue;
            }
            OrderSnapshotFactory.applyPrice(row, current.get(row.getSkuId()));
            row.setOrderedLineAmount(
                    OrderAmountCalculator.lineAmount(row.getOrderedQuantity(), row.getDraftUnitPrice()));
            if (ScmOrderStatusEnum.PENDING.name().equals(order.getStatus())) {
                row.setLockedUnitPrice(row.getDraftUnitPrice());
                row.setLockedPriceSource(row.getDraftPriceSource());
                row.setLockedPriceSourceId(row.getDraftPriceSourceId());
            }
            saveItem(row);
        }
        order.setOrderedTotalAmount(total(rows));
        save(order);
        SalesOrderDetailVO result = salesOrderQueryService.detailSnapshot(order.getId());
        log(order.getId(), ScmOrderOperationTypeEnum.REPRICE, form.getReason().trim(), before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO actualQuantity(OrderActualQuantityForm orderActualQuantityForm, String key) {
        var claim = orderIdempotencyService.claim(
                "ORDER_ACTUAL:" + orderActualQuantityForm.getOrderId() + ":" + orderActualQuantityForm.getItemId(), key,
                orderActualQuantityForm);
        if (claim.replay()) {
            salesOrderQueryService.detail(orderActualQuantityForm.getOrderId());
            return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        }
        var salesOrder = lock(orderActualQuantityForm.getOrderId());
        requireOrderSellerScope(salesOrder);
        OrderStateMachine.actualQuantity(salesOrder.getStatus());
        var row = salesOrderItemDao.list(salesOrder.getId()).stream()
                .filter(salesOrderItem -> Objects.equals(salesOrderItem.getId(), orderActualQuantityForm.getItemId()))
                .findFirst().orElseThrow(() -> new ScmBusinessException(ORDER_ITEM_NOT_OWNED));
        if (!ScmProductTypeEnum.NON_STANDARD.name().equals(row.getProductTypeSnapshot()))
            throw new ScmBusinessException(ORDER_ACTUAL_NOT_ALLOWED);
        if (!Objects.equals(row.getVersion(), orderActualQuantityForm.getVersion()))
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
        OrderValidator.reason(orderActualQuantityForm.getReason(), ORDER_ACTUAL_REASON_REQUIRED);
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        row.setActualQuantity(OrderValidator.decimal(orderActualQuantityForm.getActualQuantity(), true));
        row.setActualQuantitySource(ScmOrderQuantitySourceEnum.MANUAL.name());
        row.setActualQuantityReason(orderActualQuantityForm.getReason().trim());
        saveItem(row);
        // Advance the aggregate version too: stale confirm forms must refresh after any item change.
        save(salesOrder);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.ACTUAL_QUANTITY, orderActualQuantityForm.getReason(), before,
                result);
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
                salesOrder.getId(), result);
        return result;
    }

    public CustomerCreditCheckVO creditCheck(Long orderId) {
        // 先走订单详情的 seller scope，再读取任何结算主体或敞口信息，避免用不可见订单探测集团授信。
        salesOrderQueryService.detail(orderId);
        var order = salesOrderDao.selectById(orderId);
        if (order == null || !dataScopeService.resolve().getOrderSellerScope().allows(order.getSellerId())) {
            throw new ScmDataScopeException();
        }
        var rows = salesOrderItemDao.list(orderId);
        var amount = OrderAmountCalculator.orderAmount(rows.stream().map(row -> {
            if (row.getActualQuantity() == null || row.getActualQuantity().signum() <= 0) {
                throw new ScmBusinessException(ORDER_ACTUAL_QUANTITY_REQUIRED);
            }
            return OrderAmountCalculator.lineAmount(row.getActualQuantity(), row.getLockedUnitPrice());
        }).toList());
        return orderCreditService.checkSettlement(order.getSettlementCustomerId(), amount);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO confirm(OrderVersionForm orderVersionForm, String key) {
        OrderConfirmForm confirmForm = new OrderConfirmForm();
        confirmForm.setOrderId(orderVersionForm.getOrderId());
        confirmForm.setVersion(orderVersionForm.getVersion());
        return confirm(confirmForm, key);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO confirm(OrderConfirmForm orderVersionForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_CONFIRM:" + orderVersionForm.getOrderId(), key,
                orderVersionForm);
        if (claim.replay()) {
            salesOrderQueryService.detail(orderVersionForm.getOrderId());
            return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        }
        var result = confirmOrder(orderVersionForm.getOrderId(), orderVersionForm.getVersion(), orderVersionForm);
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
                result.getOrderId(), result);
        return result;
    }

    private SalesOrderDetailVO confirmOrder(Long orderId, Integer expectedVersion) {
        return confirmOrder(orderId, expectedVersion, null, true);
    }

    private SalesOrderDetailVO confirmOrderForMall(Long orderId, Integer expectedVersion) {
        return confirmOrder(orderId, expectedVersion, null, false);
    }

    private SalesOrderDetailVO confirmOrder(Long orderId, Integer expectedVersion, OrderConfirmForm confirmation) {
        return confirmOrder(orderId, expectedVersion, confirmation, true);
    }

    private SalesOrderDetailVO confirmOrder(Long orderId, Integer expectedVersion, OrderConfirmForm confirmation,
            boolean enforceEmployeeScope) {
        var salesOrder = lock(orderId);
        version(salesOrder.getVersion(), expectedVersion);
        OrderStateMachine.transition(salesOrder.getStatus(), ScmOrderStatusEnum.CONFIRMED.name());
        requireEmployeeScopeIfNeeded(salesOrder, enforceEmployeeScope);
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        var rows = salesOrderItemDao.list(salesOrder.getId());
        for (var row : rows) {
            if (row.getActualQuantity() == null || row.getActualQuantity().signum() <= 0)
                throw new ScmBusinessException(ORDER_ACTUAL_QUANTITY_REQUIRED);
            boolean bundle = bundleSnapshotService.freeze(row, salesOrder.getMallDeliveryWarehouseId());
            if (ScmOrderSourceEnum.MALL.name().equals(salesOrder.getOrderSource()) && !bundle) {
                if (salesOrder.getMallDeliveryWarehouseId() == null) {
                    throw new ScmBusinessException(VALIDATION_ERROR);
                }
                var conversion = mallStockAvailabilityService.requireConversion(salesOrder.getMallDeliveryWarehouseId(),
                        row.getSkuId(), row.getSaleUnitSnapshot());
                row.setMallStockUnitSnapshot(conversion.stockUnit());
                row.setMallStockUnitsPerSaleUnit(conversion.stockUnitsPerSaleUnit());
                com.xsy.scm.inventory.service.InventorySaleUnitConverter.toStockQuantity(row.getActualQuantity(),
                        conversion.stockUnit(), conversion.stockUnitsPerSaleUnit());
            }
            row.setSettlementLineAmount(
                    OrderAmountCalculator.lineAmount(row.getActualQuantity(), row.getLockedUnitPrice()));
            saveItem(row);
        }
        // 组合组成规格及售价分摊已在 freeze() 冻结，但确认订单不自动占库存。
        // 组成 SKU 的实际预留统一走 reserveStock() 显式命令，支持先采购收货后再履约。
        salesOrder.setSettlementTotalAmount(OrderAmountCalculator
                .orderAmount(rows.stream().map(SalesOrderItemEntity::getSettlementLineAmount).toList()));
        // 优惠冻结并入确认命令，而不是让客户端确认后再单独调一次：否则会出现
        // 「订单已确认、优惠却没冻结」的中间态。行基础金额取 ordered_line_amount
        // （下单量 × 锁定单价），该口径由负责人 2026-10-03 确认。
        // 券来源：确认表单显式给出的优先；表单为空时回退到商城客户下单时的预选券
        // （仅 MALL 来源订单可能有）。冻结会重验券的归属、状态、模板生效与门槛，
        // 预选券在确认前已失效的，确认按券校验规则失败，由操作员改选或客户取消重下。
        // <b>冻结排在授信判定之前</b>：账期占用必须与签收应收同口径（实重 × 锁定价 − 冻结优惠等比分摊），
        // 而优惠额只有冻结之后才存在。整笔仍是同一个事务，授信不过时券的占用随之回滚。
        boolean clearMallCoupon = confirmation != null && Boolean.TRUE.equals(confirmation.getClearMallCoupon());
        if (clearMallCoupon && confirmation.getCouponInstanceId() != null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        Long couponInstanceId = clearMallCoupon
                ? null
                : confirmation != null && confirmation.getCouponInstanceId() != null
                        ? confirmation.getCouponInstanceId()
                        : salesOrder.getMallCouponInstanceId();
        promotionDiscountService.freeze(
                new PromotionOrderFacts(salesOrder.getId(), salesOrder.getCustomerId(),
                        rows.stream()
                                .map(row -> new PromotionOrderFacts.Line(row.getId(), row.getSkuId(),
                                        row.getOrderedQuantity(), row.getOrderedLineAmount()))
                                .toList()),
                couponInstanceId);
        if (ScmOrderSourceEnum.MALL.name().equals(salesOrder.getOrderSource())
                && salesOrder.getSettlementMethod() != null) {
            settlementConfirmationPolicy.validate(salesOrder);
        } else {
            var check = orderCreditService.checkForSettlementConfirmation(salesOrder.getSettlementCustomerId(),
                    salesOrder.getSettlementTotalAmount());
            if (!Boolean.TRUE.equals(check.getAllowed())) {
                if (confirmation == null || !Boolean.TRUE.equals(confirmation.getCreditOverride())
                        || confirmation.getCreditOverrideReason() == null
                        || confirmation.getCreditOverrideReason().isBlank()) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_CREDIT_BLOCKED);
                }
                StpUtil.checkPermission(OrderPermission.CREDIT_OVERRIDE);
                String reason = confirmation.getCreditOverrideReason().trim();
                salesOrder.setCreditOverrideReason(reason);
                orderLogs.record(salesOrder.getId(), ScmOrderOperationTypeEnum.CREDIT_OVERRIDE, reason, null,
                        Map.of("settlementCustomerId", salesOrder.getSettlementCustomerId(), "orderAmount",
                                salesOrder.getSettlementTotalAmount().toPlainString(), "overLimit",
                                check.getOverLimit(), "overdue", check.getOverdue()));
            }
        }
        salesOrder.setStatus(ScmOrderStatusEnum.CONFIRMED.name());
        salesOrder.setConfirmedAt(OffsetDateTime.now());
        save(salesOrder);
        notificationService.sendOnce("ORDER_CONFIRMED:" + salesOrder.getId(), "ORDER_CONFIRMED",
                salesOrder.getSellerId(), salesOrder.getId(), "销售订单已确认",
                "销售订单 " + salesOrder.getOrderNo() + " 已确认，可进入采购与履约流程。");
        // 订单确认不自动预留库存；当前主链是先接单、再采购和收货，预留由后续显式动作完成。
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.CONFIRM, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO cancel(OrderCancelForm orderCancelForm, String key) {
        return cancel(orderCancelForm, key, true);
    }

    /**
     * 商城客户取消入口：与管理端共用同一条取消路径与同一套状态机，只跳过员工负责人范围（商城客户没有该范围）。
     *
     * <p>
     * 状态机与取消原因校验一律不变：只有 {@code DRAFT} / {@code PENDING} 可取消，已确认订单一律拒绝， 客户不能绕过已确认、已履约、已支付订单的处理规则。归属由商城适配器在入口按登录客户判定。
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO cancelFromMall(OrderCancelForm orderCancelForm, String key) {
        return cancel(orderCancelForm, key, false);
    }

    private SalesOrderDetailVO cancel(OrderCancelForm orderCancelForm, String key, boolean enforceEmployeeScope) {
        // 商城取消的幂等指纹只绑定订单与原因：version 是并发控制字段，第一次执行成功后订单版本就会前进，
        // 把它算进指纹会让「同一请求重试」被判成内容不同，合法重放变成幂等冲突。
        // 管理端保持原样（整表单参与指纹），版本规则不变。
        Object requestFingerprint = enforceEmployeeScope ? orderCancelForm : cancelFingerprint(orderCancelForm);
        var claim = orderIdempotencyService.claim("ORDER_CANCEL:" + orderCancelForm.getOrderId(), key,
                requestFingerprint);
        if (claim.replay()) {
            // 重放也要过归属：管理端用员工范围重新判一次；商城入口已在 MallOrderService 按登录客户校验过归属，
            // 这里若再套员工范围，商城客户的重试会被自己挡掉，幂等就白做了。
            if (enforceEmployeeScope) {
                salesOrderQueryService.detail(orderCancelForm.getOrderId());
            }
            return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        }
        var salesOrder = lock(orderCancelForm.getOrderId());
        requireEmployeeScopeIfNeeded(salesOrder, enforceEmployeeScope);
        version(salesOrder.getVersion(), orderCancelForm.getVersion());
        OrderStateMachine.transition(salesOrder.getStatus(), ScmOrderStatusEnum.CANCELLED.name());
        OrderValidator.reason(orderCancelForm.getReason(), ORDER_CANCEL_REASON_REQUIRED);
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        salesOrder.setStatus(ScmOrderStatusEnum.CANCELLED.name());
        salesOrder.setCancelReason(orderCancelForm.getReason().trim());
        salesOrder.setCancelledAt(OffsetDateTime.now());
        save(salesOrder);
        // 取消时释放该订单的预留（若存在）。当前没有触发点会创建预留，因此通常是空操作；
        // 保留这行是为了让「预留一旦启用」时取消路径自动正确，不需要再改这里。
        inventoryReservationService.releaseBySalesOrder(salesOrder.getId());
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.CANCEL, orderCancelForm.getReason(), before, result);
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
                salesOrder.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(OrderVersionForm orderVersionForm) {
        var salesOrder = salesOrderDao.lock(orderVersionForm.getOrderId());
        if (salesOrder == null)
            return;
        requireOrderSellerScope(salesOrder);
        version(salesOrder.getVersion(), orderVersionForm.getVersion());
        if (!ScmOrderStatusEnum.DRAFT.name().equals(salesOrder.getStatus())) {
            throw new ScmBusinessException(ORDER_DELETE_STATE_INVALID);
        }
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        for (var row : salesOrderItemDao.list(salesOrder.getId()))
            removeItem(row);
        if (salesOrderDao.softDelete(salesOrder.getId(), salesOrder.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        // 草稿若是换货单，关联一并移除：return_id 有唯一索引，残留会挡住退货单重新创建换货
        orderExchangeLinkDao.deleteByReplacementOrderId(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.UPDATE, "删除草稿", before,
                Map.of("deleted", true, "version", salesOrder.getVersion() + 1));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(OrderBatchDeleteForm batchDeleteForm) {
        for (var orderVersionForm : batchDeleteForm.getOrders().stream()
                .sorted(Comparator.comparing(OrderVersionForm::getOrderId)).toList())
            delete(orderVersionForm);
    }

    /** 确认订单不自动占库存，货到后才由业务人员对已确认订单执行预留。只要任一明细可用量不足，整单预留回滚，不允许部分成功。 */
    @Transactional(rollbackFor = Exception.class)
    public void reserveStock(Long orderId) {
        var salesOrder = lock(orderId);
        requireOrderSellerScope(salesOrder);
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(salesOrder.getStatus())) {
            throw new ScmBusinessException(ORDER_RESERVE_STATE_INVALID);
        }
        var rows = salesOrderItemDao.list(salesOrder.getId());
        List<OrderBundleComponentEntity> components = orderBundleComponentDao.listByOrder(salesOrder.getId());
        var bundledItemIds = components.stream().map(OrderBundleComponentEntity::getOrderItemId)
                .collect(Collectors.toSet());
        var reserveLines = new ArrayList<>(rows.stream().filter(row -> !bundledItemIds.contains(row.getId()))
                .map(row -> new InventoryReservationService.OrderReserveLine(row.getId(), row.getSkuId(),
                        row.getActualQuantity(), row.getMallStockUnitSnapshot(), row.getMallStockUnitsPerSaleUnit()))
                .toList());
        reserveLines.addAll(OrderBundleReservationPlanner.lines(components, rows));
        Long warehouseId = salesOrder.getMallDeliveryWarehouseId();
        if (!components.isEmpty() && warehouseId == null) {
            warehouseId = components.getFirst().getWarehouseId();
        }
        Long selectedWarehouseId = warehouseId;
        if (components.stream()
                .anyMatch(component -> !Objects.equals(component.getWarehouseId(), selectedWarehouseId))) {
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        }
        if (reserveLines.isEmpty()) {
            return;
        }
        if (warehouseId != null) {
            inventoryReservationService.reserveForSalesOrder(warehouseId, salesOrder.getId(), reserveLines,
                    OffsetDateTime.now());
        } else {
            inventoryReservationService.reserveForSalesOrder(salesOrder.getId(), reserveLines, OffsetDateTime.now());
        }
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.RESERVE_STOCK, null, null,
                Map.of("reservedLines", reserveLines.size()));
    }

    public SalesOrderEntity lock(Long orderId) {
        var salesOrder = salesOrderDao.lock(orderId);
        if (salesOrder == null)
            throw new ScmBusinessException(ORDER_NOT_FOUND);
        return salesOrder;
    }

    /** 修改订单前必须校验当前员工是否有权操作该订单。功能权限与订单数据范围是两层独立校验，不允许凭订单 ID 越权修改。 */
    private void requireOrderSellerScope(SalesOrderEntity salesOrder) {
        if (!dataScopeService.resolve().getOrderSellerScope().allows(salesOrder.getSellerId()))
            throw new ScmDataScopeException();
    }

    /** 商城建单链按登录客户归属校验，不进入员工负责人范围。 */
    private void requireEmployeeScopeIfNeeded(SalesOrderEntity salesOrder, boolean enforceEmployeeScope) {
        if (enforceEmployeeScope) {
            requireOrderSellerScope(salesOrder);
        }
    }

    public static void version(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected))
            throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void validateOriginal(SalesOrderAddForm salesOrderAddForm) {
        if (salesOrderAddForm.getOriginalOrderId() == null)
            return;
        var original = salesOrderDao.selectById(salesOrderAddForm.getOriginalOrderId());
        if (original == null || !ScmOrderStatusEnum.CONFIRMED.name().equals(original.getStatus())
                || !Objects.equals(original.getCustomerId(), salesOrderAddForm.getCustomerId()))
            throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
    }

    /** 商城与后台分别从各自的可信字段读取本次订单的明确配送仓，不使用系统默认仓。 */
    private static Long selectedWarehouseForCreate(SalesOrderAddForm form) {
        return ScmOrderSourceEnum.MALL.name().equals(form.getOrderSource())
                ? form.getMallDeliveryWarehouseId()
                : form.getDeliveryWarehouseId();
    }

    private List<SalesOrderItemEntity> materialize(SalesOrderAddForm salesOrderAddForm) {
        var ids = salesOrderAddForm.getItems().stream().map(SalesOrderItemForm::getSkuId).toList();
        var products = productSkuOptionDao.selectByIds(ids).stream()
                .collect(Collectors.toMap(skuOption -> skuOption.getSkuId(), Function.identity()));
        var spuIds = products.values().stream().map(skuOption -> skuOption.getSpuId()).distinct().toList();
        var codes = spuIds.isEmpty()
                ? Map.<Long, String>of()
                : productSpuDao.selectBatchIds(spuIds).stream().collect(
                        Collectors.toMap(productSpu -> productSpu.getId(), productSpu -> productSpu.getSpuCode()));
        var resolved = priceResolver
                .resolve(salesOrderAddForm.getCustomerId(), ids, OffsetDateTime.now(),
                        selectedWarehouseForCreate(salesOrderAddForm))
                .stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
        return salesOrderAddForm.getItems().stream().map(orderItemForm -> {
            var productSkuOption = products.get(orderItemForm.getSkuId());
            return OrderSnapshotFactory.item(orderItemForm, productSkuOption,
                    productSkuOption == null ? null : codes.get(productSkuOption.getSpuId()),
                    resolved.get(orderItemForm.getSkuId()));
        }).toList();
    }

    private void header(SalesOrderEntity salesOrder, SalesOrderAddForm salesOrderAddForm) {
        salesOrder.setOrderSource(salesOrderAddForm.getOrderSource());
        salesOrder.setOriginalOrderId(salesOrderAddForm.getOriginalOrderId());
        salesOrder.setSupplementReason(OrderValidator.trim(salesOrderAddForm.getSupplementReason()));
        salesOrder.setRemark(OrderValidator.trim(salesOrderAddForm.getRemark()));
        salesOrder.setExpectDeliveryTime(salesOrderAddForm.getExpectDeliveryTime());
    }

    private BigDecimal total(List<SalesOrderItemEntity> rows) {
        return OrderAmountCalculator
                .orderAmount(rows.stream().map(SalesOrderItemEntity::getOrderedLineAmount).toList());
    }

    private void insert(Long orderId, SalesOrderItemEntity salesOrderItem) {
        salesOrderItem.setOrderId(orderId);
        salesOrderItem.setVersion(0);
        salesOrderItem.setCreatedAt(OffsetDateTime.now());
        salesOrderItem.setCreatedBy(ScmOperator.current());
        salesOrderItem.setUpdatedAt(salesOrderItem.getCreatedAt());
        salesOrderItem.setUpdatedBy(salesOrderItem.getCreatedBy());
        salesOrderItemDao.insert(salesOrderItem);
    }

    private void stamp(SalesOrderEntity salesOrder, boolean creating) {
        salesOrder.setUpdatedAt(OffsetDateTime.now());
        salesOrder.setUpdatedBy(ScmOperator.current());
        if (creating) {
            salesOrder.setCreatedAt(salesOrder.getUpdatedAt());
            salesOrder.setCreatedBy(salesOrder.getUpdatedBy());
        }
    }

    private void save(SalesOrderEntity salesOrder) {
        stamp(salesOrder, false);
        if (salesOrderDao.updateById(salesOrder) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void saveItem(SalesOrderItemEntity row) {
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (salesOrderItemDao.updateById(row) != 1)
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
    }

    private void removeItem(SalesOrderItemEntity row) {
        if (salesOrderItemDao.softDelete(row.getId(), row.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
    }

    private void log(Long orderId, ScmOrderOperationTypeEnum operationType, String reason, Object before,
            Object after) {
        orderLogs.record(orderId, operationType, reason, before, after);
    }
}
