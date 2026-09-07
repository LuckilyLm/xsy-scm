package com.xianshuyuan.scm.purchase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.inventory.service.InventoryApplicationService;
import com.xianshuyuan.scm.inventory.service.PurchaseInCommand;
import com.xianshuyuan.scm.order.service.IdempotencyService;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptConfirmItemRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptConfirmRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptCreateRequest;
import com.xianshuyuan.scm.purchase.entity.PurchaseOperationLogEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderItemEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderStatus;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptConfirmationEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptConfirmationItemEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptItemEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptStatus;
import com.xianshuyuan.scm.purchase.entity.ReceiptWeighingRecordEntity;
import com.xianshuyuan.scm.purchase.entity.ReceiptWeighingSource;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOperationLogMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseReceiptConfirmationItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseReceiptConfirmationMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseReceiptItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseReceiptMapper;
import com.xianshuyuan.scm.purchase.mapper.ReceiptWeighingRecordMapper;
import com.xianshuyuan.scm.purchase.vo.PurchaseReceiptConfirmResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
public class PurchaseReceiptApplicationService {
    private static final int QUANTITY_SCALE = 4;
    private static final int QUANTITY_INTEGER_DIGITS = 14;

    private final PurchaseReceiptMapper receipts;
    private final PurchaseReceiptItemMapper receiptItems;
    private final PurchaseReceiptConfirmationMapper confirmations;
    private final PurchaseReceiptConfirmationItemMapper confirmationItems;
    private final ReceiptWeighingRecordMapper weighingRecords;
    private final PurchaseOrderMapper orders;
    private final PurchaseOrderItemMapper orderItems;
    private final PurchaseOperationLogMapper logs;
    private final PurchaseReceiptNumberGenerator numbers;
    private final InventoryApplicationService inventory;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public PurchaseReceiptApplicationService(
            PurchaseReceiptMapper receipts,
            PurchaseReceiptItemMapper receiptItems,
            PurchaseReceiptConfirmationMapper confirmations,
            PurchaseReceiptConfirmationItemMapper confirmationItems,
            ReceiptWeighingRecordMapper weighingRecords,
            PurchaseOrderMapper orders,
            PurchaseOrderItemMapper orderItems,
            PurchaseOperationLogMapper logs,
            PurchaseReceiptNumberGenerator numbers,
            InventoryApplicationService inventory,
            IdempotencyService idempotency,
            ObjectMapper json
    ) {
        this.receipts = receipts;
        this.receiptItems = receiptItems;
        this.confirmations = confirmations;
        this.confirmationItems = confirmationItems;
        this.weighingRecords = weighingRecords;
        this.orders = orders;
        this.orderItems = orderItems;
        this.logs = logs;
        this.numbers = numbers;
        this.inventory = inventory;
        this.idempotency = idempotency;
        this.json = json;
    }

    @Transactional
    public long create(PurchaseReceiptCreateRequest request) {
        var order = orders.selectActiveByIdForUpdate(request.purchaseOrderId());
        if (order == null) {
            throw new BusinessException(PurchaseOrderErrorCodes.NOT_FOUND);
        }
        if (order.getStatus() != PurchaseOrderStatus.SUBMITTED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException(PurchaseReceiptErrorCodes.INVALID_STATE);
        }
        var existing = receipts.selectActiveByOrderIdForUpdate(order.getId());
        if (existing != null) {
            return existing.getId();
        }

        var receipt = new PurchaseReceiptEntity();
        receipt.setReceiptNo(numbers.next());
        receipt.setPurchaseOrderId(order.getId());
        receipt.setPurchaseOrderNoSnapshot(order.getOrderNo());
        receipt.setWarehouseId(order.getWarehouseId());
        receipt.setWarehouseCodeSnapshot(order.getWarehouseCodeSnapshot());
        receipt.setWarehouseNameSnapshot(order.getWarehouseNameSnapshot());
        receipt.setStatus(PurchaseReceiptStatus.DRAFT);
        receipt.setRemark(request.remark());
        receipt.setVersion(0);
        receipt.setDeleted(false);
        receipt.setCreatedBy("SYSTEM");
        receipts.insert(receipt);

        int sort = 0;
        for (var source : orderItems.selectActiveByOrderIdForUpdate(order.getId())) {
            var row = new PurchaseReceiptItemEntity();
            row.setPurchaseReceiptId(receipt.getId());
            row.setPurchaseOrderItemId(source.getId());
            row.setSkuId(source.getSkuId());
            row.setSpuCodeSnapshot(source.getSpuCodeSnapshot());
            row.setProductNameSnapshot(source.getProductNameSnapshot());
            row.setSkuCodeSnapshot(source.getSkuCodeSnapshot());
            row.setSkuNameSnapshot(source.getSkuNameSnapshot());
            row.setSpecValuesSnapshot(source.getSpecValuesSnapshot());
            row.setPurchaseUnitSnapshot(source.getPurchaseUnitSnapshot());
            row.setProductTypeSnapshot(source.getProductTypeSnapshot());
            row.setReceivedQuantity(BigDecimal.ZERO.setScale(QUANTITY_SCALE));
            row.setSortOrder(sort++);
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedBy("SYSTEM");
            receiptItems.insert(row);
        }
        log(order.getId(), receipt.getId(), "RECEIPT_CREATE", receipt.getStatus().name());
        return receipt.getId();
    }

