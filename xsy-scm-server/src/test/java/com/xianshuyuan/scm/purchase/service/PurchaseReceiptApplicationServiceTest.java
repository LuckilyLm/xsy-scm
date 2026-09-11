package com.xianshuyuan.scm.purchase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.inventory.service.InventoryApplicationService;
import com.xianshuyuan.scm.inventory.service.PurchaseInCommand;
import com.xianshuyuan.scm.order.entity.IdempotencyRecordEntity;
import com.xianshuyuan.scm.order.service.IdempotencyService;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptConfirmItemRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptConfirmRequest;
import com.xianshuyuan.scm.purchase.entity.*;
import com.xianshuyuan.scm.purchase.mapper.*;
import com.xianshuyuan.scm.purchase.vo.PurchaseReceiptConfirmResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseReceiptApplicationServiceTest {
    private final PurchaseReceiptMapper receipts = mock(PurchaseReceiptMapper.class);
    private final PurchaseReceiptItemMapper receiptItems = mock(PurchaseReceiptItemMapper.class);
    private final PurchaseReceiptConfirmationMapper confirmations = mock(PurchaseReceiptConfirmationMapper.class);
    private final PurchaseReceiptConfirmationItemMapper confirmationItems = mock(PurchaseReceiptConfirmationItemMapper.class);
    private final ReceiptWeighingRecordMapper weighingRecords = mock(ReceiptWeighingRecordMapper.class);
    private final PurchaseOrderMapper orders = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderItemMapper orderItems = mock(PurchaseOrderItemMapper.class);
    private final PurchaseOperationLogMapper logs = mock(PurchaseOperationLogMapper.class);
    private final PurchaseReceiptNumberGenerator numbers = mock(PurchaseReceiptNumberGenerator.class);
    private final InventoryApplicationService inventory = mock(InventoryApplicationService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final PurchaseReceivingConfigMapper config = mock(PurchaseReceivingConfigMapper.class);
    private final PurchaseReceiptApplicationService service = new PurchaseReceiptApplicationService(receipts, receiptItems,
            confirmations, confirmationItems, weighingRecords, orders, orderItems, logs, numbers, inventory,
            idempotency, new ObjectMapper(), config);

    @BeforeEach
    void setUp() {
        IdempotencyRecordEntity record = new IdempotencyRecordEntity();
        record.setRequestHash("hash");
        when(idempotency.claim(anyString(), anyString(), any())).thenReturn(new IdempotencyService.Claim(record, false));
        when(config.selectEnabledValue(anyString())).thenReturn("10");
        when(confirmations.nextNumber()).thenReturn("RC-1");
        when(confirmations.insert(any(PurchaseReceiptConfirmationEntity.class))).thenAnswer(invocation -> {
            invocation.<PurchaseReceiptConfirmationEntity>getArgument(0).setId(70L);
            return 1;
        });
        when(weighingRecords.insert(any(ReceiptWeighingRecordEntity.class))).thenAnswer(invocation -> {
            invocation.<ReceiptWeighingRecordEntity>getArgument(0).setId(80L);
            return 1;
        });
        when(receipts.updateById(any(PurchaseReceiptEntity.class))).thenReturn(1);
        when(receiptItems.updateById(any(PurchaseReceiptItemEntity.class))).thenReturn(1);
        when(orders.updateById(any(PurchaseOrderEntity.class))).thenReturn(1);
        when(orderItems.updateById(any(PurchaseOrderItemEntity.class))).thenReturn(1);
    }

    @Test
    void confirmPartialReceiptPostsIncrementAndLeavesOrderAndReceiptPartial() {
        PurchaseOrderEntity order = order(PurchaseOrderStatus.SUBMITTED);
        PurchaseOrderItemEntity poItem = orderItem(ProductType.STANDARD, "10.0000", "0.0000");
        PurchaseReceiptEntity receipt = receipt(PurchaseReceiptStatus.DRAFT);
        PurchaseReceiptItemEntity receiptItem = receiptItem(ProductType.STANDARD);
        arrange(receipt, order, poItem, receiptItem);

        PurchaseReceiptConfirmResult result = service.confirm(50L, request("4.0000", null, null), " partial-key ");

        assertThat(result.status()).isEqualTo("PARTIALLY_CONFIRMED");
        assertThat(result.purchaseOrderStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(result.confirmedQuantity()).isEqualTo("4.0000");
        assertThat(poItem.getReceivedQuantity()).isEqualByComparingTo("4.0000");
        assertThat(receiptItem.getReceivedQuantity()).isEqualByComparingTo("4.0000");
        verify(inventory).postPurchaseIn(argThat(command -> command.quantity().compareTo(new BigDecimal("4.0000")) == 0));
        verify(idempotency).complete(any(), eq("PURCHASE_RECEIPT_CONFIRMATION"), eq(70L), eq(result));
    }

    @Test
    void confirmFinalReceiptCompletesOrderAndReceipt() {
        PurchaseOrderEntity order = order(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        PurchaseOrderItemEntity poItem = orderItem(ProductType.STANDARD, "10.0000", "6.0000");
        PurchaseReceiptEntity receipt = receipt(PurchaseReceiptStatus.PARTIALLY_CONFIRMED);
        PurchaseReceiptItemEntity receiptItem = receiptItem(ProductType.STANDARD);
        receiptItem.setReceivedQuantity(new BigDecimal("6.0000"));
        arrange(receipt, order, poItem, receiptItem);

        PurchaseReceiptConfirmResult result = service.confirm(50L, request("4.0000", null, null), "final-key");

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.purchaseOrderStatus()).isEqualTo("RECEIVED");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(receipt.getStatus()).isEqualTo(PurchaseReceiptStatus.CONFIRMED);
        assertThat(poItem.getReceivedQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    void confirmReplayReturnsStoredResultWithoutWrites() {
        IdempotencyService.Claim replay = new IdempotencyService.Claim(new IdempotencyRecordEntity(), true);
        PurchaseReceiptConfirmResult stored = new PurchaseReceiptConfirmResult(50L, 70L, "RC-1",
                "PARTIALLY_CONFIRMED", "PARTIALLY_RECEIVED", "4.0000");
        when(idempotency.claim(eq("PURCHASE_RECEIPT_CONFIRM:50"), eq("same-key"), any())).thenReturn(replay);
        when(idempotency.replay(replay, PurchaseReceiptConfirmResult.class)).thenReturn(stored);

        assertThat(service.confirm(50L, request("4.0000", null, null), "same-key")).isSameAs(stored);

        verifyNoInteractions(receipts, receiptItems, confirmations, confirmationItems, weighingRecords, orders,
                orderItems, logs, inventory);
        verify(idempotency, never()).complete(any(), anyString(), anyLong(), any());
    }

    @Test
    void confirmRejectsMalformedQuantityAsDomainValidationErrorBeforeWrites() {
        arrange(receipt(PurchaseReceiptStatus.DRAFT), order(PurchaseOrderStatus.SUBMITTED),
                orderItem(ProductType.STANDARD, "10.0000", "0.0000"), receiptItem(ProductType.STANDARD));

        assertInvalidQuantity(() -> service.confirm(50L, request("not-a-number", null, null), "bad-key"));
        verifyNoConfirmationWrites();
    }

    @Test
    void confirmRejectsQuantityWithMoreThanFourDecimalPlacesBeforeWrites() {
        arrange(receipt(PurchaseReceiptStatus.DRAFT), order(PurchaseOrderStatus.SUBMITTED),
                orderItem(ProductType.STANDARD, "10.0000", "0.0000"), receiptItem(ProductType.STANDARD));

        assertInvalidQuantity(() -> service.confirm(50L, request("1.00001", null, null), "precision-key"));
        verifyNoConfirmationWrites();
    }

    @Test
    void confirmRejectsOverReceiptBeforeWrites() {
        arrange(receipt(PurchaseReceiptStatus.DRAFT), order(PurchaseOrderStatus.SUBMITTED),
                orderItem(ProductType.STANDARD, "10.0000", "8.0000"), receiptItem(ProductType.STANDARD));

        assertThatThrownBy(() -> service.confirm(50L, request("3.0001", null, null), "over-key"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PurchaseReceiptErrorCodes.OVER_RECEIVED));
        verifyNoConfirmationWrites();
    }

    @Test
    void confirmRejectsWeightFieldsForStandardProductBeforeWrites() {
        arrange(receipt(PurchaseReceiptStatus.DRAFT), order(PurchaseOrderStatus.SUBMITTED),
                orderItem(ProductType.STANDARD, "10.0000", "0.0000"), receiptItem(ProductType.STANDARD));

        assertInvalidQuantity(() -> service.confirm(
                50L, request("2.0000", "1.9000", ReceiptWeighingSource.MANUAL), "weight-key"));
        verifyNoConfirmationWrites();
    }

    @Test
    void confirmPostsInventoryInStableSkuOrder() {
        PurchaseReceiptEntity receipt = receipt(PurchaseReceiptStatus.DRAFT);
        PurchaseOrderEntity order = order(PurchaseOrderStatus.SUBMITTED);
        PurchaseOrderItemEntity skuTwenty = orderItem(ProductType.STANDARD, "3.0000", "0.0000");
        PurchaseOrderItemEntity skuTen = orderItem(ProductType.STANDARD, "4.0000", "0.0000");
        skuTen.setId(62L);
        skuTen.setSkuId(10L);
        PurchaseReceiptItemEntity skuTwentyReceipt = receiptItem(ProductType.STANDARD);
        PurchaseReceiptItemEntity skuTenReceipt = receiptItem(ProductType.STANDARD);
        skuTenReceipt.setId(52L);
        skuTenReceipt.setPurchaseOrderItemId(62L);
        skuTenReceipt.setSkuId(10L);
        skuTenReceipt.setSkuCodeSnapshot("SKU10");
        arrange(receipt, order, List.of(skuTwenty, skuTen), List.of(skuTwentyReceipt, skuTenReceipt));
        PurchaseReceiptConfirmRequest request = new PurchaseReceiptConfirmRequest(2, List.of(
                new PurchaseReceiptConfirmItemRequest(51L, 3, "3.0000", null, null, null),
                new PurchaseReceiptConfirmItemRequest(52L, 3, "4.0000", null, null, null)));

        service.confirm(50L, request, "ordered-key");

        ArgumentCaptor<PurchaseInCommand> commands = ArgumentCaptor.forClass(PurchaseInCommand.class);
        verify(inventory, times(2)).postPurchaseIn(commands.capture());
        assertThat(commands.getAllValues()).extracting(PurchaseInCommand::receiptItemId).containsExactly(52L, 51L);
    }

    @Test
    void deferredReceiptConfirmsWithoutInventoryAndWaitsForPutaway() {
        PurchaseReceiptEntity receipt = receipt(PurchaseReceiptStatus.DRAFT);
        receipt.setReceiptMode(PurchaseReceiptMode.DEFERRED);
        arrange(receipt, order(PurchaseOrderStatus.SUBMITTED),
                orderItem(ProductType.STANDARD, "10.0000", "0.0000"), receiptItem(ProductType.STANDARD));

        service.confirm(50L, request("4.0000", null, null), "deferred-key");

        verifyNoInteractions(inventory);
        assertThat(receipt.getPutawayStatus()).isEqualTo(PurchaseReceiptPutawayStatus.PENDING_PUTAWAY);
    }

    @Test
    void toleranceAllowsTenPercentButRejectsBeyondConfiguredLimit() {
        arrange(receipt(PurchaseReceiptStatus.DRAFT), order(PurchaseOrderStatus.PARTIALLY_RECEIVED),
                orderItem(ProductType.STANDARD, "10.0000", "10.0000"), receiptItem(ProductType.STANDARD));

        service.confirm(50L, request("1.0000", null, null), "within-tolerance");

        assertThatThrownBy(() -> service.confirm(50L, request("1.0001", null, null), "beyond-tolerance"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void confirmNonStandardUsesManualActualWeightAndLinksWeighingRecord() {
        PurchaseOrderItemEntity poItem = orderItem(ProductType.NON_STANDARD, "10.0000", "1.0000");
        PurchaseReceiptItemEntity receiptItem = receiptItem(ProductType.NON_STANDARD);
        arrange(receipt(PurchaseReceiptStatus.PARTIALLY_CONFIRMED), order(PurchaseOrderStatus.PARTIALLY_RECEIVED),
                poItem, receiptItem);

        service.confirm(50L, request("3.0000", "2.7500", ReceiptWeighingSource.MANUAL), "manual-key");

        ArgumentCaptor<ReceiptWeighingRecordEntity> weighing = ArgumentCaptor.forClass(ReceiptWeighingRecordEntity.class);
        verify(weighingRecords).insert(weighing.capture());
        assertThat(weighing.getValue().getConfirmedReading()).isEqualByComparingTo("2.7500");
        assertThat(weighing.getValue().getSource()).isEqualTo(ReceiptWeighingSource.MANUAL);
        ArgumentCaptor<PurchaseReceiptConfirmationItemEntity> detail =
                ArgumentCaptor.forClass(PurchaseReceiptConfirmationItemEntity.class);
        verify(confirmationItems).insert(detail.capture());
        assertThat(detail.getValue().getPlannedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(detail.getValue().getActualWeight()).isEqualByComparingTo("2.7500");
        assertThat(detail.getValue().getEffectiveQuantity()).isEqualByComparingTo("2.7500");
        assertThat(detail.getValue().getWeighingRecordId()).isEqualTo(80L);
        assertThat(poItem.getPlannedQuantity()).isEqualByComparingTo("10.0000");
        assertThat(poItem.getReceivedQuantity()).isEqualByComparingTo("3.7500");
        ArgumentCaptor<PurchaseInCommand> command = ArgumentCaptor.forClass(PurchaseInCommand.class);
        verify(inventory).postPurchaseIn(command.capture());
        assertThat(command.getValue().quantity()).isEqualByComparingTo("2.7500");
    }

    private void arrange(PurchaseReceiptEntity receipt, PurchaseOrderEntity order,
                         PurchaseOrderItemEntity poItem, PurchaseReceiptItemEntity receiptItem) {
        arrange(receipt, order, List.of(poItem), List.of(receiptItem));
    }

    private void arrange(PurchaseReceiptEntity receipt, PurchaseOrderEntity order,
                         List<PurchaseOrderItemEntity> poItems, List<PurchaseReceiptItemEntity> items) {
        when(receipts.selectActiveById(50L)).thenReturn(receipt);
        when(receipts.selectActiveByIdForUpdate(50L)).thenReturn(receipt);
        when(orders.selectActiveByIdForUpdate(60L)).thenReturn(order);
        when(orderItems.selectActiveByOrderIdForUpdate(60L)).thenReturn(poItems);
        when(receiptItems.selectActiveByReceiptIdForUpdate(50L)).thenReturn(items);
    }

    private PurchaseReceiptConfirmRequest request(String received, String weight, ReceiptWeighingSource source) {
        return new PurchaseReceiptConfirmRequest(2,
                List.of(new PurchaseReceiptConfirmItemRequest(51L, 3, received, weight, source,
                        weight == null ? null : "manual correction")));
    }

    private void assertInvalidQuantity(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PurchaseReceiptErrorCodes.INVALID_QUANTITY));
    }

    private void verifyNoConfirmationWrites() {
        verify(confirmations, never()).insert(any(PurchaseReceiptConfirmationEntity.class));
        verify(confirmationItems, never()).insert(any(PurchaseReceiptConfirmationItemEntity.class));
        verify(weighingRecords, never()).insert(any(ReceiptWeighingRecordEntity.class));
        verifyNoInteractions(inventory);
        verify(receipts, never()).updateById(any(PurchaseReceiptEntity.class));
        verify(orders, never()).updateById(any(PurchaseOrderEntity.class));
    }

    private PurchaseReceiptEntity receipt(PurchaseReceiptStatus status) {
        PurchaseReceiptEntity row = new PurchaseReceiptEntity();
        row.setId(50L);
        row.setPurchaseOrderId(60L);
        row.setWarehouseId(10L);
        row.setWarehouseCodeSnapshot("W10");
        row.setWarehouseNameSnapshot("Main");
        row.setStatus(status);
        row.setVersion(2);
        row.setDeleted(false);
        return row;
    }

    private PurchaseOrderEntity order(PurchaseOrderStatus status) {
        PurchaseOrderEntity row = new PurchaseOrderEntity();
        row.setId(60L);
        row.setOrderNo("PO-60");
        row.setStatus(status);
        row.setVersion(5);
        row.setDeleted(false);
        return row;
    }

    private PurchaseOrderItemEntity orderItem(ProductType type, String planned, String received) {
        PurchaseOrderItemEntity row = new PurchaseOrderItemEntity();
        row.setId(61L);
        row.setPurchaseOrderId(60L);
        row.setSkuId(20L);
        row.setProductTypeSnapshot(type);
        row.setPlannedQuantity(new BigDecimal(planned));
        row.setReceivedQuantity(new BigDecimal(received));
        row.setPurchasePrice(new BigDecimal("3.5000"));
        row.setVersion(4);
        row.setDeleted(false);
        return row;
    }

    private PurchaseReceiptItemEntity receiptItem(ProductType type) {
        PurchaseReceiptItemEntity row = new PurchaseReceiptItemEntity();
        row.setId(51L);
        row.setPurchaseReceiptId(50L);
        row.setPurchaseOrderItemId(61L);
        row.setSkuId(20L);
        row.setSkuCodeSnapshot("SKU20");
        row.setSkuNameSnapshot("Tomato");
        row.setPurchaseUnitSnapshot("kg");
        row.setProductTypeSnapshot(type);
        row.setReceivedQuantity(new BigDecimal("0.0000"));
        row.setVersion(3);
        row.setDeleted(false);
        return row;
    }
}
