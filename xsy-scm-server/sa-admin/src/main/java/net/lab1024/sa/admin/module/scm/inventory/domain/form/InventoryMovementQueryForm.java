package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.OffsetDateTime;

/**
 * 库存流水列表查询条件（W6 Target Design §10.1）。
 *
 * <p>时间范围过滤的是 {@code occurred_at}（业务发生时刻 = 收货确认时刻），
 * **不是** {@code created_at}（写入时刻）：backfill 回放历史收货时两者相差很远，
 * 用 created_at 过滤会让「按业务时间查流水」这件事在 backfill 数据上完全失效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryMovementQueryForm extends PageParam {

    private Long warehouseId;

    private Long skuId;

    /**
     * SKU 编码模糊匹配（联 {@code product_sku}）。
     *
     * <p><b>为什么必须有它</b>：流水页的筛选不能只给 {@code skuId} ——
     * 用户在页面上看到的是 SKU 编码，手上也只有编码。只提供 id 筛选等于要求用户先知道 id，
     * 那是一个只对开发者成立的筛选条件（W6 Target Design §12.3 #5 的「sku 编码关键字」）。
     */
    @Size(max = 64)
    private String skuCode;

    /** W6-1 只有 {@code PURCHASE_IN}（枚举与 DB CHECK 白名单同源）。 */
    @Pattern(regexp = "PURCHASE_IN")
    private String movementType;

    /** W6-1 只有 {@code PURCHASE_RECEIPT_ITEM}。 */
    @Pattern(regexp = "PURCHASE_RECEIPT_ITEM")
    private String sourceDocumentType;

    /** 来源单据头 id（收货单 id），头级溯源过滤。 */
    private Long sourceDocumentId;

    /** {@code occurred_at >= occurredFrom}。 */
    private OffsetDateTime occurredFrom;

    /** {@code occurred_at < occurredTo}（左闭右开，与 W5 收货时间范围口径一致）。 */
    private OffsetDateTime occurredTo;

    @Override
    @Min(1)
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(1)
    @Max(100)
    public Long getPageSize() {
        return super.getPageSize();
    }
}
