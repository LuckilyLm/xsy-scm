package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收货单行（<b>对账恒等式</b>）。
 *
 * <p>
 * 五个对账数量（{@code received} / {@code cumulative_received} / {@code remaining} / {@code over_receipt} /
 * {@code receipt_difference}）由 {@code ck_purchase_receipt_item_reconciliation} 在 DB 层强制，因此
 * {@link #updateReconciliation} <b>必须一次写全五个值</b> —— 少写一个就会撞 CHECK。
 *
 * <p>
 * {@link #updateReconciliation} 显式传 {@code plannedQuantity} 是为了让 CHECK 恒等式可被 DB 复核； {@code planned_quantity}
 * 本身<b>永不被覆盖</b>，传的是从采购行继承的同一个值。
 */
@Mapper
public interface PurchaseReceiptItemDao extends BaseMapper<PurchaseReceiptItemEntity> {

    /**
     * 本收货单全部活动行，按 {@code sort_order} 排序。
     */
    List<PurchaseReceiptItemEntity> listByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 锁定本收货单全部行（{@code ORDER BY id ASC FOR UPDATE}）。
     */
    List<PurchaseReceiptItemEntity> lockByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 单行 {@code FOR UPDATE}。
     */
    PurchaseReceiptItemEntity lock(@Param("id") Long id);

    /**
     * 按采购行查活动收货行（跨收货单累计对账用）。
     */
    List<PurchaseReceiptItemEntity> listActiveByOrderItemId(@Param("purchaseOrderItemId") Long purchaseOrderItemId);

    /**
     * 一次性写全 5 个对账数量 + 实重三字段（标品三字段全空）。
     *
     * <p>
     * 实重三字段与 {@code actual_weight} 必须<b>同生同灭</b>（{@code ck_purchase_receipt_item_weight_fields}），因此它们和 5 个对账数量放在同一条
     * UPDATE 里，不做两步写。
     */
    int updateReconciliation(@Param("id") Long id, @Param("version") Integer version,
            @Param("receivedQuantity") BigDecimal receivedQuantity,
            @Param("cumulativeReceivedQuantity") BigDecimal cumulativeReceivedQuantity,
            @Param("remainingQuantity") BigDecimal remainingQuantity,
            @Param("overReceiptQuantity") BigDecimal overReceiptQuantity,
            @Param("receiptDifference") BigDecimal receiptDifference, @Param("actualWeight") BigDecimal actualWeight,
            @Param("weightUnit") String weightUnit, @Param("weighingSource") String weighingSource,
            @Param("correctionReason") String correctionReason, @Param("operator") String operator);

    /**
     * 软删单行。
     */
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    /**
     * 软删本收货单全部行。
     */
    int softDeleteByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId, @Param("operator") String operator);

    /**
     * 活动行数（{@code confirm} 必须提交全部明细的判定用，40998）。
     */
    int countActiveByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId);
}