    @Transactional
    public PurchaseReceiptConfirmResult confirm(
            long receiptId,
            PurchaseReceiptConfirmRequest request,
            String key
    ) {
        String scope = "PURCHASE_RECEIPT_CONFIRM:" + receiptId;
        var claim = idempotency.claim(scope, key, request);
        if (claim.replay()) {
            return idempotency.replay(claim, PurchaseReceiptConfirmResult.class);
        }

        var receiptSnapshot = receipts.selectActiveById(receiptId);
        if (receiptSnapshot == null) {
            throw new BusinessException(PurchaseReceiptErrorCodes.NOT_FOUND);
        }
        var order = orders.selectActiveByIdForUpdate(receiptSnapshot.getPurchaseOrderId());
        var receipt = receipts.selectActiveByIdForUpdate(receiptId);
        if (receipt == null || !Objects.equals(receipt.getPurchaseOrderId(), receiptSnapshot.getPurchaseOrderId())) {
            throw new BusinessException(PurchaseReceiptErrorCodes.NOT_FOUND);
        }
        if (receipt.getStatus() == PurchaseReceiptStatus.CONFIRMED) {
            throw new BusinessException(PurchaseReceiptErrorCodes.INVALID_STATE);
        }
        if (!Objects.equals(receipt.getVersion(), request.version())) {
            throw new BusinessException(PurchaseReceiptErrorCodes.VERSION_CONFLICT);
        }

        if (order == null || (order.getStatus() != PurchaseOrderStatus.SUBMITTED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED)) {
            throw new BusinessException(PurchaseReceiptErrorCodes.INVALID_STATE);
        }

        var purchaseRows = orderItems.selectActiveByOrderIdForUpdate(order.getId());
        var purchaseById = new HashMap<Long, PurchaseOrderItemEntity>();
        purchaseRows.forEach(row -> purchaseById.put(row.getId(), row));

        var receiptRows = receiptItems.selectActiveByReceiptIdForUpdate(receiptId);
        var receiptById = new HashMap<Long, PurchaseReceiptItemEntity>();
        receiptRows.forEach(row -> receiptById.put(row.getId(), row));

        var lines = validateLines(request, receiptById, purchaseById);
        lines.sort(Comparator
                .comparing((ConfirmationLine line) -> line.receiptItem().getSkuId())
                .thenComparing(line -> line.receiptItem().getId()));
        BigDecimal total = lines.stream()
                .map(ConfirmationLine::effectiveQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var confirmation = new PurchaseReceiptConfirmationEntity();
        confirmation.setConfirmationNo(confirmations.nextNumber());
        confirmation.setPurchaseReceiptId(receiptId);
        confirmation.setIdempotencyScope(scope);
        confirmation.setIdempotencyKey(key.trim());
        confirmation.setRequestHash(claim.record().getRequestHash());
        confirmation.setTotalQuantity(total);
        confirmation.setStatus("CONFIRMED");
        confirmation.setOperator("SYSTEM");
        confirmation.setConfirmedAt(OffsetDateTime.now());
        confirmation.setCreatedBy("SYSTEM");
        confirmations.insert(confirmation);

        for (var line : lines) {
            confirmLine(receipt, confirmation, line);
        }

        boolean complete = purchaseRows.stream()
                .allMatch(row -> row.getReceivedQuantity().compareTo(row.getPlannedQuantity()) == 0);
        order.setStatus(complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (orders.updateById(order) != 1) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }

        receipt.setStatus(complete ? PurchaseReceiptStatus.CONFIRMED : PurchaseReceiptStatus.PARTIALLY_CONFIRMED);
        receipt.setConfirmedAt(OffsetDateTime.now());
        receipt.setReceivedAt(receipt.getConfirmedAt());
        receipt.setOperator("SYSTEM");
        receipt.setVersion(request.version());
        if (receipts.updateById(receipt) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCodes.VERSION_CONFLICT);
        }

        var result = new PurchaseReceiptConfirmResult(
                receiptId,
                confirmation.getId(),
                confirmation.getConfirmationNo(),
                receipt.getStatus().name(),
                order.getStatus().name(),
                decimal(total));
        confirmation.setResultData(json.valueToTree(result));
        confirmations.updateById(confirmation);
        log(order.getId(), receiptId, "RECEIPT_CONFIRM", confirmation.getConfirmationNo());
        idempotency.complete(claim, "PURCHASE_RECEIPT_CONFIRMATION", confirmation.getId(), result);
        return result;
    }

