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
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;
import com.xsy.scm.order.constant.ScmReturnReceiptDispositionEnum;
import com.xsy.scm.order.dao.OrderReturnItemDao;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
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
            BigDecimal remaining = quantity;
            var salesOutMovements = inventorySalesReturnQueryService.salesOutAllocations(returnItem.getOrderItemId());
            for (var salesOut : salesOutMovements) {
                if (salesOut.getUnitCost() == null || salesOut.getUnitSnapshot() == null) {
                    throw new ScmBusinessException(
                            com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_COST_INCOMPLETE);
                }
                BigDecimal available = salesOut.getQuantity()
                        .subtract(orderReturnReceiptItemDao.allocatedQuantity(salesOut.getId()));
                if (available.signum() <= 0) {
                    continue;
                }
                BigDecimal allocated = remaining.min(available);
                OrderReturnReceiptItemEntity item = new OrderReturnReceiptItemEntity();
                item.setReceiptId(receipt.getId());
                item.setReturnItemId(returnItem.getId());
                item.setSourceSalesOutMovementId(salesOut.getId());
                item.setSourceOutboundItemId(salesOut.getSourceDocumentItemId());
                item.setDisposition(disposition.name());
                item.setQuantity(allocated);
                item.setUnitSnapshot(salesOut.getUnitSnapshot());
                item.setUnitCost(salesOut.getUnitCost());
                item.setCreatedAt(now);
                item.setCreatedBy(operator);
                orderReturnReceiptItemDao.insert(item);
                if (disposition == ScmReturnReceiptDispositionEnum.RETURN_TO_STOCK) {
                    inventoryCommandService.postSalesReturnInbound(new InventorySalesReturnFact(form.getWarehouseId(),
                            orderItem.getSkuId(), returned.getId(), item.getId(), allocated, item.getUnitSnapshot(),
                            item.getUnitCost(), now, operator));
                }
                OrderReturnReceiptVO.Item itemVO = new OrderReturnReceiptVO.Item();
                itemVO.setReceiptItemId(item.getId());
                itemVO.setReturnItemId(returnItem.getId());
                itemVO.setSourceSalesOutMovementId(item.getSourceSalesOutMovementId());
                itemVO.setSourceOutboundItemId(item.getSourceOutboundItemId());
                itemVO.setDisposition(disposition.name());
                itemVO.setQuantity(allocated);
                itemVO.setUnit(item.getUnitSnapshot());
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

    private void maskCost(OrderReturnReceiptVO result, boolean costVisible) {
        if (!costVisible) {
            result.getItems().forEach(item -> item.setUnitCost(null));
        }
    }
}
