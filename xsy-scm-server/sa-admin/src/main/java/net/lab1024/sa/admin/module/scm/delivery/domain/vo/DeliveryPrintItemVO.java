package net.lab1024.sa.admin.module.scm.delivery.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 配送打印的明细行。
 *
 * <p>原先这里直接返回 {@code SalesOrderItemEntity}：33 个字段一起出网，包含
 * {@code deleted} / {@code version} / {@code createdBy} 等审计列与
 * {@code manualPriceReason} / {@code draftPriceSourceId} / {@code lockedPriceSourceId}
 * 等价格口径内部字段，而打印页只用得到下面这几列。
 *
 * <p>金额与数量刻意带 {@link ScmFixedScale4Serializer}：实体上的 {@code BigDecimal} 会输出成
 * JSON 数字，而前端 {@code PrintItem} 把这些字段声明为 {@code string}，且四定点一律走字符串
 * 是本仓库既有约定。换成实体直出等于同时违反形状与精度两条口径。
 */
@Data
public class DeliveryPrintItemVO {
    private Long id;
    private Long orderId;
    private String productNameSnapshot;
    private String specNameSnapshot;
    private String saleUnitSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedLineAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementLineAmount;
}
