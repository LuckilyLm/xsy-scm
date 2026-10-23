package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.inventory.domain.InventorySalesReturnFact;
import com.xsy.scm.inventory.service.InventoryCommandService;
import com.xsy.scm.inventory.service.InventorySalesReturnQueryService;
import com.xsy.scm.inventory.service.InventorySaleUnitConverter;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;
import com.xsy.scm.order.constant.ScmReturnReceiptDispositionEnum;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.OrderBundleComponentDao;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.inventory.domain.vo.BundleSalesOutAllocationVO;
import com.xsy.scm.order.dao.OrderReturnReceiptDao;
import com.xsy.scm.order.dao.OrderReturnReceiptItemDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.OrderReturnReceiptEntity;
import com.xsy.scm.order.domain.entity.OrderReturnReceiptItemEntity;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.vo.OrderReturnReceiptVO;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.warehouse.service.WarehouseService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_APPROVAL_INVALID;

@Service
@RequiredArgsConstructor
public class OrderReturnReceiptService {
    private final OrderReturnService orderReturnService;
    private final OrderReturnItemDao orderReturnItemDao;
    private final OrderReturnReceiptDao orderReturnReceiptDao;
    private final OrderReturnReceiptItemDao orderReturnReceiptItemDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderBundleComponentDao orderBundleComponentDao;
    private final InventorySalesReturnQueryService inventorySalesReturnQueryService;
    private final InventoryCommandService inventoryCommandService;
    private final ScmIdempotencyService idempotencyService;
    private final OrderOperationLogRecorder orderOperationLogRecorder;
    private final ScmDataScopeService dataScopeService;
    private final WarehouseService warehouseService;

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnReceiptVO receive(OrderReturnReceiveForm form, String key) {
        orderReturnService.detail(form.getReturnId());
        var scope = dataScopeService.resolve();
        if (!scope.getWarehouseScope().allows(form.getWarehouseId())) {
            throw new ScmDataScopeException();
        }
        warehouseService.require(form.getWarehouseId());
        var claim = idempotencyService.claim("ORDER_RETURN_RECEIVE:" + form.getReturnId(), key, form);
        if (claim.replay()) {
            var result = idempotencyService.replay(claim, OrderReturnReceiptVO.class);
            maskCost(result, scope.isCostVisible());
            return result;
        }
        var returned = orderReturnService.lockForReceipt(form.getReturnId());
        if (!ScmOrderReturnStatusEnum.APPROVED.name().equals(returned.getStatus())) {
            throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        }
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();
        OrderReturnReceiptEntity receipt = new OrderReturnReceiptEntity();
        receipt.setReturnId(returned.getId());
        receipt.setWarehouseId(form.getWarehouseId());
        receipt.setIdempotencyKey(key);
        receipt.setReceivedAt(now);
        receipt.setOperator(operator);
        receipt.setCreatedAt(now);
        receipt.setCreatedBy(operator);
        orderReturnReceiptDao.insert(receipt);
        if (!Objects.equals(returned.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        var approved = orderReturnItemDao.list(returned.getId()).stream()
            .collect(Collectors.toMap(e -> e.getId(), e -> e));
        var resultItems = new ArrayList<OrderReturnReceiptVO.Item>();
        var seen = new HashSet<Long>();
        for (var input : form.getItems()) {
            if (!seen.add(input.getReturnItemId())) {
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            }
            var returnItem = approved.get(input.getReturnItemId());
            if (returnItem == null) {
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            }
            orderReturnItemDao.lock(returnItem.getId());
            BigDecimal quantity = ScmDecimalStrings.parseScale4Required(input.getQuantity());
            BigDecimal received = orderReturnReceiptItemDao.receivedQuantity(returnItem.getId());
            if (quantity.signum() <= 0 || received.add(quantity).compareTo(returnItem.getApprovedQuantity()) > 0) {
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            }
            var orderItem = salesOrderItemDao.selectById(returnItem.getOrderItemId());
            if (orderItem == null) {
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            }
            ScmReturnReceiptDispositionEnum disposition;
            try {
                disposition = ScmReturnReceiptDispositionEnum.valueOf(input.getDisposition());
            } catch (RuntimeException exception) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
            List<OrderBundleComponentEntity> bundle = orderBundleComponentDao.listByOrderItem(orderItem.getId());
            if (!bundle.isEmpty()) {
                resultItems.addAll(receiveBundle(bundle, orderItem.getSaleUnitSnapshot(), receipt.getId(),
                    returnItem.getId(), returned.getId(), form.getWarehouseId(), quantity, disposition, now, operator));
                continue;
            }
            BigDecimal remaining = quantity;
            var salesOutMovements = inventorySalesReturnQueryService.salesOutAllocations(returnItem.getOrderItemId());
            for (var salesOut : salesOutMovements) {
                if (salesOut.getUnitCost() == null || salesOut.getUnitSnapshot() == null) {
                    throw new ScmBusinessException(
                        com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_COST_INCOMPLETE);
                }
                // SALES_OUT 流水记的是库存单位数量，退货申请/批准/累计接收记的是销售单位数量；
                // 两种量不能直接相减，否则 1 箱=10 kg 时退回 2 箱会错记成 2 kg。
                String stockUnit = orderItem.getMallStockUnitSnapshot();
                BigDecimal factor = orderItem.getMallStockUnitsPerSaleUnit();
                if (stockUnit != null && !stockUnit.equals(salesOut.getUnitSnapshot())) {
                    throw new ScmBusinessException(
                        com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_UNIT_MISMATCH);
                }
                BigDecimal availableStock = salesOut.getQuantity()
                    .subtract(orderReturnReceiptItemDao.allocatedStockQuantity(salesOut.getId()));
                if (availableStock.signum() <= 0) {
                    continue;
                }
                BigDecimal availableSale = InventorySaleUnitConverter.availableSaleQuantity(availableStock, stockUnit,
                    factor);
                BigDecimal allocated = remaining.min(availableSale);
                if (allocated.signum() <= 0) {
                    continue;
                }
                BigDecimal allocatedStock = InventorySaleUnitConverter.toStockQuantity(allocated, stockUnit, factor);
                if (allocatedStock.compareTo(availableStock) > 0) {
                    throw new ScmBusinessException(
                        com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_QUANTITY_EXCEEDED);
                }
                OrderReturnReceiptItemEntity item = new OrderReturnReceiptItemEntity();
                item.setReceiptId(receipt.getId());
                item.setReturnItemId(returnItem.getId());
                item.setSourceSalesOutMovementId(salesOut.getId());
                item.setSourceOutboundItemId(salesOut.getSourceDocumentItemId());
                item.setDisposition(disposition.name());
                item.setQuantity(allocated);
                item.setUnitSnapshot(orderItem.getSaleUnitSnapshot() == null
                    ? salesOut.getUnitSnapshot()
                    : orderItem.getSaleUnitSnapshot());
                item.setStockQuantity(allocatedStock);
                item.setStockUnitSnapshot(salesOut.getUnitSnapshot());
                item.setUnitCost(salesOut.getUnitCost());
                item.setCreatedAt(now);
                item.setCreatedBy(operator);
                orderReturnReceiptItemDao.insert(item);
                if (disposition == ScmReturnReceiptDispositionEnum.RETURN_TO_STOCK) {
                    inventoryCommandService.postSalesReturnInbound(
                        new InventorySalesReturnFact(form.getWarehouseId(), orderItem.getSkuId(), returned.getId(),
                            item.getId(), allocatedStock, item.getStockUnitSnapshot(), item.getUnitCost(), now,
                            operator, item.getSourceOutboundItemId()));
                }
                OrderReturnReceiptVO.Item itemVO = new OrderReturnReceiptVO.Item();
                itemVO.setReceiptItemId(item.getId());
                itemVO.setReturnItemId(returnItem.getId());
                itemVO.setSourceSalesOutMovementId(item.getSourceSalesOutMovementId());
                itemVO.setSourceOutboundItemId(item.getSourceOutboundItemId());
                itemVO.setDisposition(disposition.name());
                itemVO.setQuantity(allocated);
                itemVO.setUnit(item.getUnitSnapshot());
                itemVO.setStockQuantity(allocatedStock);
                itemVO.setStockUnit(item.getStockUnitSnapshot());
                itemVO.setUnitCost(item.getUnitCost());
                resultItems.add(itemVO);
                remaining = remaining.subtract(allocated);
                if (remaining.signum() == 0) {
                    break;
                }
            }
            if (remaining.signum() > 0) {
                throw new ScmBusinessException(
                    com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_QUANTITY_EXCEEDED);
            }
        }
        OrderReturnReceiptVO result = new OrderReturnReceiptVO();
        result.setReceiptId(receipt.getId());
        result.setReturnId(receipt.getReturnId());
        result.setWarehouseId(receipt.getWarehouseId());
        result.setReceivedAt(now);
        result.setOperator(operator);
        result.setItems(resultItems);
        orderOperationLogRecorder.record(returned.getOrderId(), ScmOrderOperationTypeEnum.RETURN, "退货实物接收", null,
            Map.of("receiptId", receipt.getId(), "warehouseId", receipt.getWarehouseId()));
        idempotencyService.complete(claim, "ORDER_RETURN_RECEIPT", receipt.getId(), result);
        maskCost(result, scope.isCostVisible());
        return result;
    }

    private List<OrderReturnReceiptVO.Item> receiveBundle(List<OrderBundleComponentEntity> components,
        String bundleSaleUnit, Long receiptId, Long returnItemId, Long returnId, Long warehouseId,
        BigDecimal bundleQuantity, ScmReturnReceiptDispositionEnum disposition, OffsetDateTime now, String operator) {
        Long orderItemId = components.getFirst().getOrderItemId();
        Map<Long,
            List<BundleSalesOutAllocationVO>> byComponent = inventorySalesReturnQueryService
                .bundleSalesOutAllocations(orderItemId).stream()
                .collect(Collectors.groupingBy(BundleSalesOutAllocationVO::getBundleComponentId));
        List<OrderReturnReceiptVO.Item> result = new ArrayList<>();
        boolean counted = false;
        for (OrderBundleComponentEntity component : components) {
            BigDecimal wanted;
            try {
                wanted = bundleQuantity.multiply(component.getQuantityPerBundleSaleUnit())
                    .multiply(component.getStockUnitsPerComponentSaleUnit()).setScale(4, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException invalid) {
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            }
            for (BundleSalesOutAllocationVO movement : byComponent.getOrDefault(component.getId(), List.of())) {
                if (!Objects.equals(movement.getSkuId(), component.getComponentSkuId())
                    || !Objects.equals(movement.getUnitSnapshot(), component.getComponentStockUnitSnapshot())
                    || movement.getUnitCost() == null) {
                    throw new ScmBusinessException(
                        com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_COST_INCOMPLETE);
                }
                BigDecimal available = movement.getQuantity()
                    .subtract(orderReturnReceiptItemDao.allocatedStockQuantity(movement.getMovementId()));
                BigDecimal allocated = wanted.min(available);
                if (allocated.signum() <= 0) {
                    continue;
                }
                OrderReturnReceiptItemEntity item = new OrderReturnReceiptItemEntity();
                item.setReceiptId(receiptId);
                item.setReturnItemId(returnItemId);
                item.setSourceSalesOutMovementId(movement.getMovementId());
                item.setSourceOutboundItemId(movement.getOutboundItemId());
                item.setBundleComponentId(component.getId());
                item.setBundleReceiptCounted(!counted);
                item.setDisposition(disposition.name());
                item.setQuantity(bundleQuantity);
                item.setUnitSnapshot(bundleSaleUnit);
                item.setStockQuantity(allocated);
                item.setStockUnitSnapshot(movement.getUnitSnapshot());
                item.setUnitCost(movement.getUnitCost());
                item.setCreatedAt(now);
                item.setCreatedBy(operator);
                orderReturnReceiptItemDao.insert(item);
                if (disposition == ScmReturnReceiptDispositionEnum.RETURN_TO_STOCK) {
                    inventoryCommandService.postSalesReturnInbound(new InventorySalesReturnFact(warehouseId,
                        component.getComponentSkuId(), returnId, item.getId(), allocated, item.getStockUnitSnapshot(),
                        item.getUnitCost(), now, operator, movement.getOutboundItemId()));
                }
                OrderReturnReceiptVO.Item vo = new OrderReturnReceiptVO.Item();
                vo.setReceiptItemId(item.getId());
                vo.setReturnItemId(returnItemId);
                vo.setSourceSalesOutMovementId(movement.getMovementId());
                vo.setSourceOutboundItemId(movement.getOutboundItemId());
                vo.setBundleComponentId(component.getId());
                vo.setBundleReceiptCounted(item.getBundleReceiptCounted());
                vo.setDisposition(disposition.name());
                vo.setQuantity(bundleQuantity);
                vo.setUnit(bundleSaleUnit);
                vo.setStockQuantity(allocated);
                vo.setStockUnit(item.getStockUnitSnapshot());
                vo.setUnitCost(item.getUnitCost());
                result.add(vo);
                counted = true;
                wanted = wanted.subtract(allocated);
                if (wanted.signum() == 0) {
                    break;
                }
            }
            if (wanted.signum() > 0) {
                throw new ScmBusinessException(
                    com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_QUANTITY_EXCEEDED);
            }
        }
        return result;
    }

    private void maskCost(OrderReturnReceiptVO result, boolean costVisible) {
        if (!costVisible) {
            result.getItems().forEach(item -> item.setUnitCost(null));
        }
    }
}