    private List<ConfirmationLine> validateLines(
            PurchaseReceiptConfirmRequest request,
            HashMap<Long, PurchaseReceiptItemEntity> receiptById,
            HashMap<Long, PurchaseOrderItemEntity> purchaseById
    ) {
        var seen = new HashSet<Long>();
        var lines = new ArrayList<ConfirmationLine>();
        for (var requested : request.items()) {
            if (!seen.add(requested.receiptItemId())) {
                throw new BusinessException(PurchaseReceiptErrorCodes.INVALID_QUANTITY);
            }
            var receiptItem = receiptById.get(requested.receiptItemId());
            if (receiptItem == null) {
                throw new BusinessException(PurchaseReceiptErrorCodes.ITEM_NOT_FOUND);
            }
            if (!Objects.equals(receiptItem.getVersion(), requested.version())) {
                throw new BusinessException(PurchaseReceiptErrorCodes.VERSION_CONFLICT);
            }
            var purchaseItem = purchaseById.get(receiptItem.getPurchaseOrderItemId());
            if (purchaseItem == null) {
                throw new BusinessException(PurchaseReceiptErrorCodes.ITEM_NOT_FOUND);
            }

            BigDecimal declaredQuantity = parsePositiveQuantity(requested.receivedQuantity());
            BigDecimal actualWeight = null;
            BigDecimal effectiveQuantity = declaredQuantity;
            if (receiptItem.getProductTypeSnapshot() == ProductType.NON_STANDARD) {
                actualWeight = parsePositiveQuantity(requested.actualWeight());
                if (requested.weightSource() != ReceiptWeighingSource.MANUAL) {
                    throw new BusinessException(PurchaseReceiptErrorCodes.INVALID_QUANTITY);
                }
                effectiveQuantity = actualWeight;
            } else if (requested.actualWeight() != null
                    || requested.weightSource() != null
                    || hasText(requested.correctionReason())) {
                throw new BusinessException(PurchaseReceiptErrorCodes.INVALID_QUANTITY);
            }

            BigDecimal remaining = purchaseItem.getPlannedQuantity().subtract(purchaseItem.getReceivedQuantity());
            if (effectiveQuantity.compareTo(remaining) > 0) {
                throw new BusinessException(PurchaseReceiptErrorCodes.OVER_RECEIVED);
            }
            lines.add(new ConfirmationLine(
                    receiptItem,
                    purchaseItem,
                    requested,
                    declaredQuantity,
                    effectiveQuantity,
                    actualWeight));
        }
        return lines;
    }

