package com.xianshuyuan.scm.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.CustomerEntity;
import com.xianshuyuan.scm.customer.service.CustomerPriceResolver;
import com.xianshuyuan.scm.customer.service.CustomerService;
import com.xianshuyuan.scm.customer.service.ResolvedCustomerPrice;
import com.xianshuyuan.scm.order.dto.*;
import com.xianshuyuan.scm.order.entity.*;
import com.xianshuyuan.scm.order.mapper.*;
import com.xianshuyuan.scm.order.vo.SalesOrderResponse;
import com.xianshuyuan.scm.product.entity.*;
import com.xianshuyuan.scm.product.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOrderApplicationService {
    private final SalesOrderMapper orders;
    private final SalesOrderItemMapper items;
    private final OrderOperationLogMapper logs;
    private final CustomerService customers;
    private final CustomerPriceResolver pricing;
    private final ProductSkuMapper skus;
    private final ProductSpuMapper spus;
    private final SalesOrderNumberGenerator numbers;
    private final SalesOrderValidator validator;
    private final SalesOrderQueryService query;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public SalesOrderApplicationService(SalesOrderMapper o, SalesOrderItemMapper i, OrderOperationLogMapper l, CustomerService c, CustomerPriceResolver p, ProductSkuMapper s, ProductSpuMapper sp, SalesOrderNumberGenerator n, SalesOrderValidator v, SalesOrderQueryService q, IdempotencyService d, ObjectMapper j) {
        orders = o;
        items = i;
        logs = l;
        customers = c;
        pricing = p;
        skus = s;
        spus = sp;
        numbers = n;
        validator = v;
        query = q;
        idempotency = d;
        json = j;
    }

    @Transactional
    public long create(SalesOrderSaveRequest r, String key) {
        var claim = idempotency.claim("ORDER_CREATE", key, r);
        if (claim.replay()) return idempotency.replay(claim, Long.class);
        validator.validateDraft(r);
        var customer = customers.requireEnabled(r.customerId());
        validateOriginal(r, customer);
        var prices = priceMap(r);
        var o = new SalesOrderEntity();
        o.setOrderNo(numbers.next());
        o.setCustomerId(customer.getId());
        o.setCustomerCodeSnapshot(customer.getCustomerCode());
        o.setCustomerNameSnapshot(customer.getName());
        o.setOrderSource(r.source());
        o.setOriginalOrderId(r.originalOrderId());
        o.setSupplementReason(trim(r.supplementReason()));
        o.setStatus(OrderStatus.DRAFT);
        o.setVersion(0);
        o.setDeleted(false);
        o.setCreatedBy("SYSTEM");
        orders.insert(o);
        saveNewItems(o, r, prices);
        recalculateDraft(o);
        log(o.getId(), "CREATE", null, null, auditSnapshot(o, items.selectActiveByOrderId(o.getId())));
        idempotency.complete(claim, "SALES_ORDER", o.getId(), o.getId());
        return o.getId();
    }

    @Transactional
    public void update(long id, SalesOrderSaveRequest r) {
        validator.validateDraft(r);
        var o = lock(id);
        requireState(o, OrderStatus.DRAFT);
        requireVersion(o, r.version());
        var customer = customers.requireEnabled(r.customerId());
        validateOriginal(r, customer);
        var existing = items.selectActiveByOrderIdForUpdate(id);
        var before = auditSnapshot(o, existing);
        var requested = materialize(r, priceMap(r));
        var changes = SalesOrderItemChangeSet.between(id, existing, requested);
        for (var x : changes.updated()) {
            x.setOrderId(id);
            x.setUpdatedBy("SYSTEM");
            if (items.updateById(x) != 1) throw new BusinessException(OrderErrorCodes.ITEM_VERSION_CONFLICT);
        }
        for (var x : changes.inserted()) insertItem(id, x);
        if (!changes.removedIds().isEmpty() && items.softDeleteByIds(id, changes.removedIds()) != changes.removedIds().size())
            throw new BusinessException(OrderErrorCodes.ITEM_VERSION_CONFLICT);
        var rows = items.selectActiveByOrderId(id);
        o.setCustomerId(customer.getId());
        o.setCustomerCodeSnapshot(customer.getCustomerCode());
        o.setCustomerNameSnapshot(customer.getName());
        o.setOrderSource(r.source());
        o.setOriginalOrderId(r.originalOrderId());
        o.setSupplementReason(trim(r.supplementReason()));
        o.setOrderedTotalAmount(OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getOrderedLineAmount).toList()));
        o.setVersion(r.version());
        o.setUpdatedBy("SYSTEM");
        if (orders.updateById(o) != 1) conflict();
        log(id, "UPDATE", null, before, auditSnapshot(o, rows));
    }

    @Transactional
    public SalesOrderResponse submit(long id, int version, String key) {
        var claim = idempotency.claim("ORDER_SUBMIT:" + id, key, new OrderVersionRequest(version));
        if (claim.replay()) return idempotency.replay(claim, SalesOrderResponse.class);
        var o = lock(id);
        requireState(o, OrderStatus.DRAFT);
        requireVersion(o, version);
        var rows = items.selectActiveByOrderIdForUpdate(id);
        var before = auditSnapshot(o, rows);
        var resolved = pricing.resolve(o.getCustomerId(), rows.stream().map(SalesOrderItemEntity::getSkuId).toList(), OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedCustomerPrice::skuId, Function.identity()));
        for (var x : rows) {
            if (Boolean.TRUE.equals(x.getManualPriceOverride())) {
                x.setLockedUnitPrice(x.getDraftUnitPrice());
                x.setLockedPriceSource(PriceSource.OVERRIDE);
                x.setLockedPriceSourceId(null);
            } else {
                var p = resolved.get(x.getSkuId());
                x.setDraftUnitPrice(p.unitPrice());
                x.setDraftPriceSource(PriceSource.valueOf(p.source().name()));
                x.setDraftPriceSourceId(p.sourceRecordId());
                x.setLockedUnitPrice(p.unitPrice());
                x.setLockedPriceSource(PriceSource.valueOf(p.source().name()));
                x.setLockedPriceSourceId(p.sourceRecordId());
            }
            x.setOrderedLineAmount(OrderAmountCalculator.lineAmount(x.getOrderedQuantity(), x.getLockedUnitPrice()));
            if (x.getProductTypeSnapshot() == ProductType.STANDARD) {
                x.setActualQuantity(x.getOrderedQuantity());
                x.setActualQuantitySource(QuantitySource.SYSTEM);
            } else {
                x.setActualQuantity(null);
                x.setActualQuantitySource(null);
            }
            x.setUpdatedBy("SYSTEM");
            if (items.updateById(x) != 1) throw new BusinessException(OrderErrorCodes.ITEM_VERSION_CONFLICT);
        }
        o.setStatus(OrderStatus.PENDING);
        o.setSubmittedAt(OffsetDateTime.now());
        o.setOrderedTotalAmount(OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getOrderedLineAmount).toList()));
        o.setVersion(version);
        o.setUpdatedBy("SYSTEM");
        if (orders.updateById(o) != 1) conflict();
        log(id, "SUBMIT", null, before, auditSnapshot(o, rows));
        var result = query.get(id);
        idempotency.complete(claim, "SALES_ORDER", id, result);
        return result;
    }

    @Transactional
    public void actualQuantity(long id, long itemId, ActualQuantityRequest r, String key) {
        var claim = idempotency.claim("ORDER_ACTUAL:" + id + ":" + itemId, key, r);
        if (claim.replay()) return;
        var o = lock(id);
        requireState(o, OrderStatus.PENDING);
        var row = items.selectActiveByOrderIdForUpdate(id).stream().filter(x -> Objects.equals(x.getId(), itemId)).findFirst().orElseThrow(() -> new BusinessException(OrderErrorCodes.ITEM_NOT_FOUND));
        if (row.getProductTypeSnapshot() != ProductType.NON_STANDARD)
            throw new BusinessException(OrderErrorCodes.ACTUAL_ONLY_NON_STANDARD);
        if (!Objects.equals(row.getVersion(), r.version()))
            throw new BusinessException(OrderErrorCodes.ITEM_VERSION_CONFLICT);
        validator.requireReason(r.reason(), OrderErrorCodes.ACTUAL_REASON_REQUIRED);
        var qty = new BigDecimal(r.actualQuantity());
        if (qty.signum() <= 0) throw new BusinessException(OrderErrorCodes.INVALID_QUANTITY);
        var before = row.getActualQuantity();
        row.setActualQuantity(qty);
        row.setActualQuantitySource(QuantitySource.MANUAL);
        row.setActualQuantityReason(r.reason().trim());
        row.setUpdatedBy("SYSTEM");
        if (items.updateById(row) != 1) throw new BusinessException(OrderErrorCodes.ITEM_VERSION_CONFLICT);
        log(id, "ACTUAL_QUANTITY", r.reason(), json.valueToTree(Map.of("itemId", itemId, "actualQuantity", before == null ? "" : before.toPlainString())), json.valueToTree(Map.of("itemId", itemId, "actualQuantity", qty.toPlainString())));
        idempotency.complete(claim, "SALES_ORDER_ITEM", itemId, itemId);
    }

    @Transactional
    public void confirm(long id, int version, String key) {
        var claim = idempotency.claim("ORDER_CONFIRM:" + id, key, new OrderVersionRequest(version));
        if (claim.replay()) return;
        var o = lock(id);
        requireState(o, OrderStatus.PENDING);
        requireVersion(o, version);
        var rows = items.selectActiveByOrderIdForUpdate(id);
        var before = auditSnapshot(o, rows);
        for (var x : rows) {
            if (x.getActualQuantity() == null || x.getActualQuantity().signum() <= 0)
                throw new BusinessException(OrderErrorCodes.ACTUAL_QUANTITY_REQUIRED);
            x.setSettlementLineAmount(OrderAmountCalculator.lineAmount(x.getActualQuantity(), x.getLockedUnitPrice()));
            x.setUpdatedBy("SYSTEM");
            if (items.updateById(x) != 1) throw new BusinessException(OrderErrorCodes.ITEM_VERSION_CONFLICT);
        }
        o.setSettlementTotalAmount(OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getSettlementLineAmount).toList()));
        o.setStatus(OrderStatus.CONFIRMED);
        o.setConfirmedAt(OffsetDateTime.now());
        o.setVersion(version);
        o.setUpdatedBy("SYSTEM");
        if (orders.updateById(o) != 1) conflict();
        log(id, "CONFIRM", null, before, auditSnapshot(o, rows));
        idempotency.complete(claim, "SALES_ORDER", id, id);
    }

    @Transactional
    public void cancel(long id, CancelOrderRequest r, String key) {
        var claim = idempotency.claim("ORDER_CANCEL:" + id, key, r);
        if (claim.replay()) return;
        validator.requireReason(r.reason(), OrderErrorCodes.CANCEL_REASON_REQUIRED);
        var o = lock(id);
        if (!OrderStateTransitionPolicy.canTransition(o.getStatus(), OrderStatus.CANCELLED))
            throw new BusinessException(OrderErrorCodes.INVALID_STATE);
        requireVersion(o, r.version());
        var rows = items.selectActiveByOrderIdForUpdate(id);
        var before = auditSnapshot(o, rows);
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelReason(r.reason().trim());
        o.setCancelledAt(OffsetDateTime.now());
        o.setVersion(r.version());
        o.setUpdatedBy("SYSTEM");
        if (orders.updateById(o) != 1) conflict();
        log(id, "CANCEL", r.reason(), before, auditSnapshot(o, rows));
        idempotency.complete(claim, "SALES_ORDER", id, id);
    }

    private Map<Long, ResolvedCustomerPrice> priceMap(SalesOrderSaveRequest r) {
        return pricing.resolve(r.customerId(), r.items().stream().map(SalesOrderItemSaveRequest::skuId).toList(), OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedCustomerPrice::skuId, Function.identity()));
    }

    private List<SalesOrderItemEntity> materialize(SalesOrderSaveRequest r, Map<Long, ResolvedCustomerPrice> prices) {
        List<SalesOrderItemEntity> result = new ArrayList<>();
        int sort = 0;
        for (var x : r.items()) {
            var sku = skus.selectById(x.skuId());
            var spu = spus.selectById(sku.getSpuId());
            var p = prices.get(x.skuId());
            var e = new SalesOrderItemEntity();
            e.setId(x.id());
            e.setVersion(x.version());
            e.setSpuId(spu.getId());
            e.setSkuId(sku.getId());
            e.setSpuCodeSnapshot(spu.getSpuCode());
            e.setProductNameSnapshot(spu.getName());
            e.setSkuCodeSnapshot(sku.getSkuCode());
            e.setSpecNameSnapshot(sku.getSpecName());
            e.setSpecValuesSnapshot(sku.getSpecValues());
            e.setSaleUnitSnapshot(sku.getSaleUnit());
            e.setProductTypeSnapshot(sku.getProductType());
            e.setOrderedQuantity(new BigDecimal(x.orderedQuantity()));
            e.setManualPriceOverride(x.manualPriceOverride());
            e.setManualPriceReason(x.manualPriceOverride() ? x.overrideReason().trim() : null);
            e.setDraftUnitPrice(x.manualPriceOverride() ? new BigDecimal(x.unitPrice()) : p.unitPrice());
            e.setDraftPriceSource(x.manualPriceOverride() ? PriceSource.OVERRIDE : PriceSource.valueOf(p.source().name()));
            e.setDraftPriceSourceId(x.manualPriceOverride() ? null : p.sourceRecordId());
            e.setOrderedLineAmount(OrderAmountCalculator.lineAmount(e.getOrderedQuantity(), e.getDraftUnitPrice()));
            e.setSortOrder(sort++);
            result.add(e);
        }
        return result;
    }

    private void saveNewItems(SalesOrderEntity o, SalesOrderSaveRequest r, Map<Long, ResolvedCustomerPrice> p) {
        for (var x : materialize(r, p)) insertItem(o.getId(), x);
    }

    private void insertItem(long id, SalesOrderItemEntity x) {
        x.setOrderId(id);
        x.setVersion(0);
        x.setDeleted(false);
        x.setCreatedBy("SYSTEM");
        items.insert(x);
    }

    private void recalculateDraft(SalesOrderEntity o) {
        var rows = items.selectActiveByOrderId(o.getId());
        o.setOrderedTotalAmount(OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getOrderedLineAmount).toList()));
        o.setUpdatedBy("SYSTEM");
        orders.updateById(o);
    }

    private void validateOriginal(SalesOrderSaveRequest r, CustomerEntity c) {
        if (r.originalOrderId() == null) return;
        var original = orders.selectActiveById(r.originalOrderId());
        if (original == null || original.getStatus() != OrderStatus.CONFIRMED || !Objects.equals(original.getCustomerId(), c.getId()))
            throw new BusinessException(OrderErrorCodes.ORIGINAL_ORDER_INVALID);
    }

    private SalesOrderEntity lock(long id) {
        var o = orders.selectActiveByIdForUpdate(id);
        if (o == null) throw new BusinessException(OrderErrorCodes.ORDER_NOT_FOUND);
        return o;
    }

    private static void requireVersion(SalesOrderEntity o, Integer version) {
        if (version == null || !Objects.equals(o.getVersion(), version)) conflict();
    }

    private static void requireState(SalesOrderEntity o, OrderStatus state) {
        if (o.getStatus() != state) throw new BusinessException(OrderErrorCodes.INVALID_STATE);
    }

    private static void conflict() {
        throw new BusinessException(OrderErrorCodes.VERSION_CONFLICT);
    }

    private void log(long id, String op, String reason, JsonNode before, JsonNode after) {
        var l = new OrderOperationLogEntity();
        l.setOrderId(id);
        l.setOperationType(op);
        l.setOperator("SYSTEM");
        l.setReason(reason);
        l.setBeforeData(before);
        l.setAfterData(after);
        l.setCreatedBy("SYSTEM");
        logs.insert(l);
    }

    private JsonNode auditSnapshot(SalesOrderEntity order, List<SalesOrderItemEntity> rows) {
        ObjectNode node = json.createObjectNode();
        node.put("id", order.getId());
        node.put("customerId", order.getCustomerId());
        node.put("status", order.getStatus().name());
        node.put("source", order.getOrderSource().name());
        if (order.getOriginalOrderId() == null) node.putNull("originalOrderId"); else node.put("originalOrderId", order.getOriginalOrderId());
        if (order.getSupplementReason() == null) node.putNull("supplementReason"); else node.put("supplementReason", order.getSupplementReason());
        node.set("items", json.valueToTree(rows.stream().map(this::itemAuditSnapshot).toList()));
        return node;
    }

    private ObjectNode itemAuditSnapshot(SalesOrderItemEntity item) {
        ObjectNode node = json.createObjectNode();
        node.put("id", item.getId());
        node.put("skuId", item.getSkuId());
        putDecimal(node, "orderedQuantity", item.getOrderedQuantity());
        putDecimal(node, "actualQuantity", item.getActualQuantity());
        putDecimal(node, "draftUnitPrice", item.getDraftUnitPrice());
        putDecimal(node, "lockedUnitPrice", item.getLockedUnitPrice());
        node.put("manualPriceOverride", Boolean.TRUE.equals(item.getManualPriceOverride()));
        if (item.getManualPriceReason() == null) node.putNull("manualPriceReason"); else node.put("manualPriceReason", item.getManualPriceReason());
        return node;
    }

    private static void putDecimal(ObjectNode node, String field, BigDecimal value) {
        if (value == null) node.putNull(field); else node.put(field, value.setScale(4).toPlainString());
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }
}
