package net.lab1024.sa.admin.module.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 按商品收货工作台行（Wave 2B §6.3，跨待收采购单按 {@code skuId + 采购单位} 归并的只读聚合）。
 *
 * <p><b>它只是视图，不是第二套收货事实</b>：数量全部来自 {@code purchase_order_item} 的
 * 计划 / 已收列，逐行裁剪后求和，与底层收货单回写的行量恒等，确认收货仍走各收货单的既有端点。
 *
 * <p><b>Q13 单位</b>：采购单位进入分组键，因此同一 SKU 若在不同采购单用了不同单位会拆成多行，
 * 绝不跨单位相加。{@code pendingQuantity} 是「至少还有行没收齐」的口径（逐行 {@code max(计划-已收,0)} 求和），
 * {@code overReceiptQuantity} 单独给出超收量，二者相加不等于计划或已收，不做互推。
 */
@Data
public class PurchaseReceiptItemWorkbenchVO {

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private String purchaseUnit;

    /**
     * STANDARD / NON_STANDARD，沿用采购行的商品类型快照。
     */
    private String productType;

    /**
     * 命中的待收采购单数（{@code COUNT(DISTINCT purchase_order_id)}）。
     */
    private Long orderCount;

    /**
     * 命中的采购行数。
     */
    private Long lineCount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal pendingQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal overReceiptQuantity;
}