    private void confirmLine(
            PurchaseReceiptEntity receipt,
            PurchaseReceiptConfirmationEntity confirmation,
            ConfirmationLine line
    ) {
        var receiptItem = line.receiptItem();
        var purchaseItem = line.purchaseItem();
        var requested = line.request();

        var detail = new PurchaseReceiptConfirmationItemEntity();
        detail.setConfirmationId(confirmation.getId());
        detail.setPurchaseReceiptItemId(receiptItem.getId());
        detail.setPurchaseOrderItemId(purchaseItem.getId());
        detail.setPlannedQuantity(line.declaredQuantity());
        detail.setEffectiveQuantity(line.effectiveQuantity());
        detail.setActualWeight(line.actualWeight());
        detail.setUnit(receiptItem.getPurchaseUnitSnapshot());
        detail.setWeighingSource(requested.weightSource());
        detail.setCorrectionReason(requested.correctionReason());
        if (line.actualWeight() != null) {
            var weighingRecord = new ReceiptWeighingRecordEntity();
            weighingRecord.setPurchaseReceiptItemId(receiptItem.getId());
            weighingRecord.setRawReading(line.actualWeight());
            weighingRecord.setConfirmedReading(line.actualWeight());
            weighingRecord.setUnit(receiptItem.getPurchaseUnitSnapshot());
            weighingRecord.setSource(ReceiptWeighingSource.MANUAL);
            weighingRecord.setModificationReason(requested.correctionReason());
            weighingRecord.setRecordedAt(OffsetDateTime.now());
            weighingRecord.setOperator("SYSTEM");
            weighingRecord.setVersion(0);
            weighingRecord.setDeleted(false);
            weighingRecord.setCreatedBy("SYSTEM");
            weighingRecords.insert(weighingRecord);
            detail.setWeighingRecordId(weighingRecord.getId());
        }
        detail.setCreatedBy("SYSTEM");
        confirmationItems.insert(detail);

        inventory.postPurchaseIn(new PurchaseInCommand(
                receipt.getId(),
                receiptItem.getId(),
                confirmation.getId(),
                receipt.getWarehouseId(),
                receiptItem.getSkuId(),
                receipt.getWarehouseCodeSnapshot(),
                receipt.getWarehouseNameSnapshot(),
                receiptItem.getSkuCodeSnapshot(),
                receiptItem.getSkuNameSnapshot(),
                receiptItem.getPurchaseUnitSnapshot(),
                line.effectiveQuantity(),
                purchaseItem.getPurchasePrice()));

        purchaseItem.setReceivedQuantity(purchaseItem.getReceivedQuantity().add(line.effectiveQuantity()));
        if (orderItems.updateById(purchaseItem) != 1) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }

        receiptItem.setReceivedQuantity(receiptItem.getReceivedQuantity().add(line.effectiveQuantity()));
        receiptItem.setActualWeight(line.actualWeight());
        receiptItem.setWeightUnit(line.actualWeight() == null ? null : receiptItem.getPurchaseUnitSnapshot());
        receiptItem.setWeighingSource(detail.getWeighingSource());
        receiptItem.setCorrectionReason(detail.getCorrectionReason());
        receiptItem.setVersion(requested.version());
        if (receiptItems.updateById(receiptItem) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCodes.VERSION_CONFLICT);
        }
    }

    private BigDecimal parsePositiveQuantity(String value) {
        try {
            BigDecimal quantity = new BigDecimal(value);
            if (quantity.signum() <= 0
                    || quantity.scale() > QUANTITY_SCALE
                    || quantity.precision() - quantity.scale() > QUANTITY_INTEGER_DIGITS) {
                throw new NumberFormatException();
            }
            return quantity;
        } catch (NullPointerException | NumberFormatException error) {
            throw new BusinessException(PurchaseReceiptErrorCodes.INVALID_QUANTITY);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String decimal(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private void log(long orderId, long receiptId, String type, String state) {
        var row = new PurchaseOperationLogEntity();
        row.setPurchaseOrderId(orderId);
        row.setPurchaseReceiptId(receiptId);
        row.setOperationType(type);
        row.setOperator("SYSTEM");
        row.setAfterData(JsonNodeFactory.instance.objectNode().put("state", state));
        row.setCreatedBy("SYSTEM");
        logs.insert(row);
    }

    public PurchaseReceiptEntity require(long id) {
        var row = receipts.selectActiveById(id);
        if (row == null) {
            throw new BusinessException(PurchaseReceiptErrorCodes.NOT_FOUND);
        }
        return row;
    }

    public List<PurchaseReceiptEntity> list() {
        return receipts.selectActiveList();
    }

    public List<PurchaseReceiptItemEntity> items(long id) {
        require(id);
        return receiptItems.selectActiveByReceiptId(id);
    }

    public List<PurchaseReceiptConfirmationEntity> confirmations(long id) {
        require(id);
        return confirmations.selectByReceiptId(id);
    }

    public List<PurchaseReceiptConfirmationItemEntity> confirmationItems(long id) {
        require(id);
        return confirmationItems.selectByReceiptId(id);
    }

    private record ConfirmationLine(
            PurchaseReceiptItemEntity receiptItem,
            PurchaseOrderItemEntity purchaseItem,
            PurchaseReceiptConfirmItemRequest request,
            BigDecimal declaredQuantity,
            BigDecimal effectiveQuantity,
            BigDecimal actualWeight
    ) {
    }
}
