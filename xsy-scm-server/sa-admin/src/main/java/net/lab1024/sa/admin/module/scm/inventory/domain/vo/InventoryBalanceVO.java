package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 库存余额行（W6 Target Design §10.1）。
 *
 * <p><b>展示字段是实时联表结果，不是快照</b>：余额是活状态，编码/名称以主数据当前值为准
 * （与 §2.1「余额是活状态不是单据」一致）。流水行上的 {@code unitSnapshot} 才是快照。
 *
 * <p>{@code quantity} 用 4 位定点**字符串**协议（{@code ScmFixedScale4Serializer}），
 * 前端不参与计算。
 */
@Data
public class InventoryBalanceVO {

    private Long id;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    /** SKU 名称（来自 {@code product_sku.spec_name}）。 */
    private String skuName;

    /** 商品名称（来自 {@code product_spu.name}）。 */
    private String productName;

    private Map<String, String> specValues;

    /** Q13 记账单位：一个仓库 + SKU 只可能有一个。 */
    private String unit;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private Integer version;

    private OffsetDateTime updatedAt;
}
