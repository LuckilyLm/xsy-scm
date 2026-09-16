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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.ResolvedPriceVO;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
/** Aggregate root: all item mutations occur inside an order transaction. */
@Service @RequiredArgsConstructor
public class SalesOrderService {
    private final SalesOrderDao orders;
    private final SalesOrderItemDao items;
    private final OrderAddressSnapshotDao addresses;
    private final OrderOperationLogDao logs;
    private final CustomerService customers;
    private final PriceResolver prices;
    private final ProductSkuOptionDao skus;
    private final ProductSpuDao spus;
    private final OrderNumberGenerator numbers;
    private final OrderIdempotencyService idempotency;
    private final SalesOrderQueryService query;
    private final ObjectMapper json;

    @Transactional(rollbackFor=Exception.class)
    public SalesOrderDetailVO create(SalesOrderAddForm f,String key) {
        var claim=idempotency.claim("ORDER_CREATE",key,f);if(claim.replay()) return idempotency.replay(claim,SalesOrderDetailVO.class);
        OrderValidator.draft(f);var customer=customers.requireTradable(f.getCustomerId());validateOriginal(f);
        if(f.getItems().stream().anyMatch(x->x.getItemId()!=null)) throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
        var rows=materialize(f);var o=new SalesOrderEntity();OrderSnapshotFactory.customer(o,customer);
        o.setOrderNo(numbers.order());o.setStatus("DRAFT");header(o,f);o.setOrderedTotalAmount(total(rows));stamp(o,true);orders.insert(o);
        for(var row:rows) insert(o.getId(),row);
        var a=new OrderAddressSnapshotEntity();BeanUtils.copyProperties(f.getAddress(),a);a.setOrderId(o.getId());a.setCustomerId(o.getCustomerId());a.setCreatedAt(OffsetDateTime.now());a.setCreatedBy(ScmOperator.current());addresses.insert(a);
        var result=query.detail(o.getId());log(o.getId(),"CREATE",null,null,result);idempotency.complete(claim,"SALES_ORDER",o.getId(),result);return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public SalesOrderDetailVO update(SalesOrderUpdateForm f) {
        OrderValidator.draft(f);var o=lock(f.getOrderId());version(o.getVersion(),f.getVersion());OrderStateMachine.editable(o.getStatus());
        // Customer and address identity belong to the creation snapshot; editing cannot replace it.
        if(!Objects.equals(o.getCustomerId(),f.getCustomerId())) throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
        customers.requireTradable(o.getCustomerId());validateOriginal(f);
        var oldAddress=addresses.list(o.getId()).getFirst();
        if(!Objects.equals(oldAddress.getReceiverName(),f.getAddress().getReceiverName())||!Objects.equals(oldAddress.getReceiverPhone(),f.getAddress().getReceiverPhone())||!Objects.equals(oldAddress.getAddress(),f.getAddress().getAddress())) throw new ScmBusinessException(ORDER_STATE_INVALID);
        var before=query.detail(o.getId());var existing=items.list(o.getId());var requested=materialize(f);var changes=SalesOrderItemChangeSet.between(existing,requested);
        // Remove before insert so a removed SKU may be added again without violating the active unique index.
        for(var row:changes.removed()) removeItem(row);
        for(var row:changes.updated()) {row.setOrderId(o.getId());saveItem(row);}
        for(var row:changes.inserted()) insert(o.getId(),row);
        header(o,f);o.setOrderedTotalAmount(total(requested));save(o);
        var result=query.detail(o.getId());log(o.getId(),"UPDATE",null,before,result);return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public SalesOrderDetailVO submit(OrderVersionForm f,String key) {
        var claim=idempotency.claim("ORDER_SUBMIT:"+f.getOrderId(),key,f);if(claim.replay()) return idempotency.replay(claim,SalesOrderDetailVO.class);
        var o=lock(f.getOrderId());version(o.getVersion(),f.getVersion());OrderStateMachine.transition(o.getStatus(),"PENDING");
        var before=query.detail(o.getId());var rows=items.list(o.getId());
        var automatic=rows.stream().filter(x->!x.getManualPriceOverride()).map(SalesOrderItemEntity::getSkuId).toList();
        customers.requireTradable(o.getCustomerId());
        var resolved=prices.requireResolvable(o.getCustomerId(),automatic,OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId,Function.identity()));
        var manual=rows.stream().filter(SalesOrderItemEntity::getManualPriceOverride).map(SalesOrderItemEntity::getSkuId).toList();
        var manualResolved=prices.resolve(o.getCustomerId(),manual,OffsetDateTime.now());
        if(manualResolved.stream().anyMatch(x->!x.isSellable())) throw new ScmBusinessException(net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode.SKU_NOT_SELLABLE);
        for(var row:rows) {
            if(!row.getManualPriceOverride()) OrderSnapshotFactory.applyPrice(row,resolved.get(row.getSkuId()));
            else {OrderValidator.reason(row.getManualPriceReason(),ORDER_PRICE_OVERRIDE_REASON_REQUIRED);if(row.getDraftUnitPrice()==null) throw new ScmBusinessException(ORDER_PRICE_INVALID);}
            row.setLockedUnitPrice(row.getDraftUnitPrice());row.setLockedPriceSource(row.getDraftPriceSource());row.setLockedPriceSourceId(row.getDraftPriceSourceId());
            row.setOrderedLineAmount(OrderAmountCalculator.lineAmount(row.getOrderedQuantity(),row.getLockedUnitPrice()));
            if("STANDARD".equals(row.getProductTypeSnapshot())) {row.setActualQuantity(row.getOrderedQuantity());row.setActualQuantitySource("SYSTEM");}
            saveItem(row);
        }
        o.setOrderedTotalAmount(total(rows));o.setStatus("PENDING");o.setSubmittedAt(OffsetDateTime.now());save(o);
        var result=query.detail(o.getId());log(o.getId(),"SUBMIT",null,before,result);idempotency.complete(claim,"SALES_ORDER",o.getId(),result);return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public SalesOrderDetailVO actualQuantity(OrderActualQuantityForm f,String key) {
        var claim=idempotency.claim("ORDER_ACTUAL:"+f.getOrderId()+":"+f.getItemId(),key,f);if(claim.replay()) return idempotency.replay(claim,SalesOrderDetailVO.class);
        var o=lock(f.getOrderId());OrderStateMachine.actualQuantity(o.getStatus());
        var row=items.list(o.getId()).stream().filter(x->Objects.equals(x.getId(),f.getItemId())).findFirst().orElseThrow(()->new ScmBusinessException(ORDER_ITEM_NOT_OWNED));
        if(!"NON_STANDARD".equals(row.getProductTypeSnapshot())) throw new ScmBusinessException(ORDER_ACTUAL_NOT_ALLOWED);
        if(!Objects.equals(row.getVersion(),f.getVersion())) throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
        OrderValidator.reason(f.getReason(),ORDER_ACTUAL_REASON_REQUIRED);var before=query.detail(o.getId());
        row.setActualQuantity(OrderValidator.decimal(f.getActualQuantity(),true));row.setActualQuantitySource("MANUAL");row.setActualQuantityReason(f.getReason().trim());saveItem(row);
        // Advance the aggregate version too: stale confirm forms must refresh after any item change.
        save(o);var result=query.detail(o.getId());log(o.getId(),"ACTUAL_QUANTITY",f.getReason(),before,result);idempotency.complete(claim,"SALES_ORDER",o.getId(),result);return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public SalesOrderDetailVO confirm(OrderVersionForm f,String key) {
        var claim=idempotency.claim("ORDER_CONFIRM:"+f.getOrderId(),key,f);if(claim.replay()) return idempotency.replay(claim,SalesOrderDetailVO.class);
        var o=lock(f.getOrderId());version(o.getVersion(),f.getVersion());OrderStateMachine.transition(o.getStatus(),"CONFIRMED");
        var before=query.detail(o.getId());var rows=items.list(o.getId());
        for(var row:rows) {
            if(row.getActualQuantity()==null||row.getActualQuantity().signum()<=0) throw new ScmBusinessException(ORDER_ACTUAL_QUANTITY_REQUIRED);
            row.setSettlementLineAmount(OrderAmountCalculator.lineAmount(row.getActualQuantity(),row.getLockedUnitPrice()));saveItem(row);
        }
        o.setSettlementTotalAmount(OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getSettlementLineAmount).toList()));o.setStatus("CONFIRMED");o.setConfirmedAt(OffsetDateTime.now());save(o);
        var result=query.detail(o.getId());log(o.getId(),"CONFIRM",null,before,result);idempotency.complete(claim,"SALES_ORDER",o.getId(),result);return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public SalesOrderDetailVO cancel(OrderCancelForm f,String key) {
        var claim=idempotency.claim("ORDER_CANCEL:"+f.getOrderId(),key,f);if(claim.replay()) return idempotency.replay(claim,SalesOrderDetailVO.class);
        var o=lock(f.getOrderId());version(o.getVersion(),f.getVersion());OrderStateMachine.transition(o.getStatus(),"CANCELLED");OrderValidator.reason(f.getReason(),ORDER_CANCEL_REASON_REQUIRED);
        var before=query.detail(o.getId());o.setStatus("CANCELLED");o.setCancelReason(f.getReason().trim());o.setCancelledAt(OffsetDateTime.now());save(o);
        var result=query.detail(o.getId());log(o.getId(),"CANCEL",f.getReason(),before,result);idempotency.complete(claim,"SALES_ORDER",o.getId(),result);return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public void delete(OrderVersionForm f) {
        var o=orders.lock(f.getOrderId());if(o==null) return;
        version(o.getVersion(),f.getVersion());if(!"DRAFT".equals(o.getStatus())) throw new ScmBusinessException(ORDER_DELETE_STATE_INVALID);
        var before=query.detail(o.getId());for(var row:items.list(o.getId())) removeItem(row);
        if(orders.softDelete(o.getId(),o.getVersion(),ScmOperator.current())!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        log(o.getId(),"UPDATE","删除草稿",before,Map.of("deleted",true,"version",o.getVersion()+1));
    }
    @Transactional(rollbackFor=Exception.class)
    public void batchDelete(OrderBatchDeleteForm f) {for(var row:f.getOrders().stream().sorted(Comparator.comparing(OrderVersionForm::getOrderId)).toList()) delete(row);}
    public SalesOrderEntity lock(Long id) {var o=orders.lock(id);if(o==null) throw new ScmBusinessException(ORDER_NOT_FOUND);return o;}
    public static void version(Integer actual,Integer expected) {if(!Objects.equals(actual,expected)) throw new ScmBusinessException(VERSION_CONFLICT);}
    private void validateOriginal(SalesOrderAddForm f) {
        if(f.getOriginalOrderId()==null) return;var original=orders.selectById(f.getOriginalOrderId());
        if(original==null||!"CONFIRMED".equals(original.getStatus())||!Objects.equals(original.getCustomerId(),f.getCustomerId())) throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
    }
    private List<SalesOrderItemEntity> materialize(SalesOrderAddForm f) {
        var ids=f.getItems().stream().map(SalesOrderItemForm::getSkuId).toList();
        var products=skus.selectByIds(ids).stream().collect(Collectors.toMap(x->x.getSkuId(),Function.identity()));
        var spuIds=products.values().stream().map(x->x.getSpuId()).distinct().toList();
        var codes=spuIds.isEmpty()?Map.<Long,String>of():spus.selectBatchIds(spuIds).stream().collect(Collectors.toMap(x->x.getId(),x->x.getSpuCode()));
        var resolved=prices.resolve(f.getCustomerId(),ids,OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId,Function.identity()));
        return f.getItems().stream().map(x->{var sku=products.get(x.getSkuId());return OrderSnapshotFactory.item(x,sku,sku==null?null:codes.get(sku.getSpuId()),resolved.get(x.getSkuId()));}).toList();
    }
    private void header(SalesOrderEntity o,SalesOrderAddForm f) {o.setOrderSource(f.getOrderSource());o.setOriginalOrderId(f.getOriginalOrderId());o.setSupplementReason(OrderValidator.trim(f.getSupplementReason()));o.setRemark(OrderValidator.trim(f.getRemark()));o.setExpectDeliveryTime(f.getExpectDeliveryTime());}
    private BigDecimal total(List<SalesOrderItemEntity> rows) {return OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getOrderedLineAmount).toList());}
    private void insert(Long id,SalesOrderItemEntity row) {row.setOrderId(id);row.setVersion(0);row.setCreatedAt(OffsetDateTime.now());row.setCreatedBy(ScmOperator.current());row.setUpdatedAt(row.getCreatedAt());row.setUpdatedBy(row.getCreatedBy());items.insert(row);}
    private void stamp(SalesOrderEntity o,boolean creating) {o.setUpdatedAt(OffsetDateTime.now());o.setUpdatedBy(ScmOperator.current());if(creating){o.setCreatedAt(o.getUpdatedAt());o.setCreatedBy(o.getUpdatedBy());}}
    private void save(SalesOrderEntity o) {stamp(o,false);if(orders.updateById(o)!=1) throw new ScmBusinessException(VERSION_CONFLICT);}
    private void saveItem(SalesOrderItemEntity row) {row.setUpdatedAt(OffsetDateTime.now());row.setUpdatedBy(ScmOperator.current());if(items.updateById(row)!=1) throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);}
    private void removeItem(SalesOrderItemEntity row) {if(items.softDelete(row.getId(),row.getVersion(),ScmOperator.current())!=1) throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);}
    private void log(Long id,String operation,String reason,Object before,Object after) {
        var l=new OrderOperationLogEntity();l.setOrderId(id);l.setOperationType(operation);l.setOperator(ScmOperator.current());l.setCreatedBy(l.getOperator());l.setReason(reason);
        l.setBeforeData(before==null?null:json.convertValue(before,new TypeReference<Map<String,Object>>(){}));l.setAfterData(json.convertValue(after,new TypeReference<Map<String,Object>>(){}));logs.insert(l);
    }
}
